/*!A dlopen library that bypasses mobile system limitation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (C) 2020-present, TBOOX Open Source Group.
 *
 * @author      ruki
 * @file        byopen_android.c
 *
 */

/* //////////////////////////////////////////////////////////////////////////////////////
 * includes
 */
#include "byopen.h"
#include "../utils/my_phone_info_util.h"


#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <inttypes.h>
#include <elf.h>
#include <link.h>
#include <pthread.h>
#include <sys/system_properties.h>

/* //////////////////////////////////////////////////////////////////////////////////////
 * macros
 */

// the fake dlopen magic
#define BY_FAKE_DLCTX_MAGIC      (0xfaddfadd)

/* g_dl_mutex in linker
 *
 * @see http://androidxref.com/5.0.0_r2/xref/bionic/linker/dlfcn.cpp#32
 */
#define BY_LINKER_MUTEX         "__dl__ZL10g_dl_mutex"

// the linker name
#ifndef __LP64__
#   define BY_LINKER_NAME       "linker"
#else
#   define BY_LINKER_NAME       "linker64"
#endif

/* //////////////////////////////////////////////////////////////////////////////////////
 * types
 */

// the dynamic library context type for fake dlopen
typedef struct _by_fake_dlctx_t {
    // magic, mark handle for fake dlopen
    by_uint32_t magic;

    // the load bias address of the dynamic library
    by_pointer_t biasaddr;

    // the .dynsym and .dynstr sections
    by_pointer_t dynstr;
    by_pointer_t dynsym;
    by_int_t dynsym_num;

    // the .symtab and .strtab sections
    by_pointer_t strtab;
    by_pointer_t symtab;
    by_int_t symtab_num;

    // the file data and size
    by_pointer_t filedata;
    by_size_t filesize;

} by_fake_dlctx_t, *by_fake_dlctx_ref_t;

/* //////////////////////////////////////////////////////////////////////////////////////
 * globals
 */

// the jni environment on tls
__thread JNIEnv *g_tls_jnienv = by_null;
static JavaVM *g_jvm = by_null;
static by_int_t g_jversion = JNI_VERSION_1_4;
static pthread_mutex_t *g_linker_mutex = by_null;

/* //////////////////////////////////////////////////////////////////////////////////////
 * declaration
 */
extern __attribute((weak)) by_int_t
dl_iterate_phdr(by_int_t (*)(struct dl_phdr_info *, size_t, by_pointer_t), by_pointer_t);

// find the load bias address from the base address
static void *by_fake_find_biasaddr_from_baseaddr(void *baseaddr) {
    // check
    by_assert_and_check_return_val(baseaddr, by_null);

    // find load bias from program header
    ElfW(Ehdr) *ehdr = (ElfW(Ehdr) *) baseaddr;
    ElfW(Phdr) const *dlpi_phdr = (ElfW(Phdr) const *) ((size_t) baseaddr + ehdr->e_phoff);
    by_int_t dlpi_phnum = ehdr->e_phnum;
    uintptr_t min_vaddr = UINTPTR_MAX;
    for (by_int_t i = 0; i < dlpi_phnum; i++) {
        ElfW(Phdr) const *phdr = &(dlpi_phdr[i]);
        if (PT_LOAD == phdr->p_type) {
            if (min_vaddr > phdr->p_vaddr)
                min_vaddr = phdr->p_vaddr;
        }
    }
    return reinterpret_cast<void *>(min_vaddr != UINTPTR_MAX ? ((size_t) baseaddr - min_vaddr)
                                                             : by_null);
}

