package com.zhh.jiagu.shell.dex;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.zhh.jiagu.shell.util.RefInvoke;
import com.zhh.jiagu.shell.util.ShellNativeMethod2;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

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

        // 反射调用 openMemory 方法加载多个 dex
        // 待 JNI 实现
        for (ByteBuffer dexbuffer : dexBuffers) {
            int dexlen = dexbuffer.limit() - dexbuffer.position();
            Log.d(TAG, "dexbuffer.capacity(): " + dexlen);
            byte[] dex = new byte[dexlen];
            dexbuffer.get(dex);
            Log.d(TAG, "dex前3个字节: " + dex[0] + dex[1] + dex[2]);
            //调用native层方法, OpenMemory返回的是DexFile对象，这是不是说明cookie其实就是DexFile的地址？尤其是对int型的cookie来说
            Object cookie = ShellNativeMethod2.openMemory(dex, dexlen, Build.VERSION.SDK_INT);
            addIntoCookieArray(cookie);
        }
    }

    //重写findClass方法
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = null;

        //获取Dex中的所有类，支持多dex
        ArrayList<String[]> classNameList = getClassNameList(this.cookieArray);
        int classNameNum = classNameList.size();
        Log.d(TAG, "classNameNum num: " + classNameNum);
        //遍历每个dex获取classNameList
        for (int cookiePos = 0; cookiePos < classNameNum; cookiePos++) {
            String[] singleClassNameList = classNameList.get(cookiePos);
            Log.d(TAG, cookiePos + ":singleClassNameList:" + Arrays.toString(singleClassNameList));
            //遍历每个dex中的classNameList，获取className
            for (int classPos = 0; classPos < singleClassNameList.length; classPos++) {
                Log.d(TAG, "className: " + singleClassNameList[classPos]);
                //如果找到了需要加载的类
                if (singleClassNameList[classPos].equals(name)) {
                    clazz = defineClassNative(
                            name.replace('.', '/'),
                            this.mContext.getClassLoader(),
                            this.cookieArray.get(cookiePos)
                    );
                } else {
                    //这一步存疑，都不是要加载的类为什么还有加载？？？
                    clazz = defineClassNative(
                            singleClassNameList[classPos].replace('.', '/'),
                            this.mContext.getClassLoader(),
                            this.cookieArray.get(cookiePos)
                    );
                }
            }
        }

        if (clazz == null) {
            super.findClass(name);
        }

        return clazz;
    }


    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        //其实并不需要改写，就是添加几个日志语句
        Log.d(TAG, "loadClass: " + name + "resolve: " + resolve);
        Class<?> clazz = super.loadClass(name, resolve);
        if (clazz == null) {
            Log.e(TAG, "loadClass failed");
        }
        return clazz;
    }

    //反射调用defineNative方法加载类
    private Class defineClassNative(String name, ClassLoader loader, Object cookie) {
        /*
         * Android5~6的defineClassNative没有DexFile参数
         * Android7~ 的defineClassNative多了DexFile参数
         * */
        Class<?> clazz = null;

        //系统API判断
        if (Build.VERSION.SDK_INT < 23) {
            // ~ Android5
            clazz = (Class) RefInvoke.invokeMethod(
                    "dalvik.system.DexFile",
                    "defineClassNative",
                    new Class[]{String.class, ClassLoader.class, long.class},
                    null,
                    new Object[]{name, loader, (long) cookie}
            );
        } else if (Build.VERSION.SDK_INT == 23) {
            //Android 6
            clazz = (Class) RefInvoke.invokeMethod(
                    "dalvik.system.DexFile",
                    "defineClassNative",
                    new Class[]{String.class, ClassLoader.class, Object.class},
                    null,
                    new Object[]{name, loader, cookie}
            );
        } else {
            //Android 7 ~
            clazz = (Class) RefInvoke.invokeMethod(
                    "dalvik.system.DexFile",
                    "defineClassNative",
                    new Class[]{String.class, ClassLoader.class, Object.class, DexFile.class},
                    null,
                    new Object[]{name, loader, cookie, null} /*设置为空应该没问题吧，反正也不会用到类加载器*/
            );
        }
        return clazz;

    }

    //获取dex中的类名集合
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