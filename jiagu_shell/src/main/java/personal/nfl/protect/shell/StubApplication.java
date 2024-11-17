package personal.nfl.protect.shell;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import personal.nfl.protect.shell.dex.LoadDexUtil;
import personal.nfl.protect.shell.entity.ShellConfigsBean;
import personal.nfl.protect.shell.util.AESUtil;
import personal.nfl.protect.shell.util.DebuggerUtils;
import personal.nfl.protect.shell.util.LogUtil;
import personal.nfl.protect.shell.util.RefInvoke;
import personal.nfl.protect.shell.util.Utils;

public class StubApplication extends Application {

    private static final String APP_KEY = "APPLICATION_CLASS_NAME";
    private static final String SP_SHELL_DEX = "shell_dex";
    private static final String SO_VERSION = "so_version_code";
    private static final String SO_VERSION_NAME = "so_version_name";
    private Application app;
    private String abi;
    private final ShellConfigsBean shellConfigsBean = new ShellConfigsBean();

    public StubApplication() {
        super();
        LogUtil.debug("StubApplication created.");
    }

    private void checkSha1(Context base) {
        if (!shellConfigsBean.canResign) {
            String sha1 = shellConfigsBean.sha1;
            String apkSHA1 = Utils.getPackageSignSHA1(base);
            if (sha1 != null && !sha1.replace(":", "").equals(apkSHA1)) {
                Toast.makeText(base, "This is not an official app", Toast.LENGTH_LONG).show();
                new Handler().postDelayed(() -> {
                    throw new RuntimeException("This is not an official app");
                }, 2000);
            }
            LogUtil.debug("app 原签名：" + sha1);
            LogUtil.debug("app 新签名：" + apkSHA1);
        }
    }

    private void parseShellConfigs() {
        JSONObject shellConfigsObj = null;
        try {
            String shellConfigsStr = Utils.readFirstLineInFile(getAssets().open("apk_protect/shell_configs.bin"));
            shellConfigsObj = new JSONObject(shellConfigsStr);
            String[] abis = new String[]{"arm64_v8a", "armeabi_v7a", "x86_64", "x86"};
            List<HashMap<String, String>> allHashMap = new ArrayList<>();
            allHashMap.add(shellConfigsBean.arm64_v8a);
            allHashMap.add(shellConfigsBean.armeabi_v7a);
            allHashMap.add(shellConfigsBean.x86_64);
            allHashMap.add(shellConfigsBean.x86);
            for (int i = 0; i < abis.length; i++) {
                JSONObject temp = (JSONObject) shellConfigsObj.get(abis[i]);
                Iterator<String> keys = temp.keys();
                String key;
                while (keys.hasNext()) {
                    key = keys.next();
                    allHashMap.get(i).put(key, temp.getString(key));
                }
            }
            shellConfigsBean.canResign = shellConfigsObj.optBoolean("canResign", false);
            shellConfigsBean.debuggable = shellConfigsObj.optBoolean("debuggable", false);
            shellConfigsBean.sha1 = shellConfigsObj.getString("sha1");
            shellConfigsBean.assets = shellConfigsObj.getBoolean("assets");
        } catch (Exception ignored) {
        }
        String nativeLibraryDir = getApplicationInfo().nativeLibraryDir;
        // so name | so path
        HashMap<String, String> soResult = shellConfigsBean.soResult;
        abi = nativeLibraryDir.substring(nativeLibraryDir.lastIndexOf("/") + 1);
        LogUtil.debug("nativeLibraryDir:" + nativeLibraryDir);
        switch (abi) {
            case "armeabi":
            case "armeabi-v7a":
                abi = "armeabi-v7a";
                soResult.putAll(shellConfigsBean.x86_64);
                soResult.putAll(shellConfigsBean.x86);
                soResult.putAll(shellConfigsBean.arm64_v8a);
                soResult.putAll(shellConfigsBean.armeabi_v7a);
                break;
            case "x86_64":
                abi = "x86_64";
                soResult.putAll(shellConfigsBean.armeabi_v7a);
                soResult.putAll(shellConfigsBean.arm64_v8a);
                soResult.putAll(shellConfigsBean.x86);
                soResult.putAll(shellConfigsBean.x86_64);
                break;
            case "x86":
                abi = "x86";
                soResult.putAll(shellConfigsBean.arm64_v8a);
                soResult.putAll(shellConfigsBean.armeabi_v7a);
                soResult.putAll(shellConfigsBean.x86_64);
                soResult.putAll(shellConfigsBean.x86);
                break;
            default: // "arm": "arm64":
                abi = "arm64-v8a";
                soResult.putAll(shellConfigsBean.x86);
                soResult.putAll(shellConfigsBean.x86_64);
                soResult.putAll(shellConfigsBean.armeabi_v7a);
                soResult.putAll(shellConfigsBean.arm64_v8a);
                break;
        }
        LogUtil.info("当前手机的 abi：" + abi);
    }

