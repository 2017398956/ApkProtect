package personal.nfl.protect.lib;

import com.reandroid.app.AndroidManifest;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.utils.StringsUtil;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.Adler32;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import personal.nfl.protect.lib.entity.ArgsBean;
import personal.nfl.protect.lib.entity.ShellConfigsBean;
import personal.nfl.protect.lib.util.AESUtil;
import personal.nfl.protect.lib.util.FileUtils;
import personal.nfl.protect.lib.util.KeyStoreUtil;
import personal.nfl.protect.lib.util.ProcessUtil;
import personal.nfl.protect.lib.util.Zip4jUtil;
import personal.nfl.protect.lib.util.ZipUtil;

public class JiaGuMain {

    //    private final static String ROOT = "jiaguLib/";
    private static String ROOT = "";
    private static String OUT_TMP = ROOT + "temp/";
    private static String ORIGIN_APK = "demo/release/demo-release.apk";
    private ArgsBean argsBean = new ArgsBean();
    private ShellConfigsBean shellConfigsBean;
    private String apkSha1;
    private String[] shellNativeLibraryNames = new String[]{"libsxjiagu.so", "libShellDex2.so"};

    /**
     * 是否发布为Jar包，运行的
     */
    private final static boolean isRelease = true;

    static {
        File file = new File(ROOT);
        String strDll = file.getAbsolutePath() + (isRelease ? "" : "/jiaguLib") + "/libs/sx_jiagu.dll";
        // load - 支持下的 dll 库
        System.load(strDll);//这是我即将要重新实现的动态库名字
    }

    public static void showHelp(CmdLineParser parser) {
        System.out.println("参数说明 [options ...] [arguments...]");
        parser.printUsage(System.out);
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        JiaGuMain jiaguMain = new JiaGuMain();
        CmdLineParser cmdLineParser = new CmdLineParser(jiaguMain.argsBean);
        try {
            cmdLineParser.parseArgument(args);
        } catch (CmdLineException e) {
            // showHelp(cmdLineParser);
            throw new RuntimeException(e.getLocalizedMessage());
        }
        if (jiaguMain.argsBean.apkFile == null || !jiaguMain.argsBean.apkFile.endsWith(".apk")) {
            throw new RuntimeException("--apk_file is not a apk file.");
        }
        ORIGIN_APK = jiaguMain.argsBean.apkFile;
        jiaguMain.apkSha1 = getApkSHA1(ORIGIN_APK);
        System.out.println("apk sha1:" + jiaguMain.apkSha1);
        if (!isRelease) {
            ROOT = "jiaguLib/";
            OUT_TMP = ROOT + "temp/";
        }
        if (!StringsUtil.isEmpty(jiaguMain.argsBean.keystoreCfg)) {
            ArgsBean temp = KeyStoreUtil.readKeyStoreConfig(ROOT + jiaguMain.argsBean.keystoreCfg);
            if (temp != null) {
                jiaguMain.argsBean.storeFile = temp.storeFile;
                jiaguMain.argsBean.storePassword = temp.storePassword;
                jiaguMain.argsBean.alias = temp.alias;
                jiaguMain.argsBean.keyPassword = temp.keyPassword;
            }
        }
        jiaguMain.beginJiaGu();
        System.out.println("Total time spend: " + (System.currentTimeMillis() - startTime) + "ms");
    }

