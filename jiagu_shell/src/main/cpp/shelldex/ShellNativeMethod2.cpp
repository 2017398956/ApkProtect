/**
 * 和 ShellNativeMethod.cpp 功能一样
 */
#include <jni.h>
#include <string>
#include <dlfcn.h>
#include <cstdint>
#include "dalvik_system_DexFile.h"
#include "../byopen/byopen.h"
#include "../utils/my_phone_info_util.h"
#include "../utils/my_android_log.h"
#include <sstream>
#include <iosfwd>
#include <vector>
#include <ctime>
#include <chrono>
#include <sys/time.h>
#include "../hook/plt_got_native_open_asset.h"

#define LOG_TAG  "shell_native_method2"
/* 以下是 OpenMemory函数在内存中对外的方法名 */
/*Android 5*/
#define OpenMemory21 "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPS9_"
/*Android 5.1*/
#define OpenMemory22 "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_7OatFileEPS9_"
/*Android 6*/
#define OpenMemory23 "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_10OatDexFileEPS9_"
#define OpenMemory23_32_64 "_ZN3art7DexFile10OpenMemoryERKNSt3__112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEEjPNS_6MemMapEPS7_"
#define OpenMemory23_64 "_ZN3art7DexFile10OpenMemoryEPKhmRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_10OatDexFileEPS9_"

/*Android 7.1, 与 Android 6一致，*/
// FIXME: 这里是从 64 位模拟器获取的值，由于看不到源码，会直接 crash
#define OpenMemory25_32_64 OpenMemory23_32_64
#define OpenMemory25_64 OpenMemory23_64

/*定义函数指针*/
typedef void *(*org_artDexFileOpenMemory21)(const uint8_t *base,
                                            size_t size,
                                            const std::string &location,
                                            uint32_t location_checksum,
                                            void *mem_map,
                                            std::string *error_msg);

typedef void *(*org_artDexFileOpenMemory22)(const uint8_t *base,
                                            size_t size,
                                            const std::string &location,
                                            uint32_t location_checksum,
                                            void *mem_map,
                                            const void *oat_file,
                                            std::string *error_msg);

typedef std::unique_ptr<const void *> (*org_artDexFileOpenMemory23)(const uint8_t *base,
                                                                    size_t size,
                                                                    const std::string &location,
                                                                    uint32_t location_checksum,
                                                                    void *mem_map,
                                                                    const void *oat_dex_file,
                                                                    std::string *error_msg);

// 执行这个方法后返回的是 mem_map
typedef void *(*org_artMemMapMapDummy25)(const char *name, uint8_t *addr, size_t byte_count);

typedef const char *(*GetClassDescriptor)(const ClassDef &class_def);

// libart.so 指针
void *artHandle = nullptr;

void *loadDexInAndroid5(int sdk_int, const char *base, size_t size);

std::unique_ptr<const void *> loadDexAboveAndroid6(const char *base, size_t size);

std::unique_ptr<const void *> loadDexAboveAndroid7_1(jbyte *base, size_t size);

void *createCookie(jbyte *bytes, jlong dex_size, jint sdk_int) {
    LOG_D(LOG_TAG, "native method get dex file magic number:%c%c%c", bytes[0], bytes[1], bytes[2]);
    LOG_D(LOG_TAG, "coming into OpenMemoryNative and current sdk_int:%d", sdk_int);
    void *value;
    if (sdk_int <= 22) {/* android 5.0, 5.1*/
        LOG_D(LOG_TAG, "coming into OpenMemoryNative method in Android 5");
        value = loadDexInAndroid5(sdk_int, (char *) bytes, (size_t) dex_size);
    } else if (sdk_int < 25) {/* android 6.0 7.0 */
        LOG_D(LOG_TAG, "coming into OpenMemoryNative method in Android 6 and above");
        value = loadDexAboveAndroid7_1(bytes, (size_t) dex_size).get();
    } else {/* android 7.1 */
        LOG_D(LOG_TAG, "coming into OpenMemoryNative method in Android 7.1");
        value = loadDexAboveAndroid7_1(bytes, (size_t) dex_size).get();
    }
    return value;
}

jobject *openMemory(JNIEnv *env, jclass clazz, jbyteArray dex_bytes, jlong dex_size, jint sdk_int) {
    jbyte *bytes = env->GetByteArrayElements(dex_bytes, JNI_FALSE);
    void *value = createCookie(bytes, dex_size, sdk_int);
    if (value) {
        LOGD("cookie ptr:%lld, %p", (jlong) value, &value);
        jlongArray array = env->NewLongArray(2);
        env->SetLongArrayRegion(array, 1, 1, (jlong *) &value);
        return (jobject *) array;
    }
    return (jobject *) nullptr;
}

