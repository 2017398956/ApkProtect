package personal.nfl.protect.lib.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class FileUtils {

    /**
     * 删除指定的文件或目录
     *
     * @param path
     */
    public static void deleteFile(String path) {
        File file = new File(path);
        //文件存在时，执行删除操作
        if (file.exists()) {
            if (file.isFile()) {
                file.delete();
            } else {
                File[] files = file.listFiles();
                if (null != files) {
                    for (File child : files) {
                        deleteFile(child.getAbsolutePath());
                    }
                } else {
                    // 删除空目录
                    file.delete();
                }
            }
        }
    }

    /**
     * 将指定的内容写入到一个文件中
     *
     * @param content 输出内容
     * @param outFile 输出的文件
     * @return 返回是否写入成功
     */
    public static boolean writeFile(String content, String outFile) {
        try {
            FileOutputStream os = new FileOutputStream(outFile);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os));
            bw.write(content);
            bw.flush();
            bw.close();
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static String readFile(InputStream inputStream) {
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        try {
            return bufferedReader.readLine();
        } catch (IOException ignored) {
        } finally {
            try {
                inputStream.close();
                inputStreamReader.close();
                bufferedReader.close();
            } catch (IOException ignored) {
            }
        }
        return null;
    }


    public static String getAppApplicationName(File file) {
        if (!file.exists()) {
            return null;
        }
        try {
            FileInputStream is = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            boolean isFindApplicationTag = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains("E: application")) {
                    isFindApplicationTag = true;
                } else if (isFindApplicationTag && line.contains("E: ")) {
                    // 此时说明 application 标签无 android:name属性
                    line = null;
                    break;
                } else if (isFindApplicationTag && line.contains("android:name")) {
                    break;
                }
            }
            reader.close();
            is.close();

            // 解析line获取 application 的 class name
            if (line != null && line.contains("\"")) {
                String subStr = line.substring(line.indexOf("\"") + 1);
                return subStr.substring(0, subStr.indexOf("\""));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int getAppMinSdk(File file) {
        int minSdkVersion = -1;
        if (!file.exists()) {
            return minSdkVersion;
        }
        try {
            FileInputStream is = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("android:minSdkVersion")) {
                    try {
                        minSdkVersion = Integer.parseInt(line.substring(line.length() - 2), 16);
                    } catch (Exception ignored) {
                    }
                    break;
                }
            }
            reader.close();
            is.close();
        } catch (Exception ignored) {
        }
        return minSdkVersion;
    }

}