// find the load bias address and real path from the maps
static by_pointer_t by_fake_find_biasaddr_from_maps(by_char_t const *filename, by_char_t *realpath,
                                                    by_size_t realmaxn) {
    // check
    by_assert_and_check_return_val(filename && realpath && realmaxn, by_null);

    // trace
    by_trace("find biasaddr of %s from maps", filename);

    // find it
    char line[512];
    char page_attr[10];
    void *biasaddr = by_null;
    FILE *fp = fopen("/proc/self/maps", "r");
    if (fp) {
        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, filename)) {
                int pos = 0;
                uintptr_t start = 0;
                uintptr_t offset = 0;
                // 7372a68000-7372bc1000 --xp 000fe000 fd:06 39690571                       /system/lib64/libandroid_runtime.so
                // FIXME: SCNxPTR 前加空格有影响吗？
                if (3 ==
                    sscanf(line, "%" SCNxPTR"-%*" SCNxPTR" %4s %" SCNxPTR" %*x:%*x %*d%n", &start,
                           page_attr, &offset, &pos)) {
                    // check permission and offset
                    if (page_attr[0] != 'r') continue;
                    if (page_attr[3] != 'p') continue;
                    if (0 != offset) continue;

                    // get load bias address
                    biasaddr = by_fake_find_biasaddr_from_baseaddr((by_pointer_t) start);

                    // get real path
                    if (filename[0] == '/')
                        strlcpy(realpath, filename, realmaxn);
                    else if (pos < sizeof(line)) {
                        by_char_t *p = line + pos;
                        by_char_t *e = p + strlen(p);
                        while (p < e && isspace((by_int_t) *p)) p++;
                        while (p < e && isspace((by_int_t) (*(e - 1)))) e--;
                        *e = '\0';
                        if (p < e) strlcpy(realpath, p, realmaxn);
                        else realpath[0] = '\0';
                    } else realpath[0] = '\0';

                    // trace
                    by_trace("realpath: %s, biasaddr: %p found!", realpath, biasaddr);
                }
                break;
            }
        }
        fclose(fp);
    }
    return biasaddr;
}

// the callback of dl_iterate_phdr()
static by_int_t
by_fake_find_biasaddr_from_linker_cb(struct dl_phdr_info *info, size_t size, by_pointer_t udata) {
    // check
    by_cpointer_t *args = (by_cpointer_t *) udata;
    by_check_return_val(args, 1);
    by_check_return_val(info && info->dlpi_addr && info->dlpi_name && info->dlpi_name[0] != '\0',
                        0);

    // get filename
    by_char_t const *filename = by_null;
    by_char_t const *filepath = (by_char_t const *) args[0];
    by_assert_and_check_return_val(filepath, 1);
    if (filepath[0] == '/') {
        by_char_t const *p = filepath + strlen(filepath);
        while (p >= filepath && *p != '/')
            p--;
        if (p >= filepath && *p == '/') filename = p + 1;
    }

    // find library, we can also get full path of dlpi_name from maps
    by_pointer_t *pbiasaddr = (by_pointer_t *) &args[3];
    by_char_t *realpath = (by_char_t *) args[1];
    by_size_t realmaxn = (by_size_t) args[2];
    if ((filepath && strstr(info->dlpi_name, filepath)) ||
        (filename &&
         !strcmp(info->dlpi_name, filename))) // dlpi_name ma ybe not full path, e.g. libart.so
    {
        // save load bias address
        *pbiasaddr = (by_pointer_t) info->dlpi_addr;

        // get real path
        if (filepath[0] == '/')
            strlcpy(realpath, filepath, realmaxn);
        else if (info->dlpi_name[0] == '/')
            strlcpy(realpath, info->dlpi_name, realmaxn);
        else {
            // we only find real path
            if (!by_fake_find_biasaddr_from_maps(filepath, realpath, realmaxn))
                realpath[0] = '\0';
        }

        // trace
        by_trace("realpath: %s, biasaddr: %p found!", realpath, (by_pointer_t) info->dlpi_addr);

        // found, stop it
        return 1;
    }
    return 0;
}

