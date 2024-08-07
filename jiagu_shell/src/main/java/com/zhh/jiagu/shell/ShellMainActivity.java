package com.zhh.jiagu.shell;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.zhh.jiagu.shell.util.AESUtil;
import com.zhh.jiagu.shell.util.LogUtil;
import com.zhh.jiagu.shell.util.RefInvoke;
import com.zhh.jiagu.shell.util.ShellNativeMethod;
import com.zhh.jiagu.shell.util.ShellNativeMethod2;
import com.zhh.jiagu.shell.util.Utils;

import java.io.IOException;
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
//            LogUtil.debug("cookie:" + ShellNativeMethod.loadDexFile(bytes, bytes.length));
            for (int i = 0; i < 4; i++) {
                LogUtil.debug(i + ":" + new String(bytes, i, 1));
            }
            Object cookie = ShellNativeMethod2.OpenMemory(bytes, bytes.length, Build.VERSION.SDK_INT);
            LogUtil.debug("cookie:" + cookie);
            ArrayList<long[]> arrayList = new ArrayList<>();
            arrayList.add((long[]) cookie);
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
    private ArrayList<String[]> getClassNameList(ArrayList<long[]> cookieArray) {
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
                LogUtil.debug(i + ":getClassNameList:" + Arrays.toString(singleDexClassNameList));
                classNameList.add(singleDexClassNameList);
            }
        }

        return classNameList;
    }
}