jobject *
openMemory2(JNIEnv *env, jclass clazz, jobject dexes_bytes, jint sdk_int) {
    jclass list = env->FindClass("java/util/List");
    jmethodID list_size = env->GetMethodID(list, "size", "()I");
    jmethodID list_get = env->GetMethodID(list, "get", "(I)Ljava/lang/Object;");
    jint dex_number = env->CallIntMethod(dexes_bytes, list_size);
    jlongArray array = env->NewLongArray(1 + dex_number);
    for (int i = 0; i < dex_number; ++i) {
        auto dex_bytes = (jbyteArray) env->CallObjectMethod(dexes_bytes, list_get, i);
        jsize dex_size = env->GetArrayLength(dex_bytes);
        jboolean *is_copy = nullptr;
//        auto *bytes = (jbyte *) malloc(dex_size);
//        env->GetByteArrayRegion(dex_bytes, 0, dex_size, bytes);
        jbyte *bytes = env->GetByteArrayElements(dex_bytes, is_copy);
        LOGD("before create cookie bytes:%p, size:%d, copy:%s", bytes, dex_size, is_copy);
        void *value = createCookie(bytes, dex_size, sdk_int);
        if (value) {
            LOGD("cookie ptr:%lld, %p", (jlong) value, &value);
            if (i == 0) {
                env->SetLongArrayRegion(array, 0, 1, (jlong *) &value);
            }
            env->SetLongArrayRegion(array, i + 1, 1, (jlong *) &value);
        }
        env->ReleaseByteArrayElements(dex_bytes, bytes, 0);
    }

    return (jobject *) array;
}

int dex_count = 0;

/* 加载内存dex，适用于android 5, 5.1 */
void *loadDexInAndroid5(int sdk_int, const char *base, size_t size) {
    std::string location = "Anonymous-DexFile";
    std::string err_msg;
    void *value;

    const auto *dex_header = reinterpret_cast<const Header *>(base);

    if (sdk_int == 21) {/* android 5.0 */
        LOG_E(LOG_TAG, "try to catch OpenMemory Method Pointer");
        auto func21 = (org_artDexFileOpenMemory21) by_dlsym(artHandle, OpenMemory21);
        LOG_E(LOG_TAG, "try to invoke OpenMemory Method by Pointer");
        value = func21((const unsigned char *) base,
                       (size_t) size,
                       location,
                       dex_header->checksum_,
                       nullptr,
                       &err_msg);
    } else if (sdk_int == 22) {/* android 5.1 */
        LOG_E(LOG_TAG, "try to catch OpenMemory Method Pointer");
        auto func22 = (org_artDexFileOpenMemory22) by_dlsym(artHandle, OpenMemory22);
        LOG_E(LOG_TAG, "try to invoke OpenMemory Method by Pointer");
        value = func22((const unsigned char *) base,
                       size,
                       location,
                       dex_header->checksum_,
                       nullptr,
                       nullptr,
                       &err_msg);
    }

    if (!value) {
        LOG_E(LOG_TAG, "fail to load dex in Android 5");
    }

    return value;

}

/* (原方法，使用 loadDexAboveAndroid7_1 代替) 加载内存dex，适用于android 6.0 7.0 */
std::unique_ptr<const void *> loadDexAboveAndroid6(const char *base, size_t size) {

    std::string location = "Anonymous-DexFile";
    std::string err_msg;
    std::unique_ptr<const void *> value;

    const auto *dex_header = reinterpret_cast<const Header *>(base);
    auto func23 = (org_artDexFileOpenMemory23) by_dlsym(artHandle, OpenMemory23);
    LOG_D(LOG_TAG, "invoke OpenMemory Method by Pointer and OpenMemory ptr:%p", func23);
    value = func23((const unsigned char *) base,
                   size,
                   location,
                   dex_header->checksum_,
                   nullptr,
                   nullptr,
                   &err_msg);

    if (!value) {
        LOG_E(LOG_TAG, "fail to load dex in Android 6 and above");
        return nullptr;
    }

    return value;

}