// find the load bias address and real path from the maps
static by_pointer_t
by_fake_find_biasaddr_from_linker(by_char_t const *filepath, by_char_t *realpath,
                                  by_size_t realmaxn) {
    // check
    by_assert_and_check_return_val(dl_iterate_phdr && filepath && realpath && realmaxn, by_null);

    // trace
    by_trace("find biasaddr of %s from linker", filepath);

    // find biasaddr
    by_cpointer_t args[4];
    args[0] = (by_cpointer_t) filepath;
    args[1] = (by_cpointer_t) realpath;
    args[2] = (by_cpointer_t) realmaxn;
    args[3] = by_null;
    if (g_linker_mutex) pthread_mutex_lock(g_linker_mutex);
    dl_iterate_phdr(by_fake_find_biasaddr_from_linker_cb, args);
    if (g_linker_mutex) pthread_mutex_unlock(g_linker_mutex);
    return (by_pointer_t) args[3];
}

// find the load bias address and real path
static by_pointer_t
by_fake_find_biasaddr(by_char_t const *filename, by_char_t *realpath, by_size_t realmaxn) {
    by_assert_and_check_return_val(filename && realpath, by_null);
    by_pointer_t biasaddr = by_null;
    if (dl_iterate_phdr && 0 != strcmp(filename, BY_LINKER_NAME))
        biasaddr = by_fake_find_biasaddr_from_linker(filename, realpath, realmaxn);
    if (!biasaddr)
        biasaddr = by_fake_find_biasaddr_from_maps(filename, realpath, realmaxn);
    return biasaddr;
}

