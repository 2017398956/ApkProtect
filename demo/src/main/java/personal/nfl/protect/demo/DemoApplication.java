package personal.nfl.protect.demo;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try (
                InputStream fileInputStream = getAssets().open("test.txt");
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        ) {
            Log.d("test_assets1", "read line:" + bufferedReader.readLine());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {

            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {

            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {

            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {

            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {

            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {

            }
        });
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
            Log.d("test_assets2", "read line:" + bufferedReader.readLine());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static Context getApplication() {
        return instance;
    }

    private void initMMKV() {
        MMKV.initialize(this);
    }
}