// FIXME: 测试获取 dex 中的 class 列表
void testGetClassNameList(const char *base, const Header *dex_header) {
    const ClassDef *class_defs_ = reinterpret_cast<const ClassDef *>(base +
                                                                     dex_header->class_defs_off_);
    ClassDef class_def = class_defs_[0];
    GetClassDescriptor getClassDescriptor = (GetClassDescriptor) by_dlsym(artHandle,
                                                                          "_ZN3art2gc4Heap22SafeGetClassDescriptorEPNS_6mirror5ClassE");
    LOG_D(LOG_TAG, "class_def_size:%d, getClassDescriptor:%p, idx:%d", dex_header->class_defs_size_,
          getClassDescriptor, class_def.class_idx_);
    LOG_D(LOG_TAG, "classNames: %s", getClassDescriptor(class_def));
}

/* 加载内存dex，适用于android 6.0 7.0 7.1 */
std::unique_ptr<const void *> loadDexAboveAndroid7_1(jbyte *base, size_t size) {
    dex_count++;
    std::string dex_name = "Anonymous-DexFile";
    dex_name.append(std::to_string(dex_count));
    const char *location = dex_name.c_str();
    std::string err_msg;

    const auto *dex_header = reinterpret_cast<const Header *>(base);
    std::ostringstream oss;
    oss << &base << " check magic number in " << location << " and result should start with dex:"
        << dex_header->magic_ << " class_defs_size:" << dex_header->class_defs_size_;
    LOG_D(LOG_TAG, "%s", oss.str().c_str());
    auto func23 = (org_artDexFileOpenMemory23) by_dlsym(artHandle, OpenMemory23);
    if (func23 == nullptr) {
        LOGI("current abi is x64");
        func23 = (org_artDexFileOpenMemory23) by_dlsym(artHandle, OpenMemory25_64);
    }
    LOG_D(LOG_TAG, "invoke OpenMemory Method by Pointer and OpenMemory ptr:%p", func23);
    auto mapDummy = (org_artMemMapMapDummy25) by_dlsym(artHandle, "_ZN3art6MemMap8MapDummyEPKcPhj");
    if (mapDummy) {
        // 64 位
        mapDummy = (org_artMemMapMapDummy25) by_dlsym(artHandle, "_ZN3art6MemMap8MapDummyEPKcPhm");
    }
    void *mem_map = nullptr;
    if (mapDummy) {
        mem_map = mapDummy(location, (uint8_t *) base, size);
        LOG_D(LOG_TAG, "MapDummy ptr:%p, and mem_map: %p", mapDummy, mem_map);
    }

    std::unique_ptr<const void *> value = func23((const uint8_t *) base,
                   size,
                   location,
                   dex_header->checksum_,
                   mem_map,
                   nullptr,
                   &err_msg);

    // testGetClassNameList(base, dex_header);

    if (!value) {
        LOG_E(LOG_TAG, "fail to load dex in Android 7.1 and err_msg:%s", err_msg.c_str());
        return nullptr;
    }
    return value;
}


JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    // 获取环境
    JNIEnv *env = nullptr;
    jint ret = vm->GetEnv((void **) &env, JNI_VERSION_1_6);
    if (ret != JNI_OK) {
        ret = vm->GetEnv((void **) &env, JNI_VERSION_1_4);
        if (ret != JNI_OK) {
            ret = vm->GetEnv((void **) &env, JNI_VERSION_1_2);
            if (ret != JNI_OK) {
                ret = JNI_VERSION_1_1;
            } else {
                ret = JNI_VERSION_1_2;
            }
        } else {
            ret = JNI_VERSION_1_4;
        }
    } else {
        ret = JNI_VERSION_1_6;
    }
    // 注册 jni 方法
    jclass clz = env->FindClass("personal/nfl/protect/shell/util/ShellNativeMethod2");
    JNINativeMethod methods[] = {
            {"openMemory", "([BJI)Ljava/lang/Object;",              (void *) openMemory},
            {"openMemory", "(Ljava/util/List;I)Ljava/lang/Object;", (void *) openMemory2},
    };
    env->RegisterNatives(clz, methods, sizeof(methods) / sizeof(methods[0]));
    // 打开 libart.so 文件
    artHandle = (void *) by_dlopen("libart.so", RTLD_LAZY);
    std::stringstream ss;
    ss << std::hex << ret;
    std::string version;
    ss >> version;
    LOG_D(LOG_TAG, "android sdk: %d, libart ptr: %p and jni version: 0x%s", by_rt_api_level(),
          artHandle, version.c_str());
     plt_got_hook();
    return ret;
}