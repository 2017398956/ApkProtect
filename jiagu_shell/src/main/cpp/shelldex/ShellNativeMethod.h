//
// Created by nfli on 2024/7/29.
//

#include <jni.h>

#ifndef APKPROTECT_SHELLNATIVEMETHOD_H
#define APKPROTECT_SHELLNATIVEMETHOD_H
#endif

#ifndef Included_personal_nfl_protect_shell_util_ShellNativeMethod
#define Included_personal_nfl_protect_shell_util_ShellNativeMethod
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL
Java_personal_nfl_protect_shell_util_ShellNativeMethod_loadDexFile(JNIEnv *env, jclass clazz,
                                                                   jbyteArray dex_bytes,
                                                                   jlong dex_length);

#ifdef __cplusplus
}
#endif
#endif