// open map file
static by_pointer_t by_fake_open_file(by_char_t const *filepath, by_size_t *pfilesize) {
    // check
    by_assert_and_check_return_val(filepath && pfilesize, by_null);

    // open it
    by_int_t fd = -1;
    by_pointer_t filedata = by_null;
    do {
        // open file
        fd = open(filepath, O_RDONLY | O_CLOEXEC);
        if (fd < 0 && errno == EINTR)
            fd = open(filepath, O_RDONLY | O_CLOEXEC);
        by_check_break(fd > 0);

        // get file size
        struct stat st;
        if (0 != fstat(fd, &st) || 0 == st.st_size) break;

        // mmap the file data
        filedata = mmap(by_null, st.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
        by_assert_and_check_break(filedata && filedata != MAP_FAILED);

        // save the file size
        if (pfilesize) *pfilesize = (by_size_t) st.st_size;

    } while (0);

    // close the fd first
    if (fd > 0) close(fd);
    fd = -1;

    // ok?
    return filedata;
}

// get symbol address from the fake dlopen context
static by_pointer_t by_fake_dlsym(by_fake_dlctx_ref_t dlctx, by_char_t const *symbol) {
    // check
    by_assert_and_check_return_val(dlctx && dlctx->filedata && dlctx->filesize && symbol, by_null);

    // find the symbol address from the .dynsym first
    by_int_t i = 0;
    size_t end = (size_t) dlctx->filedata + dlctx->filesize;
    by_char_t const *dynstr = (by_char_t const *) dlctx->dynstr;
    ElfW(Sym) *dynsym = (ElfW(Sym) *) dlctx->dynsym;
    by_int_t dynsym_num = dlctx->dynsym_num;
    if (dynsym && dynstr) {
        for (i = 0; i < dynsym_num; i++, dynsym++) {
            by_char_t const *name = dynstr + dynsym->st_name;
            if ((size_t) name < end && strcmp(name, symbol) == 0) {
                /* NB: sym->st_value is an offset into the section for relocatables,
                 * but a VMA for shared libs or exe files, so we have to subtract the bias
                 */
                by_pointer_t symboladdr = (by_pointer_t) ((size_t) dlctx->biasaddr +
                                                          dynsym->st_value);
                by_trace("dlsym(%s): found at .dynsym/%p = %p + %x", symbol, symboladdr,
                         dlctx->biasaddr, (by_int_t) dynsym->st_value);
                return symboladdr;
            }
        }
    }

    // find the symbol address from the .symtab
    by_char_t const *strtab = (by_char_t const *) dlctx->strtab;
    ElfW(Sym) *symtab = (ElfW(Sym) *) dlctx->symtab;
    by_int_t symtab_num = dlctx->symtab_num;
    if (symtab && strtab) {
        for (i = 0; i < symtab_num; i++, symtab++) {
            by_char_t const *name = strtab + symtab->st_name;
            if ((size_t) name < end && strcmp(name, symbol) == 0) {
                by_pointer_t symboladdr = (by_pointer_t) ((size_t) dlctx->biasaddr +
                                                          symtab->st_value);
                by_trace("dlsym(%s): found at .symtab/%p = %p + %x", symbol, symboladdr,
                         dlctx->biasaddr, (by_int_t) symtab->st_value);
                return symboladdr;
            }
        }
    }
    return by_null;
}

// close the fake dlopen context
static by_int_t by_fake_dlclose(by_fake_dlctx_ref_t dlctx) {
    // check
    by_assert_and_check_return_val(dlctx, -1);

    // clear data
    dlctx->biasaddr = by_null;
    dlctx->dynsym = by_null;
    dlctx->dynstr = by_null;
    dlctx->dynsym_num = 0;
    dlctx->strtab = by_null;
    dlctx->symtab = by_null;
    dlctx->symtab_num = 0;

    // unmap file data
    if (dlctx->filedata) munmap(dlctx->filedata, dlctx->filesize);
    dlctx->filedata = by_null;
    dlctx->filesize = 0;

    // free context
    free(dlctx);
    return 0;
}

/* @see https://www.sunmoonblog.com/2019/06/04/fake-dlopen/
 * https://github.com/avs333/Nougat_dlfunctions
 */
static by_fake_dlctx_ref_t by_fake_dlopen_impl(by_char_t const *filename, by_int_t flag) {
    // check
    by_assert_and_check_return_val(filename, by_null);

    // do open
    by_bool_t ok = by_false;
    by_char_t realpath[512];
    by_fake_dlctx_ref_t dlctx = by_null;
    do {
        // attempt to find the load bias address and real path
        by_pointer_t biasaddr = by_fake_find_biasaddr(filename, realpath, sizeof(realpath));
        by_check_break(biasaddr);

        // init context
        dlctx = static_cast<by_fake_dlctx_ref_t>(calloc(1, sizeof(by_fake_dlctx_t)));
        by_assert_and_check_break(dlctx);

        dlctx->magic = BY_FAKE_DLCTX_MAGIC;
        dlctx->biasaddr = biasaddr;

        // open file
        dlctx->filedata = by_fake_open_file(realpath, &dlctx->filesize);
        by_assert_and_check_break(dlctx->filedata && dlctx->filesize);

        // trace
        by_trace("fake_dlopen: biasaddr: %p, realpath: %s, filesize: %d", biasaddr, realpath,
                 (by_int_t) dlctx->filesize);

        // get elf
        ElfW(Ehdr) *elf = (ElfW(Ehdr) *) dlctx->filedata;
        size_t end = (size_t) dlctx->filedata + dlctx->filesize;
        by_assert_and_check_break((size_t) (elf + 1) < end);

        // get .shstrtab section
        size_t shoff = (size_t) dlctx->filedata + elf->e_shoff;
        ElfW(Shdr) *shstrtab = (ElfW(Shdr) *) (shoff + elf->e_shstrndx * elf->e_shentsize);
        by_assert_and_check_break((size_t) (shstrtab + 1) <= end);

        size_t shstr = (size_t) dlctx->filedata + shstrtab->sh_offset;
        by_assert_and_check_break(shstr < end);

        // parse elf sections
        by_int_t i = 0;
        by_bool_t broken = by_false;
        for (i = 0; !broken && i < elf->e_shnum && shoff; i++, shoff += elf->e_shentsize) {
            // get section
            ElfW(Shdr) *sh = (ElfW(Shdr) *) shoff;
            by_assert_and_check_break((size_t) (sh + 1) <= end && shstr + sh->sh_name < end);
            by_assert_and_check_break((size_t) dlctx->filedata + sh->sh_offset < end);

            // trace
            by_trace("elf section(%d): type: %d, name: %s", i, sh->sh_type, shstr + sh->sh_name);

            // get .dynsym and .symtab sections
            switch (sh->sh_type) {
                case SHT_DYNSYM:
                    // get .dynsym
                    if (dlctx->dynsym) {
                        by_trace("%s: duplicate .dynsym sections", realpath);
                        broken = by_true;
                        break;
                    }
                    dlctx->dynsym = (by_pointer_t) ((size_t) dlctx->filedata + sh->sh_offset);
                    dlctx->dynsym_num = (sh->sh_size / sizeof(ElfW(Sym)));
                    by_trace(".dynsym: %p %d", dlctx->dynsym, dlctx->dynsym_num);
                    break;
                case SHT_SYMTAB:
                    // get .symtab
                    if (dlctx->symtab) {
                        by_trace("%s: duplicate .symtab sections", realpath);
                        broken = by_true;
                        break;
                    }
                    dlctx->symtab = (by_pointer_t) ((size_t) dlctx->filedata + sh->sh_offset);
                    dlctx->symtab_num = (sh->sh_size / sizeof(ElfW(Sym)));
                    by_trace(".symtab: %p %d", dlctx->symtab, dlctx->symtab_num);
                    break;
                case SHT_STRTAB:
                    // get .dynstr
                    if (!strcmp(reinterpret_cast<const char *>(shstr + sh->sh_name), ".dynstr")) {
                        // .dynstr is guaranteed to be the first STRTAB
                        if (dlctx->dynstr) break;
                        dlctx->dynstr = (by_pointer_t) ((size_t) dlctx->filedata + sh->sh_offset);
                        by_trace(".dynstr: %p", dlctx->dynstr);
                    }
                        // get .strtab
                    else if (!strcmp(reinterpret_cast<const char *>(shstr + sh->sh_name),
                                     ".strtab")) {
                        if (dlctx->strtab) break;
                        dlctx->strtab = (by_pointer_t) ((size_t) dlctx->filedata + sh->sh_offset);
                        by_trace(".strtab: %p", dlctx->strtab);
                    }
                    break;
                default:
                    break;
            }
        }
        by_check_break(!broken && dlctx->dynstr && dlctx->dynsym);

        // ok
        ok = by_true;

    } while (0);

    // failed?
    if (!ok) {
        if (dlctx) by_fake_dlclose(dlctx);
        dlctx = by_null;
    }
    return dlctx;
}

static by_void_t by_linker_init() {
    static by_bool_t s_inited = by_false;
    if (!s_inited) {
        // we need linker mutex only for android 5.0 and 5.1
        int api_level = by_rt_api_level();
        if (api_level == __ANDROID_API_L__ || api_level == __ANDROID_API_L_MR1__) {
            by_fake_dlctx_ref_t linker = by_fake_dlopen_impl(BY_LINKER_NAME, BY_RTLD_NOW);
            by_trace("init linker: %p", linker);
            if (linker) {
                g_linker_mutex = (pthread_mutex_t *) by_fake_dlsym(linker, BY_LINKER_MUTEX);
                by_trace("load g_dl_mutex: %p", g_linker_mutex);
                by_fake_dlclose(linker);
            }
        }
        s_inited = by_true;
    }
}

static by_fake_dlctx_ref_t by_fake_dlopen(by_char_t const *filename, by_int_t flag) {
    by_linker_init();
    return by_fake_dlopen_impl(filename, flag);
}

static by_void_t by_jni_clearException(JNIEnv *env, by_bool_t report) {
    jthrowable e = report ? env->ExceptionOccurred() : by_null;
    env->ExceptionClear();
    if (e) {
        jclass clazz = env->GetObjectClass(e);
        jmethodID printStackTrace_id = env->GetMethodID(clazz, "printStackTrace", "()V");
        if (!env->ExceptionCheck() && printStackTrace_id)
            env->CallVoidMethod(e, printStackTrace_id);
        if (env->ExceptionCheck())
            env->ExceptionClear();
    }
}

static jobject by_jni_Class_getDeclaredMethod(JNIEnv *env) {
    // check
    by_assert_and_check_return_val(env, by_null);

    // push
    if (env->PushLocalFrame(10) < 0) return by_null;

    // get unreachable memory info
    jboolean check = by_false;
    jobject getDeclaredMethod_method = by_null;
    do {
        // get class
        jclass clazz = env->FindClass("java/lang/Class");
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && clazz);

        // get string class
        jclass string_clazz = env->FindClass("java/lang/String");
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && string_clazz);

        // get class/array class
        jclass classarray_clazz = env->FindClass("[Ljava/lang/Class;");
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && classarray_clazz);

        // get getDeclaredMethod id
        jmethodID getDeclaredMethod_id = env->GetMethodID(clazz, "getDeclaredMethod",
                                                          "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;");
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && getDeclaredMethod_id);

        // get getDeclaredMethod name
        jstring getDeclaredMethod_name = env->NewStringUTF("getDeclaredMethod");
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && getDeclaredMethod_name);

        // get getDeclaredMethod args
        jobjectArray getDeclaredMethod_args = env->NewObjectArray(2, clazz, by_null);
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && getDeclaredMethod_args);

        env->SetObjectArrayElement(getDeclaredMethod_args, 0, string_clazz);
        env->SetObjectArrayElement(getDeclaredMethod_args, 1, classarray_clazz);

        // Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
        getDeclaredMethod_method = (jobject) env->CallObjectMethod(clazz,
                                                                   getDeclaredMethod_id,
                                                                   getDeclaredMethod_name,
                                                                   getDeclaredMethod_args);
        by_assert_and_check_break(
                !(check = env->ExceptionCheck()) && getDeclaredMethod_method);

    } while (0);

    // exception? clear it
    if (check) {
        getDeclaredMethod_method = by_null;
        by_jni_clearException(env, by_true);
    }
    return (jstring) env->PopLocalFrame(getDeclaredMethod_method);
}

