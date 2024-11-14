package personal.nfl.protect.shell;

public class Configs {
    /**
     * 是否在 Android 7.1 上使用 native 的方式加载加壳的 dex
     */
    public static final boolean TestOpenMemory25 = false;
    /**
     * 是否在 Android 6.0 上使用 native 的方式加载加壳的 dex
     *
     */
    public static final boolean TestOpenMemory23 = false;

    public static final boolean copyNative = true;
    public static final boolean replaceNativePath = false;
}
