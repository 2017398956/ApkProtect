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
import java.nio.file.Files;

public class FileUtils {

    /**
     * 删除指定的文件或目录
     *
     * @param path
     */
    public static void deleteFile(String path) {
        deleteFile(new File(path));
    }

    public static void deleteFile(File file) {
        if (null != file && file.exists()) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (null != files) {
                    for (File child : files) {
                        deleteFile(child);
                    }
                }
                file.delete();
            } else {
                file.delete();
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

    public static String readFileFirstLine(InputStream inputStream) {
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

    public static boolean copyFile(File sourceFile, File destFile) {
        try {
            FileInputStream fileInputStream = new FileInputStream(sourceFile);
            if (destFile.exists()) {
                destFile.delete();
            }
            FileOutputStream fileOutputStream = new FileOutputStream(destFile);
            byte[] bytes = new byte[1024 * 8];
            int readLength = -1;
            while ((readLength = fileInputStream.read(bytes)) > 0) {
                fileOutputStream.write(bytes, 0, readLength);
            }
            fileInputStream.close();
            fileOutputStream.close();
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