/* load library via system call
 *
 * @see http://weishu.me/2018/06/07/free-reflection-above-android-p/
 * https://github.com/tiann/FreeReflection/blob/c995ef100f39c2eb2d7c344384ca06e8c13b9a4c/library/src/main/java/me/weishu/reflection/Reflection.java#L23-L34
 *
 * System.load(libraryPath)
 *
 * @code
    Method forName = Class.class.getDeclaredMethod("forName", String.class);
    Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
    Class<?> systemClass = (Class<?>)forName.invoke(null, "java.lang.System");
    Method load = (Method)getDeclaredMethod.invoke(systemClass, "load", new Class[]{String.class});
    load.invoke(systemClass, libraryPath);
 * @endcode
 *
 * System.loadLibrary(libraryName)
 *
 * @code
    Method forName = Class.class.getDeclaredMethod("forName", String.class);
    Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
    Class<?> systemClass = (Class<?>)forName.invoke(null, "java.lang.System");
    Method loadLibrary = (Method)getDeclaredMethod.invoke(systemClass, "loadLibrary", new Class[]{String.class});
    loadLibrary.invoke(systemClass, libraryName);
 * @endcode
 */
static by_bool_t by_jni_System_load_or_loadLibrary_from_sys(JNIEnv *env, by_char_t const *loadName,
                                                            by_char_t const *libraryPath) {
    // check
    by_assert_and_check_return_val(env && loadName && libraryPath, by_false);

    // push
    if (env->PushLocalFrame(20) < 0) return by_false;

    // do load
    jboolean check = by_false;
    do {
        // get getDeclaredMethod method
        jobject getDeclaredMethod_method = by_jni_Class_getDeclaredMethod(env);
        by_assert_and_check_break(
                !(check = env->ExceptionCheck()) && getDeclaredMethod_method);

        // get class
        jclass clazz = env->FindClass("java/lang/Class");
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && clazz);

        // get object class
        jclass object_clazz = env->FindClass("java/lang/Object");
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && object_clazz);

        // get string class
        jclass string_clazz = env->FindClass("java/lang/String");
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && string_clazz);

        // get system class
        jclass system_clazz = env->FindClass("java/lang/System");
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && system_clazz);

        // get method class
        jclass method_clazz = env->FindClass("java/lang/reflect/Method");
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && method_clazz);

        // get getDeclaredMethod_method.invoke id
        jmethodID invoke_id = env->GetMethodID(method_clazz, "invoke",
                                               "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && invoke_id);

        // get load name
        jstring load_name = env->NewStringUTF(loadName);
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && load_name);

        // get invoke args
        jobjectArray invoke_args = env->NewObjectArray(2, object_clazz, by_null);
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && invoke_args);

        // get load args
        jobjectArray load_args = env->NewObjectArray(1, clazz, string_clazz);
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && load_args);

        env->SetObjectArrayElement(invoke_args, 0, load_name);
        env->SetObjectArrayElement(invoke_args, 1, load_args);

        // Method load = (Method)getDeclaredMethod.invoke(systemClass, "load", new Class[]{String.class});
        auto load_method = (jobject) env->CallObjectMethod(getDeclaredMethod_method,
                                                           invoke_id, system_clazz,
                                                           invoke_args);
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && load_method);

        // load.invoke(systemClass, libraryPath)
        jstring libraryPath_jstr = env->NewStringUTF(libraryPath);
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && libraryPath_jstr);

        invoke_args = env->NewObjectArray(1, object_clazz, libraryPath_jstr);
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && invoke_args);

        env->CallObjectMethod((jobject) load_method, invoke_id, system_clazz, invoke_args);
        by_assert_and_check_break(!(check = env->ExceptionCheck()));

    } while (0);

    // exception? clear it
    if (check) by_jni_clearException(env, by_true);
    env->PopLocalFrame(by_null);
    return !check;
}

