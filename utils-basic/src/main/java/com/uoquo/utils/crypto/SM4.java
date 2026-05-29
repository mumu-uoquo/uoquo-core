/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.crypto;

import com.uoquo.utils.StringUtil;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * 描述：SM4 对称加解密算法工具类. <br>
 * 备注：基于 Bouncy Castle 实现，支持 ECB 和 CBC 两种工作模式
 * <ul>
 *   <li>ECB 模式：SM4/ECB/PKCS5Padding，简单快速，相同明文产生相同密文</li>
 *   <li>CBC 模式：SM4/CBC/PKCS5Padding，安全性更高，需要初始向量（IV）</li>
 * </ul>
 * 说明：
 * <ul>
 *   <li>SM4 密钥长度固定为 128 位（16 字节）</li>
 *   <li>SM4 分组长度固定为 128 位（16 字节）</li>
 *   <li>String 接口：明文为 UTF-8 编码，密文为小写 hex 编码</li>
 *   <li>byte[] 接口：直接操作字节数组</li>
 * </ul>
 * 日期：2025-01-01 00:00 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2025-01-01     uoquo team       创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class SM4 {
    // 日志
    protected static final Logger log = LoggerFactory.getLogger(SM4.class);

    /**
     * 加密算法（ECB 模式）.
     */
    public static final String ALGORITHM_ECB = "SM4/ECB/PKCS5Padding";

    /**
     * 加密算法（CBC 模式）.
     */
    public static final String ALGORITHM_CBC = "SM4/CBC/PKCS5Padding";

    /**
     * 密钥长度（16 字节 = 128 位）.
     */
    private static final int KEY_SIZE = 16;

    /**
     * 分组长度（16 字节 = 128 位）.
     */
    private static final int BLOCK_SIZE = 16;

    /**
     * 私有构造函数，防止实例化.
     */
    private SM4() {
    }

    // ===== ECB 模式加密/解密（String 接口）=====

    /**
     * SM4 ECB 加密.
     * @param data 明文（UTF-8 编码）
     * @param key  密钥（16 字符 UTF-8 字符串）
     * @return 密文（小写 hex 编码）
     * @throws IllegalArgumentException 参数无效或密钥长度不是 16 字节
     * @throws GeneralSecurityException 加密失败
     */
    public static String encryptECB(String data, String key) throws GeneralSecurityException {
        if (StringUtil.isNull(data)) {
            throw new IllegalArgumentException("加密内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("SM4密钥不能为空");
        }
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] result = encryptECB(dataBytes, keyBytes);
        return StringUtil.byte2hex(result);
    }

    /**
     * SM4 ECB 解密.
     * @param cipherHex 密文（hex 编码）
     * @param key       密钥（16 字符 UTF-8 字符串）
     * @return 明文（UTF-8 字符串）
     * @throws IllegalArgumentException 参数无效或密钥长度不是 16 字节
     * @throws GeneralSecurityException 解密失败
     */
    public static String decryptECB(String cipherHex, String key) throws GeneralSecurityException {
        if (StringUtil.isNull(cipherHex)) {
            throw new IllegalArgumentException("解密内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("SM4密钥不能为空");
        }
        byte[] cipherBytes = StringUtil.hex2byte(cipherHex);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] result = decryptECB(cipherBytes, keyBytes);
        return new String(result, StandardCharsets.UTF_8);
    }

    // ===== ECB 模式加密/解密（byte[] 接口）=====

    /**
     * SM4 ECB 加密.
     * @param data 明文 byte 数组
     * @param key  密钥 byte 数组（必须 16 字节）
     * @return 密文 byte 数组
     * @throws IllegalArgumentException 参数无效或密钥长度不是 16 字节
     * @throws GeneralSecurityException 加密失败
     */
    public static byte[] encryptECB(byte[] data, byte[] key) throws GeneralSecurityException {
        BCProvider.ensureInitialized();
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        if (key == null) {
            throw new IllegalArgumentException("SM4密钥不能为空");
        }
        checkKey(key);
        // 加密
        SecretKeySpec keySpec = new SecretKeySpec(key, "SM4");
        Cipher cipher = Cipher.getInstance(ALGORITHM_ECB, "BC");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }

    /**
     * SM4 ECB 解密.
     * @param data 密文 byte 数组
     * @param key  密钥 byte 数组（必须 16 字节）
     * @return 明文 byte 数组
     * @throws IllegalArgumentException 参数无效或密钥长度不是 16 字节
     * @throws GeneralSecurityException 解密失败
     */
    public static byte[] decryptECB(byte[] data, byte[] key) throws GeneralSecurityException {
        BCProvider.ensureInitialized();
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        if (key == null) {
            throw new IllegalArgumentException("SM4密钥不能为空");
        }
        checkKey(key);
        // 解密
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, "SM4");
            Cipher cipher = Cipher.getInstance(ALGORITHM_ECB, "BC");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            log.error("SM4 ECB 解密失败", e);
            throw new GeneralSecurityException("SM4解密失败");
        }
    }

    // ===== CBC 模式加密/解密（String 接口）=====

    /**
     * SM4 CBC 加密（使用默认 IV：16 字节全零）.
     * @param data 明文（UTF-8 编码）
     * @param key  密钥（16 字符 UTF-8 字符串）
     * @return 密文（小写 hex 编码）
     * @throws IllegalArgumentException 参数无效或密钥长度不是 16 字节
     * @throws GeneralSecurityException 加密失败
     */
    public static String encrypt(String data, String key) throws GeneralSecurityException {
        if (StringUtil.isNull(data)) {
            throw new IllegalArgumentException("加密内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("SM4密钥不能为空");
        }
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] result = encrypt(dataBytes, keyBytes);
        return StringUtil.byte2hex(result);
    }

    /**
     * SM4 CBC 加密（指定 IV）.
     * @param data 明文（UTF-8 编码）
     * @param key  密钥（16 字符 UTF-8 字符串）
     * @param iv   初始向量（16 字符 UTF-8 字符串）
     * @return 密文（小写 hex 编码）
     * @throws IllegalArgumentException 参数无效或密钥/IV 长度不是 16 字节
     * @throws GeneralSecurityException 加密失败
     */
    public static String encrypt(String data, String key, String iv) throws GeneralSecurityException {
        if (StringUtil.isNull(data)) {
            throw new IllegalArgumentException("加密内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("SM4密钥不能为空");
        }
        if (StringUtil.isNull(iv)) {
            throw new IllegalArgumentException("SM4初始向量不能为空");
        }
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] ivBytes = iv.getBytes(StandardCharsets.UTF_8);
        byte[] result = encrypt(dataBytes, keyBytes, ivBytes);
        return StringUtil.byte2hex(result);
    }

    /**
     * SM4 CBC 解密（使用默认 IV：16 字节全零）.
     * @param cipherHex 密文（hex 编码）
     * @param key       密钥（16 字符 UTF-8 字符串）
     * @return 明文（UTF-8 字符串）
     * @throws IllegalArgumentException 参数无效或密钥长度不是 16 字节
     * @throws GeneralSecurityException 解密失败
     */
    public static String decrypt(String cipherHex, String key) throws GeneralSecurityException {
        if (StringUtil.isNull(cipherHex)) {
            throw new IllegalArgumentException("解密内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("SM4密钥不能为空");
        }
        byte[] cipherBytes = StringUtil.hex2byte(cipherHex);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] result = decrypt(cipherBytes, keyBytes);
        return new String(result, StandardCharsets.UTF_8);
    }

    /**
     * SM4 CBC 解密（指定 IV）.
     * @param cipherHex 密文（hex 编码）
     * @param key       密钥（16 字符 UTF-8 字符串）
     * @param iv        初始向量（16 字符 UTF-8 字符串）
     * @return 明文（UTF-8 字符串）
     * @throws IllegalArgumentException 参数无效或密钥/IV 长度不是 16 字节
     * @throws GeneralSecurityException 解密失败
     */
    public static String decrypt(String cipherHex, String key, String iv) throws GeneralSecurityException {
        if (StringUtil.isNull(cipherHex)) {
            throw new IllegalArgumentException("解密内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("SM4密钥不能为空");
        }
        if (StringUtil.isNull(iv)) {
            throw new IllegalArgumentException("SM4初始向量不能为空");
        }
        byte[] cipherBytes = StringUtil.hex2byte(cipherHex);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] ivBytes = iv.getBytes(StandardCharsets.UTF_8);
        byte[] result = decrypt(cipherBytes, keyBytes, ivBytes);
        return new String(result, StandardCharsets.UTF_8);
    }

    // ===== CBC 模式加密/解密（byte[] 接口）=====

    /**
     * SM4 CBC 加密（使用默认 IV：16 字节全零）.
     * @param data 明文 byte 数组
     * @param key  密钥 byte 数组（必须 16 字节）
     * @return 密文 byte 数组
     * @throws IllegalArgumentException 参数无效或密钥长度不是 16 字节
     * @throws GeneralSecurityException 加密失败
     */
    public static byte[] encrypt(byte[] data, byte[] key) throws GeneralSecurityException {
        return encrypt(data, key, new byte[BLOCK_SIZE]);
    }

    /**
     * SM4 CBC 加密（指定 IV）.
     * @param data 明文 byte 数组
     * @param key  密钥 byte 数组（必须 16 字节）
     * @param iv   初始向量 byte 数组（必须 16 字节）
     * @return 密文 byte 数组
     * @throws IllegalArgumentException 参数无效或密钥/IV 长度不是 16 字节
     * @throws GeneralSecurityException 加密失败
     */
    public static byte[] encrypt(byte[] data, byte[] key, byte[] iv) throws GeneralSecurityException {
        BCProvider.ensureInitialized();
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        if (key == null) {
            throw new IllegalArgumentException("SM4密钥不能为空");
        }
        checkKey(key);
        if (iv == null) {
            throw new IllegalArgumentException("SM4初始向量不能为空");
        }
        checkIv(iv);
        // 加密
        SecretKeySpec keySpec = new SecretKeySpec(key, "SM4");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance(ALGORITHM_CBC, "BC");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }

    /**
     * SM4 CBC 解密（使用默认 IV：16 字节全零）.
     * @param data 密文 byte 数组
     * @param key  密钥 byte 数组（必须 16 字节）
     * @return 明文 byte 数组
     * @throws IllegalArgumentException 参数无效或密钥长度不是 16 字节
     * @throws GeneralSecurityException 解密失败
     */
    public static byte[] decrypt(byte[] data, byte[] key) throws GeneralSecurityException {
        return decrypt(data, key, new byte[BLOCK_SIZE]);
    }

    /**
     * SM4 CBC 解密（指定 IV）.
     * @param data 密文 byte 数组
     * @param key  密钥 byte 数组（必须 16 字节）
     * @param iv   初始向量 byte 数组（必须 16 字节）
     * @return 明文 byte 数组
     * @throws IllegalArgumentException 参数无效或密钥/IV 长度不是 16 字节
     * @throws GeneralSecurityException 解密失败
     */
    public static byte[] decrypt(byte[] data, byte[] key, byte[] iv) throws GeneralSecurityException {
        BCProvider.ensureInitialized();
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        if (key == null) {
            throw new IllegalArgumentException("SM4密钥不能为空");
        }
        checkKey(key);
        if (iv == null) {
            throw new IllegalArgumentException("SM4初始向量不能为空");
        }
        checkIv(iv);
        // 解密
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, "SM4");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM_CBC, "BC");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            log.error("SM4 CBC 解密失败", e);
            throw new GeneralSecurityException("SM4解密失败");
        }
    }

    // ===== 密钥生成 =====

    /**
     * 生成随机 SM4 密钥（hex 编码）.
     * @return 32 字符小写 hex 字符串（16 字节密钥）
     */
    public static String generateKey() {
        return StringUtil.byte2hex(generateKeyBytes());
    }

    /**
     * 生成随机 SM4 密钥（byte[] 接口）.
     * @return 16 字节密钥
     */
    public static byte[] generateKeyBytes() {
        SecureRandom random = new SecureRandom();
        byte[] key = new byte[KEY_SIZE];
        random.nextBytes(key);
        return key;
    }

    /**
     * 检测密钥长度. <br>
     * SM4 密钥长度必须为 16 字节.
     * @param key 密钥
     * @throws IllegalArgumentException 密钥长度不是 16 字节
     */
    private static void checkKey(byte[] key) {
        if (key.length != KEY_SIZE) {
            throw new IllegalArgumentException("SM4密钥长度必须为16字节");
        }
    }

    /**
     * 检测初始向量长度. <br>
     * SM4 初始向量长度必须为 16 字节.
     * @param iv 初始向量
     * @throws IllegalArgumentException 初始向量长度不是 16 字节
     */
    private static void checkIv(byte[] iv) {
        if (iv.length != BLOCK_SIZE) {
            throw new IllegalArgumentException("SM4初始向量长度必须为16字节");
        }
    }
}
