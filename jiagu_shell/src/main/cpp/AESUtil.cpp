#include "AESUtil.h"
#include <string>
#include "xxtea.h"
#include "utils/my_android_log.h"

static const char *TAG = "JiaGu_SXJY";

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     personal_nfl_protect_shell_util_AESUtil
 * Method:    encrypt
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_personal_nfl_protect_shell_util_AESUtil_encrypt
        (JNIEnv *env, jclass clazz, jbyteArray data) {

    //jbyteArray 转换成byte*
    jboolean isCopy = false;
    jbyte *dataBuff = env->GetByteArrayElements(data, &isCopy);
    jsize dataSize = env->GetArrayLength(data);
    LOG_D(TAG, "data size----%d", dataSize);

    const char *key = "personal_nfl_protect_app";
    size_t len;
    jbyte *encrypt_data = (jbyte *) xxtea_encrypt(dataBuff, dataSize, key, &len);
    //使用isCopy 与GetByteArrayElements保持一致
    env->ReleaseByteArrayElements(data, encrypt_data, isCopy);

    LOG_E(TAG, "encrypt_data size === %zu", len);

    //将char*转为jbyteArray
    jbyteArray array = env->NewByteArray(len);
    env->SetByteArrayRegion(array, 0, len, reinterpret_cast<jbyte *>(encrypt_data));
    return array;
}

/*
 * Class:     personal_nfl_protect_shell_util_AESUtil
 * Method:    decrypt
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_personal_nfl_protect_shell_util_AESUtil_decrypt
        (JNIEnv *env, jclass clazz, jbyteArray data) {
    // 将jbyteArray转换成byte*
    jboolean isCopy = false;
    jbyte *dataBuff = env->GetByteArrayElements(data, &isCopy);
    jsize dataSize = env->GetArrayLength(data);

    LOG_D(TAG, "data size ==== %d", dataSize);

    //decrypt
    const char *key = "personal_nfl_protect_app";
    size_t len;
    jbyte *decrypt_data = (jbyte *) xxtea_decrypt(dataBuff, dataSize, key, &len);
    //env->ReleaseByteArrayElements(data,decrypt_data,isCopy);

    LOG_D(TAG, "decrypt_data size====%zu", len);

    //将char*转为jbyteArray
    jbyteArray array = env->NewByteArray(len);
    env->SetByteArrayRegion(array, 0, len, reinterpret_cast<jbyte *>(decrypt_data));
    return array;
}

const char *getVersion() {
    return "1.0.0";
}

#ifdef __cplusplus
}
#endif

const char *getEmail(const char *prefix) {
    char *result = new char[strlen(prefix) + 19];
    sprintf(result, "%s_%s", prefix, "2017398956@qq.com");
    return result;
}

int getVersionCode() {
    return 1;
}
