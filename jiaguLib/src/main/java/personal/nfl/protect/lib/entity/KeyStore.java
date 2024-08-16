package personal.nfl.protect.lib.entity;

/**
 * 签名配置信息
 */
public class KeyStore {
    public String storeFile;
    public String storePassword;
    public String alias;
    public String keyPassword;
    public boolean mergeDexFiles;
    public boolean v1SigningEnabled = true;
    public boolean v2SigningEnabled = true;
}