static by_bool_t by_jni_System_load_or_loadLibrary_from_app(JNIEnv *env, by_char_t const *loadName,
                                                            by_char_t const *libraryPath) {
    // check
    by_assert_and_check_return_val(env && loadName && libraryPath, by_false);

    // push
    if (env->PushLocalFrame(10) < 0) return by_false;

    // do load
    jboolean check = by_false;
    do {
        // get system class
        jclass system_clazz = env->FindClass("java/lang/System");
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && system_clazz);

        // get load/loadLibrary id
        jmethodID load_id = env->GetStaticMethodID(system_clazz, loadName,
                                                   "(Ljava/lang/String;)V");
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && load_id);

        // get library path
        jstring libraryPath_jstr = env->NewStringUTF(libraryPath);
        by_assert_and_check_break(!(check = env->ExceptionCheck()) && libraryPath_jstr);

        // load library
        env->CallStaticVoidMethod(system_clazz, load_id, libraryPath_jstr);
        by_assert_and_check_break(!(check = env->ExceptionCheck()));

    } while (0);

    // exception? clear it
    if (check) by_jni_clearException(env, by_true);
    env->PopLocalFrame(by_null);
    return !check;
}

// System.load(libraryPath)
static by_bool_t by_jni_System_load(JNIEnv *env, const by_char_t *libraryPath) {
    by_trace("load: %s", libraryPath);
    return by_jni_System_load_or_loadLibrary_from_app(env, "load", libraryPath) ||
           by_jni_System_load_or_loadLibrary_from_sys(env, "load", libraryPath);
}

