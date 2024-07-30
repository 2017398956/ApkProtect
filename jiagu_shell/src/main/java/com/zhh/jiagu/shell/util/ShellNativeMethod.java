package com.zhh.jiagu.shell.util;

public class ShellNativeMethod {
    static {
        System.loadLibrary("ShellDex");
    }

    public static native int loadDexFile(byte[] dexBytes, long dexLength);
}

