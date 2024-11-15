package personal.nfl.protect.demo;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.tencent.mmkv.MMKV;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class DemoApplication extends Application {

    private static DemoApplication instance = null;

    public DemoApplication() {
        Log.d("JiaguApk", "DemoApplication created.");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        initMMKV();
        try (
                InputStream fileInputStream = getAssets().open("test.txt");
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        ) {
            Log.d("test_assets", "read line:" + bufferedReader.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static Context getApplication() {
        return instance;
    }

    private void initMMKV() {
        MMKV.initialize(this);
    }
}
