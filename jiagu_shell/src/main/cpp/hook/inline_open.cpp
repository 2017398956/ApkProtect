//
//
//
#include <cstdio>
#include <dlfcn.h>
#include <unistd.h>
#include <android/log.h>
#include <asm-generic/mman.h>
#include <sys/mman.h>
#include <cstring>

#define TAG "NativeHook"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

typedef int (*orig_open_func_type)(const char *pathname, int flags);

orig_open_func_type orig_open;

#ifdef __cplusplus
extern "C" {
#endif

int my_open(const char *pathname, int flags) {
    LOGD("File opened: %s", pathname);
    return orig_open(pathname, flags);
}

void *get_function_address(const char *func_name) {
    void *handle = dlopen("libc.so", RTLD_NOW);
    if (!handle) {
        LOGD("Error: %s", dlerror());
        return NULL;
    }

    void *func_addr = dlsym(handle, func_name);
    dlclose(handle);

    return func_addr;
}

void inline_hook() {
    void *orig_func_addr = get_function_address("open");
    if (orig_func_addr == nullptr) {
        LOGD("Error: Cannot find the address of 'open' function");
        return;
    }

    // Backup the original function
    orig_open = (orig_open_func_type)orig_func_addr;

    // Change the page protection
    size_t page_size = sysconf(_SC_PAGESIZE);
    uintptr_t page_start = (uintptr_t)orig_func_addr & (~(page_size - 1));
    mprotect((void *)page_start, page_size, PROT_READ | PROT_WRITE | PROT_EXEC);

    // Construct the jump instruction
    unsigned char jump[8] = {0};
    jump[0] = 0x01;  // The machine code of the jump instruction
    *(void **)(jump + 1) = (void *) my_open;  // The address of our hook function

    // Write the jump instruction to the entry point of the target function
    memcpy(orig_func_addr, jump, sizeof(jump));
}

// orig_func_addr & (~(page_size - 1)) 这段代码的作用是获取包含 orig_func_addr 地址的内存页的起始地址。
// 这里使用了一个技巧：page_size 总是2的幂，因此 page_size - 1 的二进制表示形式是低位全为1，高位全为0，取反后低位全为0，高位全为1。
// 将 orig_func_addr 与 ~(page_size - 1) 进行与操作，可以将 orig_func_addr 的低位清零，从而得到内存页的起始地址。
// mprotect((void *)page_start, page_size, PROT_READ | PROT_WRITE | PROT_EXEC); 这行代码的作用是修改内存页的保护属性。
// mprotect 函数可以设置一块内存区域的保护属性，它接受三个参数：需要修改的内存区域的起始地址，内存区域的大小，以及新的保护属性。
// 在这里，我们将包含 orig_func_addr 地址的内存页的保护属性设置为可读、可写、可执行（PROT_READ | PROT_WRITE | PROT_EXEC），
// 以便我们可以修改这个内存页中的代码。

#ifdef __cplusplus
}
#endif