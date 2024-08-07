package com.zhh.jiagu.shell.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import com.zhh.jiagu.shell.constant.Configs;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dalvik.system.BaseDexClassLoader;

public class Utils {

    public static byte[] readAssetsClassesDex(Context context) {
        return readAssetsClassesDex(context, "apk_protect/classes.dex");
    }

    public static byte[] readAssetsClassesDex(Context context, String path) {
        LogUtil.info("begin readAssetsClassesDex");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            InputStream dexFis = context.getAssets().open(path);
            byte[] arrayOfByte = new byte[1024];
            int readSize = -1;
            while ((readSize = dexFis.read(arrayOfByte)) != -1) {
                byteArrayOutputStream.write(arrayOfByte, 0, readSize);
            }
            dexFis.close();
            LogUtil.info("dex size:" + byteArrayOutputStream.size());
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            LogUtil.error(e.getLocalizedMessage());
        } finally {
            try {
                byteArrayOutputStream.close();
            } catch (IOException e) {
                LogUtil.error(e.getLocalizedMessage());
            }
        }
        return null;
    }

    /**
     * 从apk包里面获取dex文件内容（byte）
     *
     * @return
     * @throws IOException
     */
    public static byte[] readDexFileFromApk(String apkPath) throws IOException {
        LogUtil.info("apkPath:" + apkPath);
        ByteArrayOutputStream dexByteArrayOutputStream = new ByteArrayOutputStream();
        //获取当前zip进行解压
        ZipInputStream zipInputStream = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(apkPath)));
        while (true) {
            ZipEntry entry = zipInputStream.getNextEntry();
            if (entry == null) {
                zipInputStream.close();
                break;
            }
            if (entry.getName().equals("classes.dex")) {
                byte[] arrayOfByte = new byte[1024];
                while (true) {
                    int i = zipInputStream.read(arrayOfByte);
                    if (i == -1)
                        break;
                    dexByteArrayOutputStream.write(arrayOfByte, 0, i);
                }
            }
            zipInputStream.closeEntry();
        }
        zipInputStream.close();
        return dexByteArrayOutputStream.toByteArray();
    }

    public static String removeNativeLibraries(String apkPath, String abi, String newNativeLibraryPath) {
        try {
            //获取当前zip进行解压
            ZipInputStream zipInputStream = new ZipInputStream(
                    new BufferedInputStream(new FileInputStream(apkPath)));
            while (true) {
                ZipEntry entry = zipInputStream.getNextEntry();
                if (entry == null) {
                    zipInputStream.close();
                    break;
                }
                if (entry.getName().startsWith("lib/" + abi + "/") && entry.getName().endsWith(".so")) {
                    File file = new File(newNativeLibraryPath, entry.getName().replace("lib/" + abi + "/", ""));
                    if (!file.getParentFile().exists()) {
                        file.getParentFile().mkdirs();
                    }
                    if (file.createNewFile()) {
                        FileOutputStream fileOutputStream = new FileOutputStream(file);
                        LogUtil.info("copy native library:" + file.getAbsolutePath());
                        byte[] arrayOfByte = new byte[1024];
                        while (true) {
                            int i = zipInputStream.read(arrayOfByte);
                            if (i == -1)
                                break;
                            fileOutputStream.write(arrayOfByte, 0, i);
                        }
                        fileOutputStream.close();
                    }
                }
                zipInputStream.closeEntry();
            }
            zipInputStream.close();
        } catch (Exception e) {
            LogUtil.error(e.getLocalizedMessage());
        }

        return newNativeLibraryPath + File.separator + abi;
    }

    public static boolean changeDefaultNativeLibraryPath(Object classLoader, String oldNativeLibraryPath, String newNativeLibraryPath) {
        Object dexPathList = RefInvoke.getField(BaseDexClassLoader.class.getName(), classLoader, "pathList");
        ArrayList<File> nativeLibraryDirectories = (ArrayList<File>) RefInvoke.getField("dalvik.system.DexPathList", dexPathList, "nativeLibraryDirectories");
        if (nativeLibraryDirectories != null) {
            for (int i = 0; i < nativeLibraryDirectories.size(); i++) {
                if (nativeLibraryDirectories.get(i).getAbsolutePath().equals(oldNativeLibraryPath)) {
                    nativeLibraryDirectories.set(i, new File(newNativeLibraryPath));
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 释放被加壳的AppDex.zip，so文件
     *
     * @param apkdata classes.dex数据
     * @throws IOException 异常
     */
    public static ByteBuffer[] releaseAppDexFile(byte[] apkdata, String apkFileName) throws Exception {
        int length = apkdata.length;

        //取被加壳apk的长度   这里的长度取值，对应加壳时长度的赋值都可以做些简化
        byte[] dexlen = new byte[4];
        System.arraycopy(apkdata, length - 4, dexlen, 0, 4);
        ByteArrayInputStream bais = new ByteArrayInputStream(dexlen);
        DataInputStream in = new DataInputStream(bais);
        int readInt = in.readInt();
        LogUtil.info("============ 读取原Dex压缩文件大小 ======" + readInt);
        byte[] newdex = new byte[readInt];
        //把被加壳apk内容拷贝到newdex中
        System.arraycopy(apkdata, length - 4 - readInt, newdex, 0, readInt);


        LogUtil.info("============ 开始对加密dex进行解密======" + newdex.length);
        //对zip包进行解密
        newdex = AESUtil.decrypt(newdex);

        LogUtil.info("============ 解密后的大小为======" + newdex.length);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                (Configs.TestOpenMemory && Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1)) {
            LogUtil.info("Android 10 及以上");
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(newdex);
            ZipInputStream zipInputStream = new ZipInputStream(byteArrayInputStream);
            ZipEntry zipEntry = null;
            byte[] bytesTemp = new byte[8192];
            int bytesTempLength = -1;
            List<ByteBuffer> byteBufferList = new ArrayList<>();
            ByteArrayOutputStream byteArrayOutputStream;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                // FIXME:这里没有校验 zipEntry ，正常情况 zip 中只有 dex
                byteArrayOutputStream = new ByteArrayOutputStream();
                while ((bytesTempLength = zipInputStream.read(bytesTemp)) != -1) {
                    byteArrayOutputStream.write(bytesTemp, 0, bytesTempLength);
                }
                byteBufferList.add(ByteBuffer.wrap(byteArrayOutputStream.toByteArray()));
                byteArrayOutputStream.close();
                zipInputStream.closeEntry();
            }
            zipInputStream.close();
            byteArrayInputStream.close();
            LogUtil.info("find classes.dex number:" + byteBufferList.size());
            return byteBufferList.toArray(new ByteBuffer[0]);
        }

        // 写入 AppDex.zip文件
        File file = new File(apkFileName);
        file.createNewFile();
        try {
            FileOutputStream localFileOutputStream = new FileOutputStream(file);
            localFileOutputStream.write(newdex);
            localFileOutputStream.close();
            LogUtil.info("Dex 准备完毕");
        } catch (IOException localIOException) {
            throw new RuntimeException(localIOException);
        }
        //LogUtil.info("============ 开始对压缩包进行解压得到dex文件======");
        // todo:由于仅加密的是源dex文件，故这里不需要检查so文件
        return null;
    }

    public static String getMd5(File file) {
        if (!file.exists()) {
            return "";
        }
        String value = null;
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            MappedByteBuffer byteBuffer = in.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length());


            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(byteBuffer);
            BigInteger bi = new BigInteger(1, md5.digest());
            value = bi.toString(16);
            // 防止文件的md5值以0开头
            if (value.length() == 31) {
                value = "0" + value;
            }
        } catch (Exception e) {
            LogUtil.error(e.getLocalizedMessage());
        } finally {
            try {
                in.close();
            } catch (Exception e) {
                LogUtil.error(e.getLocalizedMessage());
            }
        }
        return value;
    }

    public static String encryptionMD5(byte[] byteStr) {
        MessageDigest messageDigest;
        StringBuilder md5SB = new StringBuilder();
        try {
            messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.reset();
            messageDigest.update(byteStr);
            byte[] byteArray = messageDigest.digest();
            for (byte aByteArray : byteArray) {
                if (Integer.toHexString(0xFF & aByteArray).length() == 1) {
                    md5SB.append("0").append(Integer.toHexString(0xFF & aByteArray));
                } else {
                    md5SB.append(Integer.toHexString(0xFF & aByteArray));
                }
            }
        } catch (Exception e) {
            LogUtil.error(e.getLocalizedMessage());
        }
        return md5SB.toString();
    }

    public static String encryption(byte[] byteStr, String algorithm) {
        MessageDigest messageDigest;
        StringBuilder md5SB = new StringBuilder();
        try {
            messageDigest = MessageDigest.getInstance(algorithm);
            messageDigest.reset();
            messageDigest.update(byteStr);
            byte[] byteArray = messageDigest.digest();
            for (byte aByteArray : byteArray) {
                if (Integer.toHexString(0xFF & aByteArray).length() == 1) {
                    md5SB.append("0").append(Integer.toHexString(0xFF & aByteArray));
                } else {
                    md5SB.append(Integer.toHexString(0xFF & aByteArray));
                }
            }
        } catch (Exception e) {
            LogUtil.error(e.getLocalizedMessage());
        }
        return md5SB.toString();
    }

    /**
     * 获取包签名 MD5
     */
    public static String getPackageSignSHA1(Context context) {
        String signStr = "-1";
        if (context != null) {
            // 获取包管理器
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo;
            // 获取当前要获取 SHA1 值的包名，也可以用其他的包名，但需要注意，
            // 在用其他包名的前提是，此方法传递的参数 Context 应该是对应包的上下文。
            String packageName = context.getPackageName();
            // 签名信息
            Signature[] signatures = null;
            try {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                    SigningInfo signingInfo = packageInfo.signingInfo;
                    signatures = signingInfo.getApkContentsSigners();
                } else {
                    //获得包的所有内容信息类
                    packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                    signatures = packageInfo.signatures;
                }
            } catch (Exception e) {
                LogUtil.error(e.getLocalizedMessage());
            }

            if (null != signatures && signatures.length > 0) {
                Signature sign = signatures[0];
                signStr = encryption(sign.toByteArray(), "SHA-1").toUpperCase();
            }
        }
        return signStr;
    }

}
