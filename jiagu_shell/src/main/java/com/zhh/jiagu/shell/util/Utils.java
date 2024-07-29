package com.zhh.jiagu.shell.util;

import android.content.Context;

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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dalvik.system.BaseDexClassLoader;

public class Utils {

    public static byte[] readAssetsClassesDex(Context context) {
        LogUtil.info("begin readAssetsClassesDex");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            InputStream dexFis = (InputStream) context.getAssets().open("apk_protect/classes.dex");
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
        LogUtil.info("从classes.dex解析出加密的原包的dex数据");
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
                if (entry.getName().startsWith("lib/" + abi) && entry.getName().endsWith(".so")) {
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
    public static void releaseAppDexFile(byte[] apkdata, String apkFileName) throws Exception {
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

        //写入AppDex.zip文件
        File file = new File(apkFileName);
        try {
            FileOutputStream localFileOutputStream = new FileOutputStream(file);
            localFileOutputStream.write(newdex);
            localFileOutputStream.close();
        } catch (IOException localIOException) {
            throw new RuntimeException(localIOException);
        }
        //LogUtil.info("============ 开始对压缩包进行解压得到dex文件======");
        // todo:由于仅加密的是源dex文件，故这里不需要检查so文件

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
}
