//
//
//
#include <stdio.h>
#include <dlfcn.h>
#include <unistd.h>
#include <android/log.h>
#include "../byopen/byopen.h"
#include "plt_got_native_open_asset.h"

#define TAG "NativeHook"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

#ifdef __cplusplus
extern "C" {
#endif

typedef int (*orig_open_func_type)(const char *pathname, int flags);

orig_open_func_type orig_open;

int my_open(const char *pathname, int flags) {
    LOGD("File opened: %s", pathname);
    return orig_open(pathname, flags);
}

void plt_got_hook() {
    void * artHandle = (void *) by_dlopen("libandroidfw.so", RTLD_LAZY);
    void **got_func_addr = (void **)by_dlsym(artHandle, "_ZN7android12AssetManager23openAssetFromFileLockedERKNS_7String8ENS_5Asset10AccessModeE");
    // void **got_func_addr = (void **)by_dlsym(RTLD_DEFAULT, "_ZN7android12AssetManager23openAssetFromFileLockedERKNS_7String8ENS_5Asset10AccessModeE");
    if (got_func_addr == nullptr) {
        LOGD("Error: Cannot find the GOT entry of 'nativeOpenAsset' function");
        return;
    }
    LOGD("test nativeOpenAsset");
    // Backup the original function
    orig_open = (orig_open_func_type)*got_func_addr;

    // Replace the GOT entry with the address of our hook function
    *got_func_addr = (void *)(my_open);
}

#ifdef __cplusplus
}
#endif