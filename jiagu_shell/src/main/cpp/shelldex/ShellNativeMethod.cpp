#include <jni.h>
#include <string>
#include "ShellNativeMethod.h"
#include "datadefine.h"
#include <stdlib.h>
#include <dlfcn.h>
#include <stdio.h>
#include <android/log.h>
#include "../byopen/byopen.h"
#include "dalvik_system_DexFile.h"
#include <dlfcn.h>
#include <iostream>
#include <sys/mman.h>
#include <elf.h>
#include <asm/fcntl.h>
#include <fcntl.h>
#include <vector>
#include <vector>
#include <unistd.h>

#define  LOG_TAG    "ShellNativeMethod"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

//#ifdef __arm__
#define Elf_Ehdr Elf32_Ehdr
#define Elf_Shdr Elf32_Shdr
#define Elf_Sym  Elf32_Sym
//#else defined(__aarch64__)
//#define Elf_Ehdr Elf64_Ehdr
//#define Elf_Shdr Elf64_Shdr
//#define Elf_Sym  Elf64_Sym
//#endif

struct ctx {
    void *load_addr;
    void *dynstr;
    void *dynsym;
    int nsyms;
    off_t bias;
};


void *fake_dlopen(const char *libpath, int flags);

void *fake_dlsym(void *handle, const char *name);

bool useOpenMemory = true;

typedef void *(*org_artDexFileOpenMemory22)(const std::string &location,
                                            uint32_t location_checksum,
                                            void *mem_map,
                                            std::string *error_msg);

//typedef void *(*org_artDexFileOpenMemory22)(const uint8_t *base,
//                                            size_t size,
//                                            const std::string &location,
//                                            uint32_t location_checksum,
//                                            void *mem_map,
//                                            const void *oat_dex_file,
//                                            std::string *error_msg);

// 执行这个方法后返回的是 mem_map
typedef void *(*org_artMemMapMapDummy)(const char *name, uint8_t *addr, size_t byte_count);
// MemMap* mem_map,
// const OatDexFile* oat_dex_file,

jlong replace_cookie(JNIEnv *env, void *c_dex_cookie, int sdk_int);

int checkFunction(JNINativeMethod *table, const char *name, const char *sig,
                  void(**fnPtrOut)(u4 const *, union JValue *));

JNINativeMethod *dexFile;

org_artDexFileOpenMemory22 openMemory22;
org_artMemMapMapDummy mapDummy;

void (*openDexFile)(const u4 *args, union JValue *pResult);

int checkFunction(JNINativeMethod *table, const char *name, const char *sig,
                  void(**fnPtrOut)(u4 const *, union JValue *)) {
    int i = 0;
    while (table[i].name != nullptr) {
        LOGI("dvm native method:%s", table[i].name);
        if ((strcmp(name, table[i].name) == 0) && (strcmp(sig, table[i].signature) == 0)) {
            LOGI("find method:%s", table[i].name);
            *fnPtrOut = (void (*)(const u4 *, union JValue *)) (table[i].fnPtr);
            return 1;
        }
        i++;
    }
    LOGI("can not find method:%s", table[i].name);
    return 0;
}

int printCPlusPlusFunction(JNINativeMethod *table) {
    int i = 0;
    while (table[i].name != nullptr) {
        LOGI("Function name: %s", table[i].name);
        i++;
    }
    return 0;
}

void test() {
    auto *ao = (ArrayObject *) malloc(16 + 20);
    ao->length = 20;
    u4 t1;
    u4 t2 = (size_t) ao;
    size_t t3;
    auto t4 = (size_t) ao;
    LOGI("sizeof ao:%d, t1:%d, t2:%d, t3:%d, t4:%d", sizeof ao, sizeof t1, sizeof t2, sizeof t3,
         sizeof t4);
    LOGI("sizeof ao:%lu, t1:%u, t2:%u, t3:%zu,t4: %zu", ao, t1, t2, t3, t4);

    typedef const char *(*GetVersion)();
    typedef const char *(*GetEmail)(const char *);
    void *libsxjiagu = (void *) by_dlopen("libsxjiagu.so", RTLD_LAZY);
    auto getVersion = (GetVersion) by_dlsym(libsxjiagu, "getVersion");
    auto getEmail = (GetEmail) by_dlsym(libsxjiagu, "_Z8getEmailPKc");
    LOGI("libsxjiagu: %p, getVersion:%p, getEmail: %p", libsxjiagu, getVersion, getEmail);
    LOGI("GetVersion:%s", getVersion());
    const char *email_prefix = "Email";
    LOGI("email_prefix ptr: %p", email_prefix);
    LOGI("GetEmail:%s", getEmail(email_prefix));
}