// System.loadLibrary(libraryName)
static by_bool_t by_jni_System_loadLibrary(JNIEnv *env, by_char_t const *libraryName) {
    by_trace("loadLibrary: %s", libraryName);
    return by_jni_System_load_or_loadLibrary_from_app(env, "loadLibrary", libraryName) ||
           by_jni_System_load_or_loadLibrary_from_sys(env, "loadLibrary", libraryName);
}

/* get the current jni environment
 *
 * @see frameworks/base/core/jni/include/android_runtime/AndroidRuntime.h
 *
 * static AndroidRuntime* runtime = AndroidRuntime::getRuntime();
 * static JavaVM* getJavaVM() { return mJavaVM; }
 * static JNIEnv* getJNIEnv();
 */
static JNIEnv *by_jni_getenv() {
    if (g_jvm) {
        JNIEnv *env = by_null;
        if (JNI_OK == (*g_jvm).GetEnv((void **) &env, g_jversion))
            return env;
    }
    if (!g_tls_jnienv) {
        by_fake_dlctx_ref_t dlctx = by_fake_dlopen("libandroid_runtime.so", BY_RTLD_NOW);
        if (dlctx) {
            typedef by_pointer_t (*getJNIEnv_t)();
            getJNIEnv_t getJNIEnv = (getJNIEnv_t) by_fake_dlsym(dlctx,
                                                                "_ZN7android14AndroidRuntime9getJNIEnvEv");
            if (getJNIEnv)
                g_tls_jnienv = static_cast<JNIEnv *>(getJNIEnv());
            by_fake_dlclose(dlctx);
        }

        // trace
        by_trace("get jnienv: %p", g_tls_jnienv);
    }
    return g_tls_jnienv;
}

