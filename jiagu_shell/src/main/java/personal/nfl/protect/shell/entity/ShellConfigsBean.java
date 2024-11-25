package personal.nfl.protect.shell.entity;

import java.util.HashMap;

public class ShellConfigsBean {
    // so name | so path
    public HashMap<String, String> arm64_v8a = new HashMap<>();
    public HashMap<String, String> armeabi_v7a = new HashMap<>();
    public HashMap<String, String> x86_64 = new HashMap<>();
    public HashMap<String, String> x86 = new HashMap<>();
    public HashMap<String, String> soResult = new HashMap<>();
    /**
     * 防止 debug 和 so 动态注入
     */
    public boolean debuggable = false;
    public boolean canResign = false;
    public String sha1;
    public boolean assets;
    public boolean encryptNative;

//    public String toJsonString() {
//        HashMap<String, HashMap<String, String>> allSo = new HashMap<>();
//        allSo.put("arm64_v8a", arm64_v8a);
//        allSo.put("armeabi_v7a", armeabi_v7a);
//        allSo.put("x86_64", x86_64);
//        allSo.put("x86", x86);
//    }
}
