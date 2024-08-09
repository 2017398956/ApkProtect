package com.zhh.jiagu.shell.util;

public class ShellNativeMethod2 {
    static {
        System.loadLibrary("ShellDex2");
    }

    //native层通过调用libart.so中的openMemory函数加载dex
    public static native Object openMemory(byte[] dex, long dexlen, int sdkInt);
}

