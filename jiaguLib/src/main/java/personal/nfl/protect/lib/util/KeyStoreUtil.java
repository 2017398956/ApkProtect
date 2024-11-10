package personal.nfl.protect.lib.util;

import personal.nfl.protect.lib.entity.ArgsBean;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class KeyStoreUtil {


    /**
     * 读取签名文件配置信息
     * @param configPath 签名文件路径
     * @return 签名对象
     */
    public static ArgsBean readKeyStoreConfig(String configPath){
        File cf = new File(configPath);
        if (!cf.exists()){
            System.out.println("签名配置文件不存在");
            return null;
        }

        try {
            List<String> lines = Files.readAllLines(cf.toPath());
            if (lines.isEmpty()){
                System.out.println("签名配置文件内容为空");
                return null;
            }
            ArgsBean store = new ArgsBean();
            for (String line : lines){
                if (line.trim().startsWith("storeFile")){
                    store.storeFile = line.split("=")[1].trim();
                }else if (line.trim().startsWith("storePassword")){
                    store.storePassword = line.split("=")[1].trim();
                }else if (line.trim().startsWith("alias")){
                    store.alias = line.split("=")[1].trim();
                }else if (line.trim().startsWith("keyPassword")){
                    store.keyPassword = line.split("=")[1].trim();
                }
            }
            return store;
        }catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }
}
