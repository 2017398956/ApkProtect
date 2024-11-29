//
//
//
#include <stdio.h>
#include <dlfcn.h>
#include <unistd.h>
#include <android/log.h>
#include <fcntl.h>
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

void *libsample_handle = NULL;
typedef void (*sample_test_strlen_t)(int);
sample_test_strlen_t sample_test_strlen = NULL;
static void my_sample(int flags) {
    LOGD("my_sample:pre\n");
    sample_test_strlen(flags);
    LOGD("my_sample:aft\n");
}
void hacker_sample() {
    if (NULL == libsample_handle) {
        libsample_handle = dlopen("libsample.so", RTLD_NOW);
        // libsample_handle:0xd9aaa8eb2103fb01
        LOGD("libsample_handle:%p\n", libsample_handle);
        if (NULL != libsample_handle) {
            void* fm = dlsym(libsample_handle, "sample_test_strlen");
            LOGD("sample_test_strlen:%p\n", fm);
            void** fm_ref = (void**)fm;
            LOGD("fm_ref:%p\n", fm_ref);
            *fm_ref = (void *) my_sample;
            sample_test_strlen = (sample_test_strlen_t)fm;
        }
    }
}

void run_sample() {
    libsample_handle = dlopen("libsample.so", RTLD_NOW);
    LOGD("run_sample libsample_handle:%p\n", libsample_handle);
    if (NULL != libsample_handle) {
        void *temp = dlsym(libsample_handle, "sample_test_strlen");
        LOGD("run_sample sample_test_strlen:%p\n", temp);
    }
}

void plt_got_hook() {
//    void * artHandle = (void *) by_dlopen("libc.so", RTLD_LAZY);
//    void **got_func_addr = (void **)by_dlsym(artHandle, "open");
    void *got_func_addr = dlsym(RTLD_DEFAULT, "open");
    if (got_func_addr == nullptr) {
        LOGD("Error: Cannot find the GOT entry of 'open' function");
        return;
    }
    LOGD("open method address:%p", got_func_addr);
    // Backup the original function
    orig_open = (orig_open_func_type) got_func_addr;
    // Replace the GOT entry with the address of our hook function
    void **open_fun_ref_addr = &got_func_addr;
    LOGD("open_fun_ref_addr: %p", *open_fun_ref_addr);
    *open_fun_ref_addr = (void *)my_open;
    LOGD("open_fun_ref_addr: %p", *open_fun_ref_addr);
    // open 文件
    int open_result = open("a", 1);
    LOGD("open file result:%d", open_result);
    hacker_sample();
    run_sample();
}

#ifdef __cplusplus
}
#endif