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

#define LOG_TAG  "shell_native_method2"
/* 以下是 OpenMemory函数在内存中对外的方法名 */
/*Android 5*/
#define OpenMemory21 "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPS9_"
/*Android 5.1*/
#define OpenMemory22 "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_7OatFileEPS9_"
/*Android 6*/
#define OpenMemory23 "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_10OatDexFileEPS9_"
/*Android 7.1, 与 Android 6一致，但发现了另一个 OpenMemory 方法。OpenMemory25 未确定方法的具体参数，用于测试*/
#define OpenMemory25 "_ZN3art7DexFile10OpenMemoryERKNSt3__112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEEjPNS_6MemMapEPS7_"

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

std::unique_ptr<const void *> loadDexAboveAndroid7_1(const char *base, size_t size);

jobject *openMemory(JNIEnv *env, jclass clazz, jbyteArray dex_bytes, jlong dex_size, jint sdk_int) {
    jbyte *bytes = env->GetByteArrayElements(dex_bytes, JNI_FALSE);
    LOG_D(LOG_TAG, "native method get dex file magic number:%c%c%c", bytes[0], bytes[1], bytes[2]);
    void *value;
    LOG_D(LOG_TAG, "coming into OpenMemoryNative and current sdk_int:%d", sdk_int);
    if (sdk_int <= 22) {/* android 5.0, 5.1*/
        LOG_D(LOG_TAG, "coming into OpenMemoryNative method in Android 5");
        value = loadDexInAndroid5(sdk_int, (char *) bytes, (size_t) dex_size);
    } else if (sdk_int < 25) {/* android 6.0 7.0 */
        LOG_D(LOG_TAG, "coming into OpenMemoryNative method in Android 6 and above");
        value = loadDexAboveAndroid6((char *) bytes, (size_t) dex_size).get();
    } else {/* android 7.1 */
        LOG_D(LOG_TAG, "coming into OpenMemoryNative method in Android 7.1");
        value = loadDexAboveAndroid7_1((char *) bytes, (size_t) dex_size).get();
    }

    if (value) {
        jlongArray array = env->NewLongArray(1);
        env->SetLongArrayRegion(array, 0, 1, (jlong *) value);
        return (jobject *) array;
    }
    return (jobject *) nullptr;
}

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

/* 加载内存dex，适用于android 6.0 7.0 */
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

/* 加载内存dex，适用于android 7.1 */
std::unique_ptr<const void *> loadDexAboveAndroid7_1(const char *base, size_t size) {

    const char *location = "Anonymous-DexFile";
    std::string err_msg;
    std::unique_ptr<const void *> value;
    const auto *dex_header = reinterpret_cast<const Header *>(base);

    std::ostringstream oss;
    oss << "check magic number in " << location << " and result should start with dex:"
        << dex_header->magic_;
    LOG_D(LOG_TAG, "%s", oss.str().c_str());

    auto func23 = (org_artDexFileOpenMemory23) by_dlsym(artHandle, OpenMemory23);
    LOG_D(LOG_TAG, "invoke OpenMemory Method by Pointer and OpenMemory ptr:%p", func23);
    auto mapDummy = (org_artMemMapMapDummy25) by_dlsym(artHandle, "_ZN3art6MemMap8MapDummyEPKcPhj");
    void *mem_map = mapDummy(location, (uint8_t *) base, size);
    LOG_D(LOG_TAG, "MapDummy ptr:%p, and mem_map: %p", mapDummy, mem_map);

    value = func23((const uint8_t *) base,
                   size,
                   location,
                   dex_header->checksum_,
                   nullptr,
                   nullptr,
                   &err_msg);

//    const ClassDef *class_defs_ = reinterpret_cast<const ClassDef *>(base +
//                                                                     dex_header->class_defs_off_);
//    ClassDef class_def = class_defs_[0];
//    GetClassDescriptor getClassDescriptor = (GetClassDescriptor) by_dlsym(artHandle,
//                                                                          "_ZN3art2gc4Heap22SafeGetClassDescriptorEPNS_6mirror5ClassE");
//    LOG_D(LOG_TAG, "class_def_size:%d, getClassDescriptor:%p, idx:%d", dex_header->class_defs_size_,
//         getClassDescriptor, class_def.class_idx_);
//    LOG_D(LOG_TAG, "classNames: %s", getClassDescriptor(class_def));

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
    jclass clz = env->FindClass("com/zhh/jiagu/shell/util/ShellNativeMethod2");
    JNINativeMethod methods[] = {{"openMemory", "([BJI)Ljava/lang/Object;", (void *) openMemory}};
    env->RegisterNatives(clz, methods, sizeof(methods) / sizeof(methods[0]));
    // 打开 libart.so 文件
    artHandle = (void *) by_dlopen("libart.so", RTLD_LAZY);
    std::stringstream ss;
    ss << std::hex << ret;
    std::string version;
    ss >> version;
    LOG_D(LOG_TAG, "android sdk: %d, libart ptr: %p and jni version: 0x%s", by_rt_api_level(),
          artHandle, version.c_str());
    return ret;
}