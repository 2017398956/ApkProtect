package com.zhh.jiagu.shell;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.zhh.jiagu.shell.util.AESUtil;
import com.zhh.jiagu.shell.util.LogUtil;
import com.zhh.jiagu.shell.util.ShellNativeMethod;
import com.zhh.jiagu.shell.util.ShellNativeMethod2;
import com.zhh.jiagu.shell.util.Utils;

import java.io.IOException;

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
            LogUtil.debug("cookie:" + ShellNativeMethod2.OpenMemory(bytes, bytes.length, Build.VERSION.SDK_INT));
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
}