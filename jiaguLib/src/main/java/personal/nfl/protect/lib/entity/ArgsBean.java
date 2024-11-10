package personal.nfl.protect.lib.entity;

import org.kohsuke.args4j.Option;

/**
 * 签名配置信息
 */
public class ArgsBean {
    @Option(name = "--apk_file")
    public String apkFile;
    @Option(name = "--keystore_cfg")
    public String keystoreCfg;
    public String storeFile;
    public String storePassword;
    public String alias;
    public String keyPassword;
    public boolean mergeDexFiles;
    public boolean v1SigningEnabled = true;
    public boolean v2SigningEnabled = true;
}
