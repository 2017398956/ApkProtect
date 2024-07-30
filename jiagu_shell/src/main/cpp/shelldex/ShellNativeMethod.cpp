#include <jni.h>
#include "ShellNativeMethod.h"
#include "datadefine.h"
#include <stdlib.h>
#include <dlfcn.h>
#include <stdio.h>
#include <android/log.h>
#include <string.h>

#define  LOG_TAG    "ShellNativeMethod"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

int checkFunction(JNINativeMethod *table, const char *name, const char *sig,
                  void(**fnPtrOut)(u4 const *, union JValue *));

JNINativeMethod *dexFile;

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

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    void *env;
    void *soFile = (void *) dlopen("libart.so", RTLD_LAZY);
    dexFile = (JNINativeMethod *) dlsym(soFile, "dvm_dalvik_system_DexFile");

    void *tempSo = (void *) dlopen("libsxjiagu.so", RTLD_LAZY);
    auto *jm = (JNINativeMethod *) dlsym(tempSo, "Java_com_zhh_jiagu_shell_util_AESUtil_encrypt");
    LOGI("find libdvm method.%p, %p, %p, %p", jm, jm->fnPtr, jm->name, jm->signature);
    LOGI("find libdvm method.%p, %p, %p, %p", soFile, tempSo, jm->name, jm->signature);
    if (checkFunction(dexFile, "openDexFile", "([B)I", &openDexFile) == 0) {
        openDexFile = nullptr;
        LOGI("not found openDexFile method!");
    } else {
        LOGI("found openDexFile method!");
    }
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK) {
        LOGI("GetEnv failure");
        return -1;
    }
    return JNI_VERSION_1_4;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_zhh_jiagu_shell_util_ShellNativeMethod_loadDexFile(JNIEnv *env, jclass jc,
                                                            jbyteArray dexBytes,
                                                            jlong dexSize) {
    union JValue preSult{};
    jint result;
    jbyte *oldDex = env->GetByteArrayElements(dexBytes, JNI_FALSE);
    auto *ao = (ArrayObject *) malloc(16 + dexSize);
    ao->length = dexSize;
    memcpy(ao + 16, oldDex, dexSize);
    u4 args[] = {(u4)(size_t) ao};
    if (openDexFile != nullptr) {
        openDexFile(args, &preSult);
    } else {
        result = -1;
    }
    result = (jint)(size_t) preSult.l;
    LOGI("openDexFile Function Result is %d", result);
    return result;
}
