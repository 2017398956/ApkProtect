package personal.nfl.protect.lib.util;

public class AESUtil {


    /**
     * AES加密
     * @param data 待加密数据
     * @return 返回加密后的数据
     */
    public static native byte[] encrypt(byte[] data);

    /**
     * 对加密数据进行AES解密
     * @param data 加密的数据
     * @return 返回解密的数据
     */
    public static native byte[] decrypt(byte[] data);

}