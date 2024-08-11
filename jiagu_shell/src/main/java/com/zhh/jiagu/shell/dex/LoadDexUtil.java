package com.zhh.jiagu.shell.dex;

import android.app.Application;
import android.app.Instrumentation;
import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.os.Build;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.zhh.jiagu.shell.constant.Configs;
import com.zhh.jiagu.shell.util.LogUtil;
import com.zhh.jiagu.shell.util.RefInvoke;
import com.zhh.jiagu.shell.util.Utils;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;
import dalvik.system.InMemoryDexClassLoader;

/**
 * 方式一：壳程序直接对 APK 进行加密方式
 * <p>
 * 这里对APK解密及加载
 */
public class LoadDexUtil {

    private final static String LoadedApk_CLASS = "android.app.LoadedApk";
    private final static String ActivityThread_CLASS = "android.app.ActivityThread";
    private final static String AppBindData_CLASS = "android.app.ActivityThread$AppBindData";
    private final static String ProviderClientRecord_CLASS = "android.app.ActivityThread$ProviderClientRecord";

    public final static String NewNativeLibraryPath = "payload_lib";

    /**
     * 解析apk，得到加密的AppDex.zip文件，并进行解密
     *
     * @param context        壳的context
     * @param appVersionCode
     */
    public static boolean decodeDexAndReplace(Application context, int appVersionCode) {
        try {
            //创建两个文件夹payload_odex，payload_lib 私有的，可写的文件目录
            File odex = context.getDir("payload_odex", Application.MODE_PRIVATE);
//            File libs = context.getDir("payload_lib", Application.MODE_PRIVATE);
            String odexPath = odex.getAbsolutePath();
            // 按版本号来标记 zip, 在 payload_odex 文件夹内，创建真正的 dex zip 文件
            String dexFilePath = String.format(Locale.CHINESE, "%s/AppDex_%d.zip", odexPath, appVersionCode);

            LogUtil.info("dexFilePath:" + dexFilePath);
            LogUtil.info("decodeDexAndReplace =============================开始");

            File dexFile = new File(dexFilePath);
            LogUtil.info("true dex zip size: " + dexFile.length());
            ByteBuffer[] trueDexData = null;
            // 第一次加载APP
            if (!dexFile.exists() || dexFile.length() == 0) {
                // 先清空 odexPath 目录中文件, 防止数据越来越多
                File[] children = odex.listFiles();
                if (children != null) {
                    for (File child : children) {
                        child.delete();
                    }
                }
                LogUtil.info(" ===== App is first loading.");
                long start = System.currentTimeMillis();
                boolean useAssetsDex = true;
                byte[] dexData;
                if (useAssetsDex) {
                    dexData = Utils.readAssetsClassesDex(context);
                } else {
                    String apkPath = context.getApplicationInfo().sourceDir;
                    // 读取程序 classes.dex 文件
                    dexData = Utils.readDexFileFromApk(apkPath);
                }
                if (dexData != null) {
                    //从 classes.dex 中再取出 AppDex.zip 解密后存放到 /AppDex.zip，及其 so 文件放到 payload_lib 下
                    trueDexData = Utils.releaseAppDexFile(dexData, dexFilePath);
                } else {
                    return false;
                }
                LogUtil.info("解压和解密耗时 ===== " + (System.currentTimeMillis() - start) + "  === " + dexFile.exists());
            } else {
                LogUtil.info("dexFile has already existed.");
            }
            // 配置动态加载环境
            // 获取主线程对象
            Object currentActivityThread = getCurrentActivityThread();
            String packageName = context.getPackageName();// 当前 apk 的包名
            LogUtil.info("packageName ===== " + packageName);
            // 获取 LoadedAPk
            ArrayMap<String, WeakReference> mPackages = (ArrayMap) RefInvoke.getField(
                    ActivityThread_CLASS, currentActivityThread, "mPackages");
            LogUtil.info("反射得到的 mPackages ===== " + mPackages);
            WeakReference wr = mPackages.get(packageName);
            ClassLoader mClassLoader = (ClassLoader) RefInvoke.getField(LoadedApk_CLASS, wr.get(), "mClassLoader");
            LogUtil.debug("mClassLoader:" + mClassLoader);
            // 下面这三个 classloader 都是一个
            LogUtil.debug("mClassLoader:" + Integer.toHexString(mClassLoader.hashCode()));
            LogUtil.debug("getClassLoader:" + Integer.toHexString(context.getClassLoader().hashCode()));
            LogUtil.debug("LoadDexUtil.class.getClassLoader:" + Integer.toHexString(LoadDexUtil.class.getClassLoader().hashCode()));
            // 创建被加壳 apk 的 DexClassLoader 对象,加载 apk 内的类和本地代码 （c/c++代码）
            // DexClassLoader dLoader = new DexClassLoader(dexFilePath, odexPath, context.getApplicationInfo().nativeLibraryDir, mClassLoader);
            // FIXME: 重打包后，so 在 vivo S16 Android 14 上不能加载，所以这里先换个位置
            BaseDexClassLoader dLoader;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // FIXME: 由于 so 加载问题及测试不充分，不能保证在 8.1 的机器上不出问题，所以这里不从 8.1 开始处理而是从 10
                // new InMemoryDexClassLoader(trueDexData, mClassLoader);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && trueDexData != null && trueDexData.length > 0) {
                LogUtil.info("trueDexData size:" + trueDexData.length);
                dLoader = new InMemoryDexClassLoader(trueDexData, context.getDir(NewNativeLibraryPath, Application.MODE_PRIVATE).getAbsolutePath(), mClassLoader);
            } else if (Configs.TestOpenMemory23 && Build.VERSION.SDK_INT == Build.VERSION_CODES.M && trueDexData != null && trueDexData.length > 0) {
                dLoader = new MyClassLoader(context, trueDexData, context.getDir(NewNativeLibraryPath, Application.MODE_PRIVATE).getAbsolutePath(), mClassLoader, "", odexPath);
            } else if (Configs.TestOpenMemory25 && Build.VERSION.SDK_INT == Build.VERSION_CODES.N_MR1 && trueDexData != null && trueDexData.length > 0) {
                dLoader = new MyClassLoader(context, trueDexData, context.getDir(NewNativeLibraryPath, Application.MODE_PRIVATE).getAbsolutePath(), mClassLoader, dexFilePath, odexPath);
            } else {
                dLoader = new DexClassLoader(dexFilePath, odexPath, context.getDir(NewNativeLibraryPath, Application.MODE_PRIVATE).getAbsolutePath(), mClassLoader);
            }
            LogUtil.info("创建新的 dexClassLoader:" + dLoader);
            // 把当前进程的 DexClassLoader 设置成了被加壳 apk 的 DexClassLoader  ----有点c++中进程环境的意思~~
            RefInvoke.setField(LoadedApk_CLASS, "mClassLoader", wr.get(), dLoader);
            LogUtil.info("decodeDexAndReplace ============================= 结束");
            return true;
        } catch (Exception e) {
            LogUtil.error("error ===== " + Log.getStackTraceString(e));
        }
        return false;
    }

    /**
     * 构造原 Application 对象
     *
     * @param srcApplicationClassName 原 Application 类名
     * @return 返回原 application 对象
     */
    public static Application makeApplication(String srcApplicationClassName) {
        LogUtil.info("makeApplication ============== " + srcApplicationClassName);
        if (TextUtils.isEmpty(srcApplicationClassName)) {
            LogUtil.error("请配置原APK的Application ===== ");
            return null;
        }

        // 调用静态方法 android.app.ActivityThread.currentActivityThread 获取当前 activity 所在的线程对象
        Object currentActivityThread = getCurrentActivityThread();
        LogUtil.info("currentActivityThread ============ " + currentActivityThread);
        // 获取当前 currentActivityThread 的 mBoundApplication 属性对象，
        // 该对象是一个 AppBindData 类对象，该类是 ActivityThread 的一个内部类
        Object mBoundApplication = getBoundApplication(currentActivityThread);
        LogUtil.info("mBoundApplication ============ " + mBoundApplication);
        //读取 mBoundApplication 中的 info 信息，info 是 LoadedApk 对象
        Object loadedApkInfo = getLoadApkInfoObj(mBoundApplication);
        LogUtil.info("loadedApkInfo ============ " + loadedApkInfo);

        // 先从 LoadedApk 中反射出 mApplicationInfo 变量，并设置其 className 为原 Application 的 className
        // 注意：这里一定要设置，否则 makeApplication 还是壳 Application 对象，造成一直在 attach 中死循环
        ApplicationInfo mApplicationInfo = (ApplicationInfo) RefInvoke.getField(
                LoadedApk_CLASS, loadedApkInfo, "mApplicationInfo");
        mApplicationInfo.className = srcApplicationClassName;
        // 执行 makeApplication（false,null）
        Application app = null;
        Object obj = RefInvoke.invokeMethod(LoadedApk_CLASS,
                "makeApplication", loadedApkInfo,
                new Class[]{boolean.class, Instrumentation.class}, new Object[]{false, null});
        LogUtil.debug("new application:" + obj);
        LogUtil.info("makeApplication ============ app : " + app);

        // 由于源码 ActivityThread 中 handleBindApplication 方法绑定 Application 后会调用 installContentProviders，
        // 此时传入的 context 仍为壳 Application，故此处进手动安装 ContentProviders，调用完成后，清空原 providers
        installContentProviders(app, currentActivityThread, mBoundApplication);
        return app;
    }

    /**
     * 手动安装 ContentProviders
     *
     * @param app                   原 Application 对象
     * @param currentActivityThread 当前 ActivityThread 对象
     * @param boundApplication      当前 AppBindData 对象
     */
    private static void installContentProviders(Application app, Object currentActivityThread, Object boundApplication) {
        if (app == null) return;
        LogUtil.info("执行 installContentProviders =================");
        List<ProviderInfo> providers = (List<ProviderInfo>) RefInvoke.getField(AppBindData_CLASS,
                boundApplication, "providers");
        LogUtil.info("反射拿到 providers = " + providers);
        if (providers != null) {
            RefInvoke.invokeMethod(ActivityThread_CLASS, "installContentProviders", currentActivityThread, new Class[]{Context.class, List.class}, new Object[]{app, providers});
            providers.clear();
        }
    }

    /**
     * Application 替换并运行
     *
     * @param app 原 application 对象
     */
    public static void replaceAndRunMainApplication(Application app) {
        if (app == null) {
            return;
        }

        LogUtil.info("onCreate ===== 开始替换=====");
        // 如果源应用配置有 Application 对象，则替换为源应用 Application，以便不影响源程序逻辑。
        final String appClassName = app.getClass().getName();

        // 调用静态方法 android.app.ActivityThread.currentActivityThread 获取当前 activity 所在的线程对象
        Object currentActivityThread = getCurrentActivityThread();
        // 获取当前 currentActivityThread 的 mBoundApplication 属性对象，
        // 该对象是一个 AppBindData 类对象，该类是 ActivityThread 的一个内部类
        Object mBoundApplication = getBoundApplication(currentActivityThread);
        // 读取 mBoundApplication 中的 info 信息，info 是 LoadedApk 对象
        Object loadedApkInfo = getLoadApkInfoObj(mBoundApplication);
        // 检测 loadApkInfo 是 否为空
        if (loadedApkInfo == null) {
            LogUtil.error("loadedApkInfo ===== is null !!!!");
        } else {
            LogUtil.info("loadedApkInfo ===== " + loadedApkInfo);
        }

        // 把当前进程的 mApplication 设置成了原 application,
        RefInvoke.setField(LoadedApk_CLASS, "mApplication", loadedApkInfo, app);
        Object oldApplication = RefInvoke.getField(ActivityThread_CLASS, currentActivityThread, "mInitialApplication");
        LogUtil.info("oldApplication ===== " + oldApplication);
        ArrayList<Application> mAllApplications = (ArrayList<Application>) RefInvoke.getField(
                ActivityThread_CLASS, currentActivityThread, "mAllApplications");
        // 将壳 oldApplication 从 ActivityThread#mAllApplications 列表中移除
        mAllApplications.remove(oldApplication);
        // 将原 Application 赋值给 mInitialApplication
        RefInvoke.setField(ActivityThread_CLASS, "mInitialApplication", currentActivityThread, app);
//        ApplicationInfo appinfo_In_LoadedApk = (ApplicationInfo) RefInvoke.getFieldOjbect(
//                LoadedApk_CLASS, loadedApkInfo, "mApplicationInfo");
        ApplicationInfo appinfo_In_AppBindData = (ApplicationInfo) RefInvoke.getField(
                AppBindData_CLASS, mBoundApplication, "appInfo");
//        appinfo_In_LoadedApk.className = appClassName;
        appinfo_In_AppBindData.className = appClassName;

        ArrayMap mProviderMap = (ArrayMap) RefInvoke.getField(ActivityThread_CLASS, currentActivityThread, "mProviderMap");
        Iterator it = mProviderMap.values().iterator();
        while (it.hasNext()) {
            Object providerClientRecord = it.next();
            ContentProvider localProvider = (ContentProvider) RefInvoke.getField(ProviderClientRecord_CLASS, providerClientRecord, "mLocalProvider");
            RefInvoke.setField(ContentProvider.class.getName(), "mContext", localProvider, app);
        }

        LogUtil.info("app ===== " + app + "=====开始执行原Application");
        app.onCreate();
    }

    /**
     * 调用静态方法android.app.ActivityThread.currentActivityThread 获取当前 activity 所在的线程对象
     *
     * @return 当前 ActivityThread 对象
     */
    private static Object getCurrentActivityThread() {
        // 也可以通过 android.app.ActivityThread 中的 sCurrentActivityThread 字段获取
        return RefInvoke.invokeStaticMethod(ActivityThread_CLASS,
                "currentActivityThread", new Class[]{}, new Object[]{});
    }

    /**
     * 获取当前 currentActivityThread 的 mBoundApplication 属性对象，
     * 该对象是一个 AppBindData 类对象，该类是 ActivityThread 的一个内部类
     *
     * @param currentActivityThread 当前 ActivityThread 对象
     * @return 返回 AppBindData对象
     */
    private static Object getBoundApplication(Object currentActivityThread) {
        if (currentActivityThread == null)
            return null;
        return RefInvoke.getField(ActivityThread_CLASS,
                currentActivityThread, "mBoundApplication");
    }

    /**
     * 读取 mBoundApplication 中的 info 信息，info 是 LoadedApk 对象
     *
     * @param boundApplication AppBindData 对象
     * @return LoadedApkInfo 对象
     */
    private static Object getLoadApkInfoObj(Object boundApplication) {
        return RefInvoke.getField(AppBindData_CLASS,
                boundApplication, "info");
    }
}
