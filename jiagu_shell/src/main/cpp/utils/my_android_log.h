//
// Created by 2017398956 on 2024/8/9.
//

#include <string>
#include <android/log.h>

#ifndef APKPROTECT_MY_ANDROID_LOG_H
#define APKPROTECT_MY_ANDROID_LOG_H

#define DEFAULT_TAG "MyNativeLib"

#define LOG_V(TAG, ...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)
#define LOG_I(TAG, ...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOG_D(TAG, ...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOG_E(TAG, ...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define LOGV(...) LOG_V(DEFAULT_TAG, __VA_ARGS__)
#define LOGI(...) LOG_I(DEFAULT_TAG, __VA_ARGS__)
#define LOGD(...) LOG_D(DEFAULT_TAG, __VA_ARGS__)
#define LOGE(...) LOG_E(DEFAULT_TAG, __VA_ARGS__)

#endif //APKPROTECT_MY_ANDROID_LOG_H
