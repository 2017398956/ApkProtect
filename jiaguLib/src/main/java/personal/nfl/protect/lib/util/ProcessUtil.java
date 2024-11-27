package personal.nfl.protect.lib.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

import personal.nfl.protect.lib.listener.OnReadLineListener;

public class ProcessUtil {

    public static boolean execCmdAndPrint(String cmd, OnReadLineListener listener) throws Exception {
        return exeCmd(cmd, true, listener);
    }

    /**
     * 执行命令
     *
     * @param cmd
     * @throws Exception
     */
    public static boolean exeCmd(String cmd) throws Exception {
        return exeCmd(cmd, false, null);
    }

    public static boolean exeCmd(String cmd, OnReadLineListener listener) throws Exception {
        return exeCmd(cmd, false, listener);
    }

    public static boolean exeCmd(String cmd, boolean printLog, OnReadLineListener listener) throws Exception {
        if (printLog) {
            System.out.println("begin to exec command ===>" + cmd);
        }
        String cmdPrefix = "cmd -c ";
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")) {
            cmdPrefix = "cmd /c ";
        }
        Process process = Runtime.getRuntime().exec(cmdPrefix + cmd);
        ProcessUtil.consumeInputStream(process.getInputStream(), printLog, listener);
        ProcessUtil.consumeInputStream(process.getErrorStream(), printLog, listener);
        process.waitFor();
        if (process.exitValue() != 0) {
            throw new RuntimeException("exec command failed ===>" + cmd);
        }
        return true;
    }

    /**
     * 消费 InputStream，并返回
     */
    private static String consumeInputStream(InputStream is, boolean printLog, OnReadLineListener onReadLineListener) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String s;
        while ((s = br.readLine()) != null) {
            sb.append(s);
            if (printLog) {
                System.out.println(s);
            }
            if (onReadLineListener != null) {
                if (onReadLineListener.onReadLine(s)) {
                    return sb.toString();
                }
            }
        }
        return sb.toString();
    }
}
