package personal.nfl.protect.demo;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.tencent.mmkv.MMKV;

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

    }


    public static Context getApplication(){
        return instance;
    }

    private void initMMKV() {
        MMKV.initialize(this);
    }
}
