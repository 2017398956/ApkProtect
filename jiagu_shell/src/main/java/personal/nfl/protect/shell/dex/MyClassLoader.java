package personal.nfl.protect.shell.dex;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import personal.nfl.protect.shell.util.LogUtil;
import personal.nfl.protect.shell.util.RefInvoke;
import personal.nfl.protect.shell.util.ShellNativeMethod2;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

public class MyClassLoader extends DexClassLoader {

    private static final String TAG = "MyClassLoader";
    private ArrayList<Object> cookieArray = new ArrayList<>();
    private Context mContext;

    public MyClassLoader(Context context, ByteBuffer[] dexBuffers, String librarySearchPath,
                         ClassLoader parent, String dexPath, String optimizedDirectory) {
        super(dexPath, optimizedDirectory, librarySearchPath, parent);
        setContext(context);
        List<byte[]> dexList = new ArrayList<>();
        // 反射调用 openMemory 方法加载多个 dex
        // 待 JNI 实现
        for (ByteBuffer dexBuffer : dexBuffers) {
            int dexLen = dexBuffer.limit() - dexBuffer.position();
            LogUtil.debug(TAG, "dex file length:" + dexLen);
            byte[] dex = new byte[dexLen];
            dexBuffer.get(dex);
            dexList.add(dex);
            // 调用 native 层方法, OpenMemory 返回的是 DexFile 对象，这是不是说明 cookie其实就是 DexFile 的地址？
            // 尤其是对 int 型的 cookie 来说
        }
        Object cookie = ShellNativeMethod2.openMemory(dexList, Build.VERSION.SDK_INT);
        LogUtil.debug(TAG, "cookie:" + Arrays.toString((long[]) cookie));
        addIntoCookieArray(cookie);
    }

    //重写findClass方法
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        LogUtil.debug(TAG, "before findClass " + name);
        Class<?> clazz = null;
        //获取Dex中的所有类，支持多dex
        ArrayList<String[]> classNameList = getClassNameList(this.cookieArray);
        int classNameNum = classNameList.size();
        //遍历每个dex获取classNameList
        for (int cookiePos = 0; cookiePos < classNameNum; cookiePos++) {
            String[] singleClassNameList = classNameList.get(cookiePos);
            LogUtil.debug(TAG, cookiePos + ":singleClassNameList " + Arrays.toString(singleClassNameList));
            // 遍历每个 dex 中的 classNameList，获取 className
            for (int classPos = 0; classPos < singleClassNameList.length; classPos++) {
                //如果找到了需要加载的类
                if (singleClassNameList[classPos].equals(name)) {
                    clazz = defineClassNative(
                            name.replace('.', '/'),
                            this.mContext.getClassLoader(),
                            this.cookieArray.get(cookiePos)
                    );
                    break;
                } else {
                    //这一步存疑，都不是要加载的类为什么还有加载？？？
//                    clazz = defineClassNative(
//                            singleClassNameList[classPos].replace('.', '/'),
//                            this.mContext.getClassLoader(),
//                            this.cookieArray.get(cookiePos)
//                    );
                }
            }
        }

        if (clazz == null) {
            super.findClass(name);
        }
        LogUtil.debug(TAG, "find class " + name + " ? " + (clazz != null));
        return clazz;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        //其实并不需要改写，就是添加几个日志语句
        Log.d(TAG, "loadClass: " + name + " resolve: " + resolve);
        Class<?> clazz = super.loadClass(name, resolve);
        if (clazz == null) {
            LogUtil.error(TAG, "loadClass " + name + " failed!!!");
        } else {
            LogUtil.debug(TAG, "load Class:" + name + " success!!!");
        }
        return clazz;
    }

    // 反射调用 defineNative 方法加载类
    private Class defineClassNative(String name, ClassLoader loader, Object cookie) {
        LogUtil.debug(TAG, "defineClassNative's className:" + name + " classLoader:" + loader);
        /*
         * Android 5~6 的 defineClassNative 没有 DexFile 参数
         * Android 7~ 的 defineClassNative 多了 DexFile 参数
         * */
        Class<?> clazz = null;

        //系统API判断
        if (Build.VERSION.SDK_INT < 23) {
            // ~ Android5
            clazz = (Class) RefInvoke.invokeStaticMethod(
                    DexFile.class.getName(),
                    "defineClassNative",
                    new Class[]{String.class, ClassLoader.class, long.class},
                    new Object[]{name, loader, cookie}
            );
        } else if (Build.VERSION.SDK_INT == 23) {
            //Android 6
            clazz = (Class) RefInvoke.invokeStaticMethod(
                    DexFile.class.getName(),
                    "defineClassNative",
                    new Class[]{String.class, ClassLoader.class, Object.class},
                    new Object[]{name, loader, cookie}
            );
        } else {
            //Android 7 ~
            clazz = (Class) RefInvoke.invokeStaticMethod(
                    DexFile.class.getName(),
                    "defineClassNative",
                    new Class[]{String.class, ClassLoader.class, Object.class, DexFile.class},
                    new Object[]{name, loader, cookie, null} /*设置为空应该没问题吧，反正也不会用到类加载器*/
            );
        }
        return clazz;

    }

    //获取 dex 中的类名集合
    private ArrayList<String[]> getClassNameList(ArrayList<Object> cookieArray) {
        /*
         * 注意！！！Android5 中是long类型的cookie，Android6、7是Object类型的cookie
         * */
        ArrayList<String[]> classNameList = new ArrayList<String[]>();
        int cookieNum = cookieArray.size();

        //系统API判断
        if (Build.VERSION.SDK_INT < 23) {
            // ~ Android 5
            for (int i = 0; i < cookieNum; i++) {
                String[] singleDexClassNameList = (String[]) RefInvoke.invokeStaticMethod(
                        DexFile.class.getName(),
                        "getClassNameList",
                        new Class[]{long.class},
                        new Object[]{cookieArray.get(i)}
                );
                classNameList.add(singleDexClassNameList);
            }
        } else {
            // Android 6 ~
            for (int i = 0; i < cookieNum; i++) {
                String[] singleDexClassNameList = (String[]) RefInvoke.invokeStaticMethod(
                        DexFile.class.getName(),
                        "getClassNameList",
                        new Class[]{Object.class},
                        new Object[]{cookieArray.get(i)}
                );
                LogUtil.debug(TAG, cookieArray.get(i) + " getClassNameList:" + Arrays.toString(singleDexClassNameList));
                classNameList.add(singleDexClassNameList);
            }
        }

        return classNameList;
    }

    private void setContext(Context context) {
        this.mContext = context;
    }

    private void addIntoCookieArray(Object cookie) {
        this.cookieArray.add(cookie);
    }

}