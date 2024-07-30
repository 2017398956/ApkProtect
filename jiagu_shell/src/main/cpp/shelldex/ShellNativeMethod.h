//
// Created by nfli on 2024/7/29.
//

#include <jni.h>

#ifndef APKPROTECT_SHELLNATIVEMETHOD_H
#define APKPROTECT_SHELLNATIVEMETHOD_H
#endif

#ifndef _Included_com_zhh_jiagu_shell_util_ShellNativeMethod
#define _Included_com_zhh_jiagu_shell_util_ShellNativeMethod
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_com_zhh_jiagu_shell_util_ShellNativeMethod_loadDexFile
        (JNIEnv *, jclass, jbyteArray, jlong);

#ifdef __cplusplus
}
#endif
#endif