by_void_t by_jni_javavm_set(JavaVM *jvm, by_int_t jversion) {
    g_jvm = jvm;
    g_jversion = jversion;
}

/* //////////////////////////////////////////////////////////////////////////////////////
 * implementation
 */
by_pointer_t by_dlopen(by_char_t const *filename, by_int_t flag) {
    // check
    by_assert_and_check_return_val(filename, by_null);

    // attempt to use original dlopen to load it fist
    // FIXME: we disable the original dlopen now, load /data/xxx.so may be returned an invalid address
    by_pointer_t handle = by_null;// dlopen(filename, flag == BY_RTLD_LAZY? RTLD_LAZY : RTLD_NOW);
    // uses the fake dlopen to load it from maps directly
    if (!handle) handle = (by_pointer_t) by_fake_dlopen(filename, flag);

    // uses the fake dlopen to load it from maps directly
    if (!handle) {
        // load it via system call
        JNIEnv *env = by_jni_getenv();
        if (env && (((strstr(filename, "/") || strstr(filename, ".so")) &&
                     by_jni_System_load(env, filename)) ||
                    by_jni_System_loadLibrary(env, filename)))
            handle = (by_pointer_t) by_fake_dlopen(filename, flag);
    }
    return handle;
}

by_pointer_t by_dlsym(by_pointer_t handle, by_char_t const *symbol) {
    // check
    by_fake_dlctx_ref_t dlctx = (by_fake_dlctx_ref_t) handle;
    by_assert_and_check_return_val(dlctx && symbol, by_null);

    // do dlsym
    return (dlctx->magic == BY_FAKE_DLCTX_MAGIC) ? by_fake_dlsym(dlctx, symbol) : dlsym(handle,
                                                                                        symbol);
}

by_int_t by_dlclose(by_pointer_t handle) {
    // check
    by_fake_dlctx_ref_t dlctx = (by_fake_dlctx_ref_t) handle;
    by_assert_and_check_return_val(dlctx, -1);

    // do dlclose
    return (dlctx->magic == BY_FAKE_DLCTX_MAGIC) ? by_fake_dlclose(dlctx) : dlclose(handle);
}