const char *openMemory22Name = "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_10OatDexFileEPS9_";
const char *openMemory23Name = "_ZN3art7DexFile10OpenMemoryERKNSt3__112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEEjPNS_6MemMapEPS7_";

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    test();
    void *libart = (void *) by_dlopen("libart.so", RTLD_LAZY);
    // dvm 虚拟机
    // void *libdvm = (void *) dlopen("libdvm.so", RTLD_LAZY);
    // dexFile = (JNINativeMethod *) dlsym(libdvm, "dvm_dalvik_system_DexFile");
    openMemory22 = (org_artDexFileOpenMemory22) by_dlsym(libart, openMemory22Name);
    if (openMemory22 == nullptr) {
        LOGI("use sdk 23 openMemory.");
        openMemory22 = (org_artDexFileOpenMemory22) by_dlsym(libart, openMemory23Name);
    }
    LOGI("libart: %p, openMemory: %p", libart, openMemory22);
    mapDummy = (org_artMemMapMapDummy) by_dlsym(libart, "_ZN3art6MemMap8MapDummyEPKcPhm");
    if (mapDummy == nullptr) {
        mapDummy = (org_artMemMapMapDummy) by_dlsym(libart, "_ZN3art6MemMap8MapDummyEPKcPhj");
    }
    LOGI("MapDummy:%p", mapDummy);

    if (!useOpenMemory) {
        dexFile = static_cast<JNINativeMethod *>(by_dlsym(libart,
                                                          "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_10OatDexFileEPS9_"));
        if (checkFunction(dexFile, "openDexFile", "([B)I", &openDexFile) == 0) {
            openDexFile = nullptr;
            LOGI("not found openDexFile method!");
        } else {
            LOGI("found openDexFile method!");
        }
    }
    JNIEnv *env = nullptr;
    jint ret = vm->GetEnv((void **) &env, JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        LOGE("JNI_VERSION_1_6: jni_replace JVM ERROR:GetEnv");
        ret = vm->GetEnv((void **) &env, JNI_VERSION_1_4);
        if (ret != JNI_OK) {
            LOGE("JNI_VERSION_1_4: jni_replace JVM ERROR:GetEnv");
            ret = vm->GetEnv((void **) &env, JNI_VERSION_1_2);
            if (ret != JNI_OK) {
                LOGE("JNI_VERSION_1_2: jni_replace JVM ERROR:GetEnv");
                return JNI_VERSION_1_1;
            }
            return JNI_VERSION_1_2;
        }
        return JNI_VERSION_1_4;
    }
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_zhh_jiagu_shell_util_ShellNativeMethod_loadDexFile(JNIEnv *env, jclass jc,
                                                            jbyteArray dexBytes,
                                                            jlong dexSize) {
    if (useOpenMemory) {
        jbyte *bytes = env->GetByteArrayElements(dexBytes, 0);
        int inDexLen = env->GetArrayLength(dexBytes);
        char *pDex = new char[inDexLen + 1];
        const char *location = "";
        std::string err_msg;
        memset(pDex, 0, inDexLen + 1);
        memcpy(pDex, bytes, inDexLen);
        pDex[inDexLen] = '\0';
        env->ReleaseByteArrayElements(dexBytes, bytes, 0);
        const Header *dex_header = reinterpret_cast<const Header *>(pDex);
        LOGI("获取 dex 对应的 cookie, and checksum: %u, fileSize: %d, dexSize: %lld, inDexLen: %d",
             dex_header->checksum_, dex_header->file_size_, dexSize, inDexLen);

        void *mem_map = mapDummy(location, (uint8_t *) pDex, inDexLen + 1);
//        printCPlusPlusFunction((JNINativeMethod *) mem_map);

        void *value = openMemory22(location,
                                   dex_header->checksum_,
                                   mem_map,
                                   &err_msg);
        LOGI("dexBytes: %p, jbytes: %p, pDex: %p, mem_map: %p", dexBytes, bytes, pDex, mem_map);
        LOGI("dexBytes: %s, jbytes: %s, pDex: %s, mem_map: %s", (uint8_t *) dexBytes,
             (uint8_t *) bytes, (uint8_t *) pDex, (uint8_t *) mem_map);
//        void *value = openMemory22((uint8_t *) pDex,
//                                   inDexLen,
//                                   location,
//                                   dex_header->checksum_,
//                                   mem_map,
//                                   nullptr,
//                                   &err_msg);
        LOGI("cookie value: %p", value);
        jlong cookie = replace_cookie(env, value, 22);
        return cookie;
    } else {
        union JValue preSult{};
        jint result;
        jbyte *oldDex = env->GetByteArrayElements(dexBytes, JNI_FALSE);
        auto *ao = (ArrayObject *) malloc(16 + dexSize);
        ao->length = dexSize;
        memcpy(ao + 16, oldDex, dexSize);
        u4 args[] = {(u4) (size_t) ao};
        if (openDexFile != nullptr) {
            openDexFile(args, &preSult);
        } else {
            result = -1;
        }
        result = (jint) (size_t) preSult.l;
        LOGI("openDexFile Function Result is %d", result);
        return result;
    }
}

