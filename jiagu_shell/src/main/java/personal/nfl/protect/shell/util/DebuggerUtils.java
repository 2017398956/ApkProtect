package personal.nfl.protect.shell.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Debug;
import android.os.Process;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

/**
 * @Description: 防止被动态调试
 * 防止恶意注入so文件
 */
public class DebuggerUtils {

    /**
     * 判断当前应用是否是debug状态
     */
    public static boolean isDebuggable(Context context) {
        try {
            ApplicationInfo info = context.getApplicationInfo();
            return (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean finish = false;

    /**
     * 检测是否在非 Debug 编译模式下，进行了调试操作，以防动态调试
     *
     * @param context
     * @param isDebug BuildConfig.DEBUG
     * @return
     */
    public static void checkDebuggableInNotDebugModel(Context context, boolean isDebug, HashMap<String, String> soResult, CheckDebugCallback callback) {
        //非 Debug 编译，反调试检测
        if (!isDebug) {
            if (isDebuggable(context)) {
                callback.onDebug();
            } else {
                Thread t = new Thread(() -> {
                    while (!finish) {
                        try {
                            //每隔 3000ms 检测一次
                            Thread.sleep(3000);
                            //判断是否有调试器连接，是就退出
                            if (Debug.isDebuggerConnected()) {
                                finish = callback.onDebug();
                            } else {
                                // 判断是否被其他进程跟踪，是就退出
                                if (isUnderTraced()) {
                                    finish = callback.onDebug();
                                } else {
                                    HashSet<String> soFiles = getSoList(Process.myPid(), getOriginApplicationId(context));
                                    for (String soFile : soFiles) {
                                        String[] pathSplits = soFile.split("/");
                                        if (TextUtils.isEmpty(soResult.get(pathSplits[pathSplits.length - 1]))) {
                                            finish = callback.onDebug();
                                            break;
                                        }
                                    }
                                }
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }, "SafeGuardThread");
                t.start();
            }
        } else {
            callback.onDebug();
        }
    }

    /**
     * 当我们使用 Ptrace方式跟踪一个进程时，目标进程会记录自己被谁跟踪，
     * 可以查看/proc/pid/status看到这个信息,而没有被调试的时候 TracerPid为0
     *
     * @return
     */
    public static boolean isUnderTraced() {
        String processStatusFilePath = String.format(Locale.US, "/proc/%d/status", android.os.Process.myPid());
        File procInfoFile = new File(processStatusFilePath);
        try {
            BufferedReader b = new BufferedReader(new FileReader(procInfoFile));
            String readLine;
            while ((readLine = b.readLine()) != null) {
                if (readLine.contains("TracerPid")) {
                    String[] arrays = readLine.split(":");
                    if (arrays.length == 2) {
                        int tracerPid = Integer.parseInt(arrays[1].trim());
                        if (tracerPid != 0) {
                            return true;
                        }
                    }
                }
            }

            b.close();
        } catch (Exception e) {
            LogUtil.error(e.getLocalizedMessage());
        }
        return false;
    }

    private static String getOriginApplicationId(Context context) {
        return context.getPackageName();
    }

    public static HashSet<String> getSoList(int pid, String pkg) {
        HashSet<String> temp = new HashSet<>();
        File file = new File("/proc/" + pid + "/maps");
        if (!file.exists()) {
            return temp;
        }
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String lineString = null;
            while ((lineString = bufferedReader.readLine()) != null) {
                String tempString = lineString.trim();
                if (tempString.contains("/data/data/" + pkg) && tempString.contains(".so")) {
                    int index = tempString.indexOf("/data/data");
                    temp.add(tempString.substring(index));
                }
            }
            bufferedReader.close();
        } catch (Exception e) {
            LogUtil.error(e.getLocalizedMessage());
        }
        return temp;
    }

    public interface CheckDebugCallback {
        boolean onDebug();
    }
}
