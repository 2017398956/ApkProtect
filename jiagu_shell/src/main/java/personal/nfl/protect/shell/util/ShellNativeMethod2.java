package personal.nfl.protect.shell.util;

import java.util.List;

public class ShellNativeMethod2 {
    static {
        LogUtil.debug("before load memory dex.");
        System.loadLibrary("ShellDex2");
    }

    //native层通过调用libart.so中的openMemory函数加载dex
    public static native Object openMemory(byte[] dex, long dexlen, int sdkInt);

    public static native Object openMemory(List<byte[]> dexList, int sdkInt);
}