void *fake_dlsym(void *handle, const char *name) {
    int k;
    struct ctx *ctx = (struct ctx *) handle;
    if (!ctx) {
        exit(0);
    }
    Elf_Sym *sym = (Elf_Sym *) ctx->dynsym;
    char *strings = (char *) ctx->dynstr;

    for (k = 0; k < ctx->nsyms; k++, sym++)
        if (strcmp(strings + sym->st_name, name) == 0) {
            /*  NB: sym->st_value is an offset into the section for relocatables,
            but a VMA for shared libs or exe files, so we have to subtract the bias */
            void *ret = (char *) ctx->load_addr + sym->st_value - ctx->bias;
            //log_info("%s found at %p", name, ret);
            return ret;
        }
    return 0;
}

void *fake_dlopen(const char *libpath, int flags) {
    FILE *maps;
    char buff[256];
    struct ctx *ctx = 0;
    off_t load_addr, size;
    int k, fd = -1, found = 0;
    char *shoff;
    Elf_Ehdr *elf = (Elf_Ehdr *) MAP_FAILED;

#define fatal(fmt, args...) do { ; goto err_exit; } while(0)

    maps = fopen("/proc/self/maps", "r");
    if (!maps) fatal("failed to open maps");

    while (fgets(buff, sizeof(buff), maps)) {
        if ((strstr(buff, "r-xp") || strstr(buff, "r--p")) && strstr(buff, libpath)) {
            found = 1;
            break;
        }
    }

    fclose(maps);

    if (!found) fatal("%s not found in my userspace", libpath);

    if (sscanf(buff, "%lx", &load_addr) != 1)
        fatal("failed to read load address for %s", libpath);

    //log_info("%s loaded in Android at 0x%08lx", libpath, load_addr);

    /* Now, mmap the same library once again */

    fd = open(libpath, O_RDONLY);
    if (fd < 0) fatal("failed to open %s", libpath);

    size = lseek(fd, 0, SEEK_END);
    if (size <= 0) fatal("lseek() failed for %s", libpath);

    elf = (Elf_Ehdr *) mmap(0, size, PROT_READ, MAP_SHARED, fd, 0);
    close(fd);
    fd = -1;

    if (elf == MAP_FAILED) fatal("mmap() failed for %s", libpath);

    ctx = (struct ctx *) calloc(1, sizeof(struct ctx));
    if (!ctx) fatal("no memory for %s", libpath);

    ctx->load_addr = (void *) load_addr;
    shoff = ((char *) elf) + elf->e_shoff;

    for (k = 0; k < elf->e_shnum; k++, shoff += elf->e_shentsize) {

        Elf_Shdr *sh = (Elf_Shdr *) shoff;
        LOGI("%s: k=%d shdr=%p type=%x", __func__, k, sh, sh->sh_type);

        switch (sh->sh_type) {

            case SHT_DYNSYM:
                if (ctx->dynsym) fatal("%s: duplicate DYNSYM sections", libpath); /* .dynsym */
                ctx->dynsym = malloc(sh->sh_size);
                if (!ctx->dynsym) fatal("%s: no memory for .dynsym", libpath);
                memcpy(ctx->dynsym, ((char *) elf) + sh->sh_offset, sh->sh_size);
                ctx->nsyms = (sh->sh_size / sizeof(Elf_Sym));
                break;

            case SHT_STRTAB:
                if (ctx->dynstr) break;    /* .dynstr is guaranteed to be the first STRTAB */
                ctx->dynstr = malloc(sh->sh_size);
                if (!ctx->dynstr) fatal("%s: no memory for .dynstr", libpath);
                memcpy(ctx->dynstr, ((char *) elf) + sh->sh_offset, sh->sh_size);
                break;

            case SHT_PROGBITS:
                if (!ctx->dynstr || !ctx->dynsym) break;
                /* won't even bother checking against the section name */
                ctx->bias = (off_t) sh->sh_addr - (off_t) sh->sh_offset;
                k = elf->e_shnum;  /* exit for */
                break;
        }
    }

    munmap(elf, size);
    elf = 0;

    if (!ctx->dynstr || !ctx->dynsym) fatal("dynamic sections not found in %s", libpath);

#undef fatal

    LOGI("%s: ok, dynsym = %p, dynstr = %p", libpath, ctx->dynsym, ctx->dynstr);

    return ctx;

    err_exit:
    if (fd >= 0) close(fd);
    if (elf != MAP_FAILED) munmap(elf, size);
//    fake_dlclose(ctx);
    return 0;
}

jlong replace_cookie(JNIEnv *env, void *c_dex_cookie, int sdk_int) {
    if ((sdk_int == 21) || (sdk_int == 22)) {
        std::unique_ptr<std::vector<const void *>> dex_files(new std::vector<const void *>());
        dex_files.get()->push_back(c_dex_cookie);
        jlong mCookie = static_cast<jlong>(reinterpret_cast<uintptr_t>(dex_files.release()));
        return mCookie;
    }

}
