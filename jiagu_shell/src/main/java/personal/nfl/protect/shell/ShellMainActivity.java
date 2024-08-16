package personal.nfl.protect.shell;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dalvik.system.DexFile;
import personal.nfl.protect.shell.util.AESUtil;
import personal.nfl.protect.shell.util.LogUtil;
import personal.nfl.protect.shell.util.RefInvoke;
import personal.nfl.protect.shell.util.ShellNativeMethod2;
import personal.nfl.protect.shell.util.Utils;

public class ShellMainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shell_main);
        AESUtil.loadJiaGuLibrary();
        findViewById(R.id.btn_test).setOnClickListener(v -> {
            byte[] bytes;
            Object cookie;
            boolean testAllDexes = true;
            ArrayList<Object> arrayList = new ArrayList<>();
            if (testAllDexes) {
                List<byte[]> temp = new ArrayList<>();
                try {
                    for (int i = 1; i < 600; i++) {
                        bytes = Utils.readAssetsClassesDex(this, "test/classes" + i + ".dex");
                        LogUtil.debug(i + " dex file magic number should equal to dex, and magic number is: " + new String(bytes, 0, 3));
                        // bytes[6] += 2;
                        temp.add(bytes);
                    }
                } catch (Exception ignored) {
                }

                if (temp.isEmpty()) {
                    throw new RuntimeException("please run the task createTestDexFiles in the build.grade of root dir.");
                }
                cookie = ShellNativeMethod2.openMemory(temp, Build.VERSION.SDK_INT);
            } else {
                bytes = Utils.readAssetsClassesDex(this, "classes.dex");
                LogUtil.debug("dex file magic number should equal to dex, and magic number is: " + new String(bytes, 0, 3));
                cookie = ShellNativeMethod2.openMemory(bytes, bytes.length, Build.VERSION.SDK_INT);
            }
            LogUtil.debug("cookie:" + Arrays.toString((long[]) cookie));
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