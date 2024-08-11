package com.zhh.jiagu.shell;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.zhh.jiagu.shell.util.AESUtil;
import com.zhh.jiagu.shell.util.LogUtil;
import com.zhh.jiagu.shell.util.RefInvoke;
import com.zhh.jiagu.shell.util.ShellNativeMethod2;
import com.zhh.jiagu.shell.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;

import dalvik.system.DexFile;

public class ShellMainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shell_main);
        AESUtil.loadJiaGuLibrary();
        findViewById(R.id.btn_test).setOnClickListener(v -> {
            byte[] bytes = Utils.readAssetsClassesDex(this, "classes.dex");
            LogUtil.debug("dex file magic number should equal to dex, and magic number is: " + new String(bytes, 0, 3));
            Object cookie = ShellNativeMethod2.openMemory(bytes, bytes.length, Build.VERSION.SDK_INT);
            LogUtil.debug("cookie:" + Arrays.toString((long[]) cookie));
            ArrayList<Object> arrayList = new ArrayList<>();
            arrayList.add(cookie);
            getClassNameList(arrayList);

        });

        findViewById(R.id.btn_cpu).setOnClickListener(v -> {
            testCpu();
        });
    }

    private void testCpu() {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 3000) {
            Log.e("testCpu", "testCpu");
        }
    }

    //获取dex中的类名集合
    private ArrayList<String[]> getClassNameList(ArrayList<Object> cookieArray) {
        /*
         * 注意！！！Android5 中是long类型的cookie，Android6、7是Object类型的cookie
         * */
        ArrayList<String[]> classNameList = new ArrayList<>();
        int cookieNum = cookieArray.size();

        //系统API判断
        if (Build.VERSION.SDK_INT < 23) {
            // ~ Android 5
            for (int i = 0; i < cookieNum; i++) {
                String[] singleDexClassNameList = (String[]) RefInvoke.invokeStaticMethod(
                        DexFile.class.getName(),
                        "getClassNameList",
                        new Class[]{int.class},
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
                LogUtil.debug(i + ":getClassNameList:" + Arrays.toString(singleDexClassNameList));
                classNameList.add(singleDexClassNameList);
            }
        }

        return classNameList;
    }
}