package com.zhh.jiagu.shell.util;

/**
 * @deprecated use {@link ShellNativeMethod2#OpenMemory(byte[], long, int)} instead.
 */
public class ShellNativeMethod {
    static {
        System.loadLibrary("ShellDex");
    }

    public static native int loadDexFile(byte[] dexBytes, long dexLength);
}

