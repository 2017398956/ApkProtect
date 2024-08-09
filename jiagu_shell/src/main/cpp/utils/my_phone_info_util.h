//
// Created by 2017398956 on 2024/8/9.
//

#include <sys/system_properties.h>
#include <cstdio>
#include <cstring>

#ifndef APKPROTECT_PHONEINFOUTIL_H
#define APKPROTECT_PHONEINFOUTIL_H

#ifdef UTIL_DEBUG
#define by_assert_return_val(x, v)              do { if (!(x)) {by_trace_line("[assert]: expr: %s", #x); return (v); } } while(0)
#define by_assert_and_check_return_val(x, v) by_assert_return_val(x, v)
#else
#define by_check_return_val(x, v)               do { if (!(x)) return (v); } while (0)
#define by_assert_and_check_return_val(x, v) by_check_return_val(x, v)
#endif

/* Technical note regarding reading system properties.
 *
 * Try to use the new __system_property_read_callback API that appeared in
 * Android O / API level 26 when available. Otherwise use the deprecated
 * __system_property_get function.
 *
 * For more technical details from an NDK maintainer, see:
 * https://bugs.chromium.org/p/chromium/issues/detail?id=392191#c17
 */

/**
 * 获取系统属性值
 * Android 8 sdk 26 及以后 system_properties.h 中才有的方法
 */
void __attribute__((weak)) __system_property_read_callback(
        const prop_info *info,
        void (*callback)(void *cookie, const char *name, const char *value, uint32_t serial),
        void *cookie);

/**
 * 获取系统属性值
 * Android 8 之前获取系统属性的方法
 */
int __attribute__((weak)) __system_property_get(char const *name, char *value);

/**
 * callback used with __system_property_read_callback
 */
static void by_rt_prop_read_int(void *cookie, char const *name, char const *value,
                                uint32_t serial) {
    *(int *) cookie = atoi(value);
    (void) name;
    (void) serial;
}

/**
 * 读取命令行执行后的结果
 * @param cmd
 * @param data
 * @param maxn
 * @return 命令行执行结果的 int 值
 */
static int by_rt_process_read(char const *cmd, char *data, unsigned long maxn) {
    int n = 0;
    FILE *p = popen(cmd, "r");
    if (p) {
        char buf[256] = {0};
        char *pos = data;
        char *end = data + maxn;
        while (!feof(p)) {
            if (fgets(buf, sizeof(buf), p)) {
                int len = strlen(buf);
                if (pos + len < end) {
                    memcpy(pos, buf, len);
                    pos += len;
                    n += len;
                }
            }
        }

        *pos = '\0';
        pclose(p);
    }
    return n;
}

/**
 * get system property integer
 * 根据 name 获取系统对应的属性值
 */
static int by_rt_system_property_get_int(const char *name) {
    by_assert_and_check_return_val(name, -1);
    int result = 0;
    if (__system_property_read_callback) {
        const prop_info *info = __system_property_find(name);
        if (info) __system_property_read_callback(info, &by_rt_prop_read_int, &result);
    } else if (__system_property_get) {
        char value[PROP_VALUE_MAX] = {0};
        if (__system_property_get(name, value) >= 1)
            result = atoi(value);
    } else {
        // 如果系统中没有上面两种方法，那么直接使用命令行获取属性 name 的值
        char cmd[256];
        char value[PROP_VALUE_MAX];
        snprintf(cmd, sizeof(cmd), "getprop %s", name);
        if (by_rt_process_read(cmd, value, sizeof(value)) > 1)
            result = atoi(value);
    }
    return result;
}

/**
 * 获取 Android 系统版本号
 * @return Android 版本对应的数字
 */
static int by_rt_api_level() {
    return by_rt_system_property_get_int("ro.build.version.sdk");
}

#endif //APKPROTECT_PHONEINFOUTIL_H
