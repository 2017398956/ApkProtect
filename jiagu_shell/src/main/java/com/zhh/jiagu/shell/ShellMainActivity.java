package com.zhh.jiagu.shell;

import android.app.Activity;
import android.os.Bundle;

import com.zhh.jiagu.shell.util.AESUtil;
import com.zhh.jiagu.shell.util.ShellNativeMethod;

public class ShellMainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shell_main);
        AESUtil.loadJiaGuLibrary();
        findViewById(R.id.btn_test).setOnClickListener(v -> {
            byte[] bytes = new byte[10];
            ShellNativeMethod.loadDexFile(bytes, 10);
        });
    }
}