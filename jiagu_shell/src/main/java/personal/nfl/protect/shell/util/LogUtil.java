package personal.nfl.protect.shell.util;

import android.util.Log;

public class LogUtil {

    public static final String TAG = "JiaGuApk";

    public static void info(String msg) {
        Log.i(TAG, msg);
    }

    public static void info(String tag, String msg) {
        Log.i(tag, msg);
    }

    public static void debug(String msg) {
        Log.d(TAG, msg);
    }

    public static void debug(String TAG, String msg) {
        Log.d(TAG, msg);
    }

    public static void error(String msg) {
        Log.e(TAG, msg);
    }

    public static void error(String TAG, String msg) {
        Log.e(TAG, msg);
    }

    public static void warn(String msg) {
        Log.w(TAG, msg);
    }
}
