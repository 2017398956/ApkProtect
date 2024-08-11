package com.zhh.jiagu.shell;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;

import com.zhh.jiagu.shell.dex.LoadDexUtil;
import com.zhh.jiagu.shell.util.AESUtil;
import com.zhh.jiagu.shell.util.LogUtil;
import com.zhh.jiagu.shell.util.Utils;

import java.io.File;

public class StubApplication extends Application {

    private static final String APP_KEY = "APPLICATION_CLASS_NAME";

    private Application app;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // me.weishu.reflection.Reflection.unseal(base);
        String sha1 = "34:06:43:F7:52:38:C9:82:BC:86:3A:C2:83:C3:8C:13:7D:F8:2B:DF";
        String apkSHA1 = Utils.getPackageSignSHA1(base);
        if (!sha1.replace(":", "").equals(apkSHA1)) {
            // TODO: crash
        }
        LogUtil.debug("app 签名：" + apkSHA1);
        File newNativeLibraryDir = base.getDir(LoadDexUtil.NewNativeLibraryPath, Application.MODE_PRIVATE);
        if (!new File(newNativeLibraryDir.getAbsolutePath(), AESUtil.JIA_GU_NATIVE_LIBRARY).exists()) {
            String nativeLibraryDir = getApplicationInfo().nativeLibraryDir;
            String abi = nativeLibraryDir.substring(nativeLibraryDir.lastIndexOf("/") + 1);
            LogUtil.info("获取到的 abi 裁剪路径：" + abi);
            switch (abi) {
                case "arm64":
                    abi = "arm64-v8a";
                    break;
                case "armeabi":
                    abi = "armeabi-v7a";
                    break;
                case "x86_64":
                    abi = "x86_64";
                    break;
                case "x86":
                    abi = "x86";
                    break;
                default:
                    abi = "arm64-v8a";
                    break;
            }
            LogUtil.info("当前手机的 abi：" + abi);
            Utils.removeNativeLibraries(getApplicationInfo().sourceDir, abi,
                    base.getDir(LoadDexUtil.NewNativeLibraryPath, Application.MODE_PRIVATE).getAbsolutePath());
        }
        LogUtil.debug("nativeLibraryDir:" + getApplicationInfo().nativeLibraryDir);
        File oldJiaguNativeLibrary = new File(getApplicationInfo().nativeLibraryDir, AESUtil.JIA_GU_NATIVE_LIBRARY);
        LogUtil.info("libsxjiagu.so exists? " + oldJiaguNativeLibrary.exists());
        // FIXME: 在 viso S16 Android 14 上不能读取重打包后的 so 文件
        // LogUtil.debug("native library:" + oldJiaguNativeLibrary.getAbsolutePath() + " and md5:" + Utils.getMd5(oldJiaguNativeLibrary));
        // 这里采用改变 NativeLibraryPath 的方式，是因为第三方安全检测报告会警告自定义 so 目录
        // Utils.changeDefaultNativeLibraryPath(getClassLoader(), getApplicationInfo().nativeLibraryDir, newNativeLibraryDir.getAbsolutePath());
        // AESUtil.loadJiaGuLibrary();
        AESUtil.loadJiaGuLibrary(newNativeLibraryDir.getAbsolutePath() + File.separator + AESUtil.JIA_GU_NATIVE_LIBRARY);
        LogUtil.info("load jiagu library.");
        // 加载 dex，并解密出原 app 的 dex 文件进行加载
        boolean result = LoadDexUtil.decodeDexAndReplace(this, getAppVersionCode());

        if (result) {
            //生成原Application，并手动安装ContentProviders
            app = LoadDexUtil.makeApplication(getSrcApplicationClassName());
        } else {
            LogUtil.error("extract dex failed.");
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();
        LogUtil.info("StubApplication onCreate()");
        // create main Apk's Application and replace with it.
        LoadDexUtil.replaceAndRunMainApplication(app);
    }

    private int getAppVersionCode() {
        PackageInfo info = getPackageInfo();
        return info == null ? 0 : info.versionCode;
    }

    private PackageInfo getPackageInfo() {
        PackageInfo pi = null;
        try {
            pi = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_CONFIGURATIONS);
            return pi;
        } catch (Exception e) {
            LogUtil.error(e.getLocalizedMessage());
        }
        return pi;
    }

    /**
     * 获取原application的类名
     *
     * @return 返回类名
     */
    private String getSrcApplicationClassName() {
        try {
            ApplicationInfo ai = this.getPackageManager()
                    .getApplicationInfo(this.getPackageName(),
                            PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            if (bundle != null && bundle.containsKey(APP_KEY)) {
                return bundle.getString(APP_KEY);//className 是配置在xml文件中的。
            } else {
                LogUtil.info("have no application class name");
                return "";
            }
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.error(Log.getStackTraceString(e));
        }
        return "";
    }


    // 以下是加载资源
    protected AssetManager mAssetManager;//资源管理器
    protected Resources mResources;//资源
    protected Resources.Theme mTheme;//主题
/*

    protected void loadResources(String dexPath) {
        try {
            AssetManager assetManager = AssetManager.class.newInstance();
            Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
            addAssetPath.invoke(assetManager, dexPath);
            mAssetManager = assetManager;
        } catch (Exception e) {
            Log.i("inject", "loadResource error:"+Log.getStackTraceString(e));
            e.printStackTrace();
        }
        Resources superRes = super.getResources();
        superRes.getDisplayMetrics();
        superRes.getConfiguration();
        mResources = new Resources(mAssetManager, superRes.getDisplayMetrics(),superRes.getConfiguration());
        mTheme = mResources.newTheme();
        mTheme.setTo(super.getTheme());
    }
*/

    @Override
    public AssetManager getAssets() {
        return mAssetManager == null ? super.getAssets() : mAssetManager;
    }

    @Override
    public Resources getResources() {
        return mResources == null ? super.getResources() : mResources;
    }

    @Override
    public Resources.Theme getTheme() {
        return mTheme == null ? super.getTheme() : mTheme;
    }


}