    private void checkDebug(Context context, HashMap<String, String> soResult) {
        if (!shellConfigsBean.debuggable) {
            DebuggerUtils.checkDebuggableInNotDebugModel(context, false, soResult, () -> {
                Toast.makeText(context, "Can't run on debug.", Toast.LENGTH_LONG).show();
                new Handler().postDelayed(() -> {
                    throw new RuntimeException("Can't run on debug.");
                }, 2000);
                return true;
            });
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // me.weishu.reflection.Reflection.unseal(base);
        parseShellConfigs();
        checkSha1(base);
        checkDebug(base, shellConfigsBean.soResult);
        File newNativeLibraryDir = base.getDir(LoadDexUtil.NewNativeLibraryPath, Application.MODE_PRIVATE);
        if (Configs.copyNative) {
            // FIXME: 在 viso S16 Android 14 上不能读取重打包后的 so 文件，所以这里改变下 so 的位置
            SharedPreferences sharedPreferences = getSharedPreferences(SP_SHELL_DEX, MODE_PRIVATE);
            int soVersionCode = sharedPreferences.getInt(SO_VERSION, 0);
            String soVersionName = sharedPreferences.getString(SO_VERSION_NAME, "");
            if (!new File(newNativeLibraryDir.getAbsolutePath(), AESUtil.JIA_GU_NATIVE_LIBRARY).exists()
                    || (soVersionCode != getAppVersionCode() || !soVersionName.equals(getPackageInfo().versionName))) {
                Utils.removeNativeLibraries(getApplicationInfo().sourceDir, abi,
                        base.getDir(LoadDexUtil.NewNativeLibraryPath, Application.MODE_PRIVATE).getAbsolutePath(), shellConfigsBean.soResult);
                LogUtil.debug("so list:" + new JSONObject(shellConfigsBean.soResult));
                sharedPreferences.edit().putInt(SO_VERSION, getAppVersionCode()).putString(SO_VERSION_NAME, getPackageInfo().versionName).commit();
            }
            Utils.changeDefaultNativeLibraryPath(getClassLoader(), getApplicationInfo().nativeLibraryDir, newNativeLibraryDir.getAbsolutePath());
            getApplicationInfo().nativeLibraryDir = newNativeLibraryDir.getAbsolutePath();
            // 这里采用改变 NativeLibraryPath 的方式，是因为第三方安全检测报告会警告自定义 so 目录
            if (Configs.replaceNativePath) {
                AESUtil.loadJiaGuLibrary();
            } else {
                AESUtil.loadJiaGuLibrary(newNativeLibraryDir.getAbsolutePath() + File.separator + AESUtil.JIA_GU_NATIVE_LIBRARY);
            }
        } else {
            // LogUtil.debug("native library:" + oldJiaguNativeLibrary.getAbsolutePath() + " and md5:" + Utils.getMd5(oldJiaguNativeLibrary));
            AESUtil.loadJiaGuLibrary();
        }
        LogUtil.debug("load jiagu library.");
        // 加载 dex，并解密出原 app 的 dex 文件进行加载
        boolean result = LoadDexUtil.decodeDexAndReplace(this, getAppVersionCode());
        if (result) {
            // 解决 Android 6 上无法添加 assetPath
            String newAssetsPath = null;
            if (shellConfigsBean.assets) {
                newAssetsPath = Utils.copyAssetsFile(this, "assets.zip");
            }
            if (!TextUtils.isEmpty(newAssetsPath)) {
                Object assetsCount = RefInvoke.invokeMethod(AssetManager.class.getName(), "addAssetPath", getAssets(), new Class[]{String.class}, new Object[]{newAssetsPath});
                if (null != assetsCount) {
                    int addResult = (int) assetsCount;
                    LogUtil.info("add assets path 1", "result:" + addResult);
                }
            }
            //生成原Application，并手动安装ContentProviders
            app = LoadDexUtil.makeApplication(getSrcApplicationClassName());
            if (app != null) {
                if (Configs.copyNative) {
                    app.getApplicationInfo().nativeLibraryDir = newNativeLibraryDir.getAbsolutePath();
                }
                if (!TextUtils.isEmpty(newAssetsPath)) {
                    // 再添加一次，提高兼容性
                    Object assetsCount = RefInvoke.invokeMethod(AssetManager.class.getName(), "addAssetPath", app.getAssets(), new Class[]{String.class}, new Object[]{newAssetsPath});
                    if (null != assetsCount) {
                        int addResult = (int) assetsCount;
                        LogUtil.info("add assets path 2", "result:" + addResult);
                    }
                }
            }
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