    /**
     * 将壳dex和待加固的APK进行加密后合成新的dex文件
     * <p>
     * 具体步骤:
     * 步骤一: 将加固壳中的aar中的jar转成dex文件
     * 步骤二: 将需要加固的APK解压，并将所有dex文件打包成一个zip包，方便后续进行加密处理
     * 步骤三: 对步骤二的zip包进行加密，并与壳dex合成新dex文件
     * 步骤四: 修改AndroidManifest（Application的android:name属性和新增<meta-data>）
     * 步骤五: 将步骤三生成的新dex文件替换apk中的所有dex文件
     * 步骤六: APK对齐处理
     * 步骤七: 对生成的APK进行签名
     */
    public void beginJiaGu() {
        try {
            //前奏 - 先将目录删除
            FileUtils.deleteFile(OUT_TMP);
//            步骤一: 将加固壳中的aar中的jar转成dex文件
            boolean hasShellDex = true;
            File shellDexFile = null;
            if (hasShellDex) {
                shellDexFile = copyShellDex();
            } else {
                shellDexFile = shellAar2Dex();
            }
            //步骤二: 将需要加固的APK解压，并将所有dex文件打包成一个zip包，方便后续进行加密处理
            File dexZipFile = apkUnzipAndZipDexFiles();
            if (dexZipFile == null) {
                return;
            }
            //步骤三: 对步骤二的zip包进行加密，并与壳dex合成新dex文件
            File dexFile = combine2NewDexFile(shellDexFile, dexZipFile);
            //步骤四: 修改AndroidManifest（Application的android:name属性和新增<meta-data>）
//            String outpath = modifyOriginApkManifest();
//            String outpath = modifyOriginApkManifest2();
            String outpath = modifyOriginApkManifest3();

            //步骤五: 将步骤三生成的新dex文件替换apk中的所有dex文件
            if (dexFile != null && !outpath.isEmpty()) {
                boolean ret = replaceDexFiles(outpath, dexFile.getPath(), shellDexFile.getPath());
                //步骤六: APK对齐处理
                if (ret) {
//                    String outpath = OUT_TMP + ORIGIN_APK.substring(ORIGIN_APK.lastIndexOf("/")+1);
                    File apk = zipalignApk(new File(outpath));
                    if (!StringsUtil.isEmpty(argsBean.keystoreCfg)) {
                        //步骤七: 对生成的APK进行签名
                        resignApk(apk);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 步骤一: 将 shelldex 下的 classes.dex 复制到 temp/shell/ 下
     *
     * @throws Exception 异常
     */
    private File copyShellDex() throws Exception {
        logTitle("First: copy shell_dex's classes.dex to temp/shell/");
        File dexDir = new File(OUT_TMP + "shell/classes.dex");
        if (!dexDir.exists()) {
            dexDir.getParentFile().mkdirs();//创建此文件的上级目录
        }
        File aarFile = new File(ROOT + "shellapk/jiagu_shell-release-unsigned.apk");
        File aarTemp = new File(OUT_TMP + "shell");
        ZipUtil.unZip(aarFile, aarTemp);
        return dexDir;
    }

    /**
     * 步骤一: 将加固壳中的aar中的jar转成dex文件
     *
     * @throws Exception 异常
     */
    private File shellAar2Dex() throws Exception {
        logTitle("First: convert shell_dex's jar to dex and then copy to temp/shell/");
        //步骤一: 将加固壳中的aar中的jar转成dex文件
        File aarFile = new File(ROOT + "aar/jiagu_shell-release.aar");
        File aarTemp = new File(OUT_TMP + "shell");
        ZipUtil.unZip(aarFile, aarTemp);
        File classesJar = new File(aarTemp, "classes.jar");
        File classesDex = new File(aarTemp, "classes.dex");
        // 新版本用 d8 不用 dx 了（工具在 Android Sdk/build-tools/xxxx/ 中）
        boolean useD8 = true;
        boolean ret = false;
        if (useD8) {
            File d8Zip = new File(aarTemp, "classes.zip");
            ret = ProcessUtil.exeCmd(String.format(Locale.CHINESE, "d8 --output %s %s", d8Zip.getPath(), classesJar.getPath()));
            ZipUtil.unZip(d8Zip, aarTemp);
        } else {
            ret = ProcessUtil.exeCmd(String.format(Locale.CHINESE, "dx --dex --output %s %s", classesDex.getPath(), classesJar.getPath()));
        }
        if (!ret) {
            throw new RuntimeException("convert jar to dex failed.");
        }
        return classesDex;
    }

    private ShellConfigsBean getMergedSoPath(String apkUnzipAbsolutePath) {
        // so name | so path
        ShellConfigsBean abiFileBean = new ShellConfigsBean();
        File libDir = new File(apkUnzipAbsolutePath, "lib");
        File[] abiList = libDir.listFiles();
        if (abiList == null) {
            return abiFileBean;
        }
        File[] soList;
        HashMap<String, String> temp = null;
        for (File abiDir : abiList) {
            switch (abiDir.getName()) {
                case "arm64-v8a":
                    temp = abiFileBean.arm64_v8a;
                    break;
                case "armeabi-v7a":
                    temp = abiFileBean.armeabi_v7a;
                    break;
                case "x86_64":
                    temp = abiFileBean.x86_64;
                    break;
                case "x86":
                    temp = abiFileBean.x86;
                    break;
            }
            if (temp != null) {
                soList = abiDir.listFiles();
                if (soList != null) {
                    for (File soFile : soList) {
                        temp.put(soFile.getName(),
                                soFile.getAbsolutePath()
                                        .replace(apkUnzipAbsolutePath + File.separator, "")
                                        .replace("\\", "/"));
                    }
                }
                for (String shellSoName : shellNativeLibraryNames) {
                    temp.put(shellSoName, "lib/" + abiDir.getName() + "/" + shellSoName);
                }
            }
        }
        return abiFileBean;
    }

    /**
     * 步骤二: 将需要加固的APK解压，并将所有dex文件打包成一个zip包，方便后续进行加密处理
     *
     * @throws Exception 异常
     */
    private File apkUnzipAndZipDexFiles() {
        logTitle("Second: unzip the apk file and zip all its dex files to zip file.");
        //下面加密码APK中所有的dex文件
        File apkFile = new File(ORIGIN_APK);
        File apkTemp = new File(OUT_TMP + "unzip/");
        try {
            //首先把apk解压出来
            ZipUtil.unZip(apkFile, apkTemp);
            File assetsDir = new File(apkTemp, "assets");
            // 加密 assets 文件夹
            if (argsBean.assets) {
                if (assetsDir.exists()) {
                    File assetZipFile = new File(OUT_TMP, "assets.zip");
                    Zip4jUtil.zipFiles(assetsDir.listFiles(), assetZipFile, "assets");
                    //
                    ZipFile zipFile = new ZipFile(apkFile);
                    List<String> assetsPathList = new ArrayList<>();
                    zipFile.getFileHeaders().stream().filter(fileHeader -> fileHeader != null && fileHeader.getFileName().startsWith("assets/")).forEach(fileHeader -> {
                        assetsPathList.add(fileHeader.getFileName());
                    });
                    zipFile.removeFiles(assetsPathList);
                    zipFile.close();
                    // 解决 Android 6 上 addAssetPath 失败的问题。
                    Zip4jUtil.addFile2Zip(assetZipFile.getAbsolutePath(), new File(apkTemp, "AndroidManifest.xml").getAbsolutePath(), null);
                    Zip4jUtil.addFile2Zip(apkFile.getAbsolutePath(), assetZipFile.getAbsolutePath(), "assets");
                }
            }
            //
            shellConfigsBean = getMergedSoPath(apkTemp.getAbsolutePath());
            //
            if (argsBean.encryptNative) {
                // TODO: 加密 so
            }
            //其次获取解压目录中的dex文件
            File[] dexFiles = apkTemp.listFiles((file, s) -> s.endsWith(".dex"));
            if (dexFiles == null) return null;
            //三: 将所有的dex文件压缩为AppDex.zip文件
            File outTmpFile = new File(OUT_TMP);
            File outputFile = new File(outTmpFile, "AppDex.zip");
            //创建目录
            if (!outTmpFile.exists()) {
                outTmpFile.mkdirs();
            }
            if (outputFile.exists()) {
                outputFile.delete();
            }

            if (Configs.mergeDexFiles) {
                StringBuilder cmd = new StringBuilder();
                cmd.append("d8");
                for (File file : dexFiles) {
                    cmd.append(" ").append(file.getAbsolutePath());
                }
                cmd.append(" --output ").append(outputFile.getAbsolutePath());
                ProcessUtil.exeCmd(cmd.toString());
                System.out.println("convert dex files to one.");
            } else {
                Zip4jUtil.zipFiles(dexFiles, outputFile);
            }
            System.out.println("completed and zip file path:" + outputFile.getPath());
            FileUtils.deleteFile(apkTemp.getPath());
            return outputFile;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 步骤三: 对步骤二的zip包进行加密，并与壳dex合成新dex文件
     *
     * @param shellDexFile       壳dex文件
     * @param originalDexZipFile 原包中dex压缩包
     */
    private File combine2NewDexFile(File shellDexFile, File originalDexZipFile) {
        logTitle("Third: encrypt the zip file and create a new dex file.");
        try {
            // 以二进制形式读出 zip 数据
            byte[] data = readFileBytes(originalDexZipFile);
            System.out.println("The data size before encryption is: " + data.length);
            // 进行加密操作
            byte[] payloadArray = AESUtil.encrypt(data);
            // 以二进制形式读出 shell_dex
            byte[] unShellDexArray = readFileBytes(shellDexFile);
            int payloadLen = payloadArray.length;
            int unShellDexLen = unShellDexArray.length;
            //多出 4 字节是存放长度的。
            int totalLen = payloadLen + unShellDexLen + 4;
            // 申请了新的长度
            byte[] newDexData = new byte[totalLen];
            //添加壳代码
            System.arraycopy(unShellDexArray, 0, newDexData, 0, unShellDexLen);
            //添加加密后的数据
            System.arraycopy(payloadArray, 0, newDexData, unShellDexLen, payloadLen);
            //添加源数据的长度
            System.arraycopy(intToByte(payloadLen), 0, newDexData, totalLen - 4, 4);
            //修改 DEX file size 文件头
            fixFileSizeHeader(newDexData);
            //修改 DEX SHA1 文件头
            fixSHA1Header(newDexData);
            //修改 DEX CheckSum 文件头
            fixCheckSumHeader(newDexData);

            String str = OUT_TMP + "classes.dex";
            File file = new File(str);
            if (!file.exists()) {
                file.createNewFile();
            }

            //输出成新的dex文件
            FileOutputStream localFileOutputStream = new FileOutputStream(str);
            localFileOutputStream.write(newDexData);
            localFileOutputStream.flush();
            localFileOutputStream.close();
            System.out.println("Has created new dex file to " + str);
            //删除dex的zip包
//            FileUtils.deleteFile(originalDexZipFile.getAbsolutePath());
            return file;
        } catch (Exception e) {
            throw new RuntimeException(e.getLocalizedMessage());
        }
    }

    /**
     * 步骤四: 修改AndroidManifest（Application的android:name属性和新增<meta-data>）
     * 反编译: 采用apktool+xml解析方式对AndroidManifest.xml进行修改
     * 优点: 不用依赖其他jar包，
     * 缺点: 非常耗时，在21000ms左右(不包含删除反编译的临时文件的时间)
     */
    private String modifyOriginApkManifest() throws Exception {
        String apkPath = ORIGIN_APK;
        String outputPath = OUT_TMP + "apk/";
        logTitle("步骤四: 反编译后修改AndroidManifest（Application的android:name属性和新增<meta-data>）");
        String path = "";
        long start = System.currentTimeMillis();
        //1: 执行命令进行反编译原apk
        System.out.println("开始反编译原apk ......");
        boolean useApkToolJar = true;
        String apktoolCmd = "apktool";
        if (useApkToolJar) {
            apktoolCmd = "java -jar ./libs/apktool_2.9.3.jar";
        }
        boolean ret = ProcessUtil.exeCmd(apktoolCmd + " d -o " + outputPath + " " + apkPath);
        if (ret) {
            // 2. 修改AndroidManifest.xml，使用壳的Application替换原Application,并将原Application名称配置在meta-data中
            modifyAndroidManifest(new File(outputPath, "AndroidManifest.xml"));
            // 3. 重新编译成apk,仍以原来名称命名
            System.out.println("开始回编译 apk ......");
            String apk = OUT_TMP + apkPath.substring(apkPath.lastIndexOf("/") + 1);
            ret = ProcessUtil.exeCmd(String.format(Locale.CHINESE, apktoolCmd + " b -o %s %s", apk, outputPath));
            if (ret) {
                path = apk;
            }
            System.out.println("=== modifyOriginApkManifest ==== " + (System.currentTimeMillis() - start) + "ms");
        }
        //删除解压的目录
        FileUtils.deleteFile(outputPath);

        return path;
    }

    /**
     * 步骤四: 修改AndroidManifest（Application的android:name属性和新增<meta-data>）
     * <p>
     * 非反编译: 采用AAPT和AXMLEditor.jar组合方式对AndroidManifest.xml进行修改
     * 优点: 高效 耗时 在520ms左右
     * 缺点: 多依赖AXMLEditor.jar包（https://github.com/fourbrother/AXMLEditor）
     */
    private String modifyOriginApkManifest2() throws Exception {
        String apkPath = ORIGIN_APK;
        logTitle("步骤四: 直接修改AndroidManifest（Application的android:name属性和新增<meta-data>）");
        String outApk = "";
        long start = System.currentTimeMillis();
        File outAmFile = new File(OUT_TMP, "am.txt");
        // 1.执行命令: aapt dump xmltree app_preview3.apk AndroidManifest.xml > out.txt ，得到导出的配置文件
        ProcessUtil.exeCmd("aapt dump xmltree " + apkPath + " AndroidManifest.xml >" + outAmFile.getPath());
        // 2.读取输出的文件内容，获取到Application name
        String clazzName = FileUtils.getAppApplicationName(outAmFile);
        int minSdkVersion = FileUtils.getAppMinSdk(outAmFile);
        System.out.printf("minSdkVersion:" + minSdkVersion);
        if (minSdkVersion > 0) {
            minSdk = minSdkVersion + "";
        }
        System.out.println("application-name ->" + clazzName);
        FileUtils.deleteFile(outAmFile.getPath());
        // 3.将AndroidManifest.xml从apk中提取出来
        Zip4jUtil.extractFile(apkPath, "AndroidManifest.xml", OUT_TMP);
        File manifestFile = new File(OUT_TMP, "AndroidManifest.xml");
        if (manifestFile.exists()) {
            // 4.运行AXMLEditor修改AndroidManifest.xml文件
            String shellClazzName = Configs.shellProxyApplicationClassName;
            //先删除application 的android:name属性
            String cmd = String.format(Locale.CHINESE, "java -jar %slibs/AXMLEditor.jar -attr -r application package name %s %s", ROOT, manifestFile.getPath(), manifestFile.getPath());
            ProcessUtil.exeCmd(cmd);
            // 修改命令: java -jar AXMLEditor.jar -attr -m [标签名] [标签唯一标识] [属性名] [属性值] [输入xml] [输出xml]
            // 添加application 的android:name属性
            cmd = String.format(Locale.CHINESE, "java -jar %slibs/AXMLEditor.jar -attr -m application package name %s %s %s", ROOT, shellClazzName, manifestFile.getPath(), manifestFile.getPath());
            ProcessUtil.exeCmd(cmd);
            // 先将<meta-data>属性写入到一个xml文件中，然后添加<meta-data>属性
            String metaXml = OUT_TMP + "meta.xml";
            clazzName = clazzName == null || clazzName.isEmpty() ? "android.app.Application" : clazzName;
            FileUtils.writeFile("<meta-data android:name=\"APPLICATION_CLASS_NAME\" android:value=\"" + clazzName + "\"/>", metaXml);
            // 插入标签: java -jar AXMLEditor.jar -tag -i [需要插入标签内容的xml文件] [输入xml] [输出xml]
            cmd = String.format(Locale.CHINESE, "java -jar %slibs/AXMLEditor.jar -tag -i %s %s %s", ROOT, metaXml, manifestFile.getPath(), manifestFile.getPath());
            ProcessUtil.exeCmd(cmd);
            FileUtils.deleteFile(metaXml);
            // 5.将修改完成的AndroidManifest.xml重新添加到apk中
            outApk = OUT_TMP + apkPath.substring(apkPath.lastIndexOf("/") + 1);
            FileUtils.copyFile(new File(ORIGIN_APK), new File(outApk));
            ProcessUtil.exeCmd("aapt r " + outApk + " AndroidManifest.xml");
            Zip4jUtil.addFile2Zip(outApk, manifestFile.getAbsolutePath(), "");
            FileUtils.deleteFile(manifestFile.getAbsolutePath());
            System.out.println("=== modifyOriginApkManifest2 ==== " + (System.currentTimeMillis() - start) + "ms");
        } else {
            throw new FileNotFoundException("cannot find file: AndroidManifest.xml in " + OUT_TMP);
        }
        return outApk;
    }

    /**
     * 步骤四: 修改AndroidManifest（Application的android:name属性和新增<meta-data>）
     * <p>
     * 非反编译: 采用 AAPT和 APKEditor.jar组合方式对 AndroidManifest.xml 进行修改
     * 优点: 高效 耗时 在520ms左右
     * 缺点: 多依赖 APKEditor.jar 包
     */
    private String modifyOriginApkManifest3() throws Exception {
        String apkPath = ORIGIN_APK;
        logTitle("Fourth: Modify Application's name in AndroidManifest and add a new tag 'meta-data')");
        String outApk = "";
        long start = System.currentTimeMillis();
        // 0.将 AndroidManifest.xml从 apk中提取出来
        Zip4jUtil.extractFile(apkPath, "AndroidManifest.xml", OUT_TMP);
        File manifestFile = new File(OUT_TMP, "AndroidManifest.xml");
        if (manifestFile.exists()) {
            // 1.修改 AndroidManifest.xml
            AndroidManifestBlock androidManifestBlock = AndroidManifestBlock.load(manifestFile);
            minSdk = androidManifestBlock.getMinSdkVersion() + "";
            ResXmlElement metaDataElement = androidManifestBlock.getApplicationElement().createChildElement("meta-data");
            ResXmlAttribute nameAttr = metaDataElement.getOrCreateAndroidAttribute(AndroidManifest.NAME_name, AndroidManifest.ID_name);
            nameAttr.setValueAsString("APPLICATION_CLASS_NAME");
            ResXmlAttribute valueAttr = metaDataElement.getOrCreateAndroidAttribute(AndroidManifest.NAME_value, AndroidManifest.ID_value);
            valueAttr.setValueAsString(androidManifestBlock.getApplicationClassName());
            androidManifestBlock.setApplicationClassName(Configs.shellProxyApplicationClassName);
            androidManifestBlock.refresh();
            androidManifestBlock.writeBytes(manifestFile);
            // 2.将修改完成的AndroidManifest.xml重新添加到apk中
            outApk = OUT_TMP + apkPath.substring(apkPath.lastIndexOf("/") + 1);
            FileUtils.copyFile(new File(ORIGIN_APK), new File(outApk));
            // ProcessUtil.exeCmd("aapt r " + outApk + " AndroidManifest.xml");
            // FIXME: 改为用压缩工具删除 AndroidManifest.xml 文件后，好像在 Android 6 上会 crash
            Zip4jUtil.deleteFile("AndroidManifest.xml", outApk);
            Zip4jUtil.addFile2Zip(outApk, manifestFile.getAbsolutePath(), "");
            FileUtils.deleteFile(manifestFile.getAbsolutePath());
            System.out.println("=== modifyOriginApkManifest3 ==== " + (System.currentTimeMillis() - start) + "ms");
        } else {
            throw new FileNotFoundException("cannot find file: AndroidManifest.xml in " + OUT_TMP);
        }
        return outApk;
    }

    /**
     * 步骤五: 将步骤三生成的新dex文件替换apk中的所有dex文件
     * 替换zip中的所有dex文件
     *
     * @param zipPath    zip文件路径
     * @param newDexPath 新的dex文件路径
     */
    private boolean replaceDexFiles(String zipPath, String newDexPath, String shellDexFile) {
        logTitle("Fifth: replace the apk's dex files by the new dex file.");
        try {
            Zip4jUtil.deleteDexFromZip(zipPath);
            // 添加一些辅助文件
            File newDexParentDir = new File(newDexPath).getParentFile();
            // 动态库信息
            String shellConfigsFilePath = newDexParentDir + File.separator + "shell_configs.bin";
            shellConfigsBean.canResign = argsBean.canResign;
            shellConfigsBean.debuggable = argsBean.debuggable;
            shellConfigsBean.sha1 = apkSha1;
            shellConfigsBean.assets = argsBean.assets;
            shellConfigsBean.encryptNative = argsBean.encryptNative;
            FileUtils.writeFile(shellConfigsBean.toJsonString(), shellConfigsFilePath);
            Zip4jUtil.addFile2Zip(zipPath, shellConfigsFilePath, "assets/apk_protect");
            FileUtils.deleteFile(shellConfigsFilePath);
            // 添加新的 dex
            Zip4jUtil.addFile2Zip(zipPath, newDexPath, "assets/apk_protect");
            Zip4jUtil.addFile2Zip(zipPath, shellDexFile, "");
            //将加固的 so文件添加到 apk 的 lib 中
            ZipFile zipFile = new ZipFile(zipPath);
            final boolean[] boolArm = new boolean[4];
            zipFile.getFileHeaders().stream().filter(fileHeader -> fileHeader != null && fileHeader.getFileName().endsWith(".so")).forEach(fileHeader -> {
                String name = fileHeader.getFileName();
                if (name.contains("armeabi-v7a")) {
                    boolArm[0] = true;
                } else if (name.contains("arm64-v8a")) {
                    boolArm[1] = true;
                } else if (name.contains("x86_64")) {
                    boolArm[3] = true;
                } else if (name.contains("x86")) {
                    boolArm[2] = true;
                }
            });
            zipFile.close();
            for (String name : shellNativeLibraryNames) {
                if (!boolArm[0] && !boolArm[1] && !boolArm[2] && !boolArm[3]) {
                    Zip4jUtil.addFile2Zip(zipPath, OUT_TMP + "shell/lib/armeabi-v7a/" + name, "lib/armeabi-v7a/");
                } else {
                    if (boolArm[0]) {
                        Zip4jUtil.addFile2Zip(zipPath, OUT_TMP + "shell/lib/armeabi-v7a/" + name, "lib/armeabi-v7a/");
                    }
                    if (boolArm[1]) {
                        Zip4jUtil.addFile2Zip(zipPath, OUT_TMP + "shell/lib/arm64-v8a/" + name, "lib/arm64-v8a/");
                    }
                    if (boolArm[2]) {
                        Zip4jUtil.addFile2Zip(zipPath, OUT_TMP + "shell/lib/x86/" + name, "lib/x86/");
                    }
                    if (boolArm[3]) {
                        Zip4jUtil.addFile2Zip(zipPath, OUT_TMP + "shell/lib/x86_64/" + name, "lib/x86_64/");
                    }
                }
            }
            FileUtils.deleteFile(OUT_TMP + "shell");
            FileUtils.deleteFile(newDexPath);
            System.out.println("completed replace all dex files.");
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e.getLocalizedMessage());
        }
    }

    private boolean matchCpuArm(Stream<FileHeader> stream, String cpuArm) {
        boolean match = stream.anyMatch(new Predicate<FileHeader>() {
            @Override
            public boolean test(FileHeader fileHeader) {
                return fileHeader.getFileName().contains(cpuArm);
            }
        });
        stream.close();
        return match;
    }

    /**
     * 步骤六: 将已经签名的APK进行对齐处理
     *
     * @param unAlignedApk 需要对齐的apk文件对象
     * @throws Exception 异常
     */
    private File zipalignApk(File unAlignedApk) throws Exception {
        logTitle("Sixth: The APK is aligned again");
//        File protectedApk = new File(unAlignedApk.getParent(), unAlignedApk.getName().replace(".apk", "_protected.apk"));
//        String cmd = String.format(Locale.CHINESE, "java -jar ./libs/APKEditor-1.4.1.jar p -i %s -o %s", unAlignedApk.getAbsolutePath(), protectedApk.getAbsolutePath());
//        ProcessUtil.executeCommand(cmd);
        //步骤四: 重新对APK进行对齐处理
        File alignedApk = new File(unAlignedApk.getParent(), unAlignedApk.getName().replace(".apk", "_align.apk"));
        boolean ret = ProcessUtil.exeCmd("zipalign -v -p 4 " + unAlignedApk.getPath() + " " + alignedApk.getPath());
        if (ret) {
            System.out.println("completed realign.");
        }
        //删除未对齐的包
        FileUtils.deleteFile(unAlignedApk.getPath());

        return alignedApk;
    }

    /**
     * 步骤七: 对生成的APK进行签名
     *
     * @param unSignedApk 需要签名的文件对象
     * @return 返回签名后的文件对象
     * @throws Exception 异常
     */
    private File resignApk(File unSignedApk) throws Exception {
        logTitle("Seventh: Sign the generated APK");
        //步骤五: 对APK进行签名
        File signedApk = new File(ROOT + "out", unSignedApk.getName().replace(".apk", "_signed.apk"));
        //创建保存加固后apk目录
        if (!signedApk.getParentFile().exists()) {
            signedApk.getParentFile().mkdirs();
        }

        String signerCmd = String.format("apksigner sign --ks %s --ks-key-alias %s --min-sdk-version %s --ks-pass pass:%s --key-pass pass:%s --out %s %s",
                argsBean.storeFile, argsBean.alias, minSdk, argsBean.storePassword, argsBean.keyPassword, signedApk.getPath(), unSignedApk.getPath());
        boolean ret = ProcessUtil.exeCmd(signerCmd);
        System.out.println("completed and the apk path is: " + signedApk.getPath());
        //删除未对齐的包
        FileUtils.deleteFile(unSignedApk.getPath());
        return signedApk;
    }

    private String minSdk = "21";

    /**
     * 读取原包的AndroidManifest文件并修改
     *
     * @param xmlFile 原AndroidManifest文件对象
     */
    private void modifyAndroidManifest(File xmlFile) {
        if (xmlFile == null) {
            System.out.println("请设置AndroidManifest.xml文件");
            return;
        }
        if (!xmlFile.exists()) {
            System.out.println("指定的AndroidManifest.xml文件不存在");
            return;
        }
        System.out.println("开始修改AndroidManifest.xml......");
        String shellApplicationName = Configs.shellProxyApplicationClassName;
        String metaDataName = "APPLICATION_CLASS_NAME";
        String attrName = "android:name";

        //采用Dom读取AndroidManifest.xml文件
        try {
            //1.实例化Dom工厂
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            //2.构建一个builder
            DocumentBuilder builder = factory.newDocumentBuilder();
            //3.通过builder解析xml文件
            Document document = builder.parse(xmlFile);
            // 获取 minSDK
            NodeList usesSDK = document.getElementsByTagName("uses-sdk");
            if (usesSDK != null) {
                Node usesSDKNode = usesSDK.item(0);
                if (usesSDKNode != null) {
                    NamedNodeMap attrMap = usesSDKNode.getAttributes();
                    if (attrMap != null) {
                        Node node = attrMap.getNamedItem("android:minSdkVersion");
                        if (node != null) {
                            minSdk = node.getNodeValue();
                            System.out.println("当前 app minSdk: " + minSdk);
                        }
                    }
                }
            }
            // 添加 application 相关
            NodeList nl = document.getElementsByTagName("application");
            if (nl != null) {
                Node app = nl.item(0);
                //获取原APK中application
                String applicationName = "android.app.Application";
                NamedNodeMap attrMap = app.getAttributes();
                //有属性时
                Node node = app.getAttributes().getNamedItem(attrName);
                //默认为系统的Application
                if (node != null) {
                    applicationName = node.getNodeValue();
                    node.setNodeValue(shellApplicationName);
                } else {//不存在该属性时，则创建一个
                    Attr attr = document.createAttribute(attrName);
                    attr.setValue(shellApplicationName);
                    attrMap.setNamedItem(attr);
                }

                //添加<meta-data>数据，记录原APK的application
                Element metaData = document.createElement("meta-data");
                metaData.setAttribute("android:name", metaDataName);
                metaData.setAttribute("android:value", applicationName);
                app.appendChild(metaData);


                //重新写入文件xml文件
                TransformerFactory outFactory = TransformerFactory.newInstance();
                Transformer transformer = outFactory.newTransformer();
                Source xmlSource = new DOMSource(document);
                Result outResult = new StreamResult(xmlFile);
                transformer.transform(xmlSource, outResult);
                System.out.println("已完成修改AndroidManifest文件======");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //直接返回数据，读者可以添加自己加密方法
    private byte[] encrpt(byte[] srcdata) {
        for (int i = 0; i < srcdata.length; i++) {
            srcdata[i] = (byte) (0xFF ^ srcdata[i]);
        }
        return srcdata;
    }

    /**
     * 修改dex头，CheckSum 校验码
     *
     * @param dexBytes
     */
    private void fixCheckSumHeader(byte[] dexBytes) {
        Adler32 adler = new Adler32();
        adler.update(dexBytes, 12, dexBytes.length - 12);//从12到文件末尾计算校验码
        long value = adler.getValue();
        int va = (int) value;
        byte[] newcs = intToByte(va);
        //高位在前，低位在前掉个个
        byte[] recs = new byte[4];
        for (int i = 0; i < 4; i++) {
            recs[i] = newcs[newcs.length - 1 - i];
//            System.out.println(Integer.toHexString(newcs[i]));
        }
        System.arraycopy(recs, 0, dexBytes, 8, 4);//效验码赋值（8-11）
//        System.out.println(Long.toHexString(value));
//        System.out.println();
    }


    /**
     * int 转byte[]
     *
     * @param number
     * @return
     */
    public byte[] intToByte(int number) {
        byte[] b = new byte[4];
        for (int i = 3; i >= 0; i--) {
            b[i] = (byte) (number % 256);
            number >>= 8;
        }
        return b;
    }

    /**
     * 修改dex头 sha1值
     *
     * @param dexBytes
     * @throws NoSuchAlgorithmException
     */
    private void fixSHA1Header(byte[] dexBytes)
            throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(dexBytes, 32, dexBytes.length - 32);//从32为到结束计算sha--1
        byte[] newdt = md.digest();
        System.arraycopy(newdt, 0, dexBytes, 12, 20);//修改sha-1值（12-31）
        //输出sha-1值，可有可无
        String hexstr = "";
        for (int i = 0; i < newdt.length; i++) {
            hexstr += Integer.toString((newdt[i] & 0xff) + 0x100, 16)
                    .substring(1);
        }
        //System.out.println(hexstr);
    }

    /**
     * 修改dex头 file_size值
     *
     * @param dexBytes
     */
    private void fixFileSizeHeader(byte[] dexBytes) {
        //新文件长度
        byte[] newfs = intToByte(dexBytes.length);
        System.out.println("fixFileSizeHeader ===== size : " + dexBytes.length);
        byte[] refs = new byte[4];
        //高位在前，低位在前掉个个
        for (int i = 0; i < 4; i++) {
            refs[i] = newfs[newfs.length - 1 - i];
            //System.out.println(Integer.toHexString(newfs[i]));
        }
        System.arraycopy(refs, 0, dexBytes, 32, 4);//修改（32-35）
    }


    /**
     * 以二进制读出文件内容
     *
     * @param file
     * @return
     * @throws IOException
     */
    private byte[] readFileBytes(File file) throws IOException {
        byte[] arrayOfByte = new byte[1024];
        ByteArrayOutputStream localByteArrayOutputStream = new ByteArrayOutputStream();
        FileInputStream fis = new FileInputStream(file);
        while (true) {
            int i = fis.read(arrayOfByte);
            if (i != -1) {
                localByteArrayOutputStream.write(arrayOfByte, 0, i);
            } else {
                return localByteArrayOutputStream.toByteArray();
            }
        }
    }

    private void logTitle(String title) {
        System.out.println("==================== " + title + " ====================");
    }

    private static String getApkSHA1(String apkPath) {
        String[] apkSHA1 = new String[1];
        try {
            ProcessUtil.exeCmd("keytool -printcert -jarfile " + apkPath, lineStr -> {
                if (null != lineStr && lineStr.contains("SHA1")) {
                    apkSHA1[0] = lineStr.trim().substring(6);
                    return true;
                }
                return false;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return apkSHA1[0];
    }
}
