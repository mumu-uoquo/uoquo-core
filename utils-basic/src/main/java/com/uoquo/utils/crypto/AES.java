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
 * 描述：AES 对称加解密算法. <br>
 * 备注：本工具类默认采用CBC模式，初始向量为new byte[16]
 * <ul>
 *   <li>标准模式：AES/CBC/PKCS5Padding，加密后的长度16的整数倍</li>
 *   <li>固长模式：AES/CBC/NoPadding，超过16整数倍的部分不加密，优点：加密前和加密后的长度一致</li>
 * </ul>
 * 说明：
 * <ul>
 *   <li>标准AES加密后的字节（byte）长度为16的整数倍</li>
 *   <li>AES支持的密钥长度：128位（16字符），192位（24字符），256位（32字符）</li>
 *   <li>工作模式：
 *     <ul>
 *       <li>ECB：电子密码本模式，加解密均可并行，但相同明文对应的密文也相同</li>
 *       <li>CBC：分组链接模式，安全性高，可并行解密，但不能并行加密</li>
 *     </ul>
 *   </li>
 *   <li>补码方式：
 *     <ul>
 *       <li>NoPadding：不填充，明文字节长度必须为16的整数倍</li>
 *       <li>PKCS5Padding：块大小为128byte（即16字节），假设数据长度需要填充n(n>0)个字节才对齐，那么填充n个字节，每个字节都是n;如果数据本身就已经对齐了，则填充一块长度为块大小的数据，每个字节都是块大小</li>
 *     </ul>
 *   </li>
 * </ul>
 * 日期：2018-02-24 18:22 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-02-24     xuhz.           创建
 * 1.1          2019-05-30     xuhz.           增加固长模式
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class AES {
    // 日志
    protected static final Logger log = LoggerFactory.getLogger(AES.class);
    
    /**
     * 加密块大小.
     */
    private static final int BLOCK_SIZE = 16; // 16byte（即：128bit）

    /**
     * 私有构造函数，防止工具类被实例化.
     */
    private AES() {}


    /**
     * 加密算法（标准）.
     */
    public static final String ALGORITHM_CBC = "AES/CBC/PKCS5Padding"; // 算法/模式/补码方式

    /**
     * 加密算法（ECB）.
     */
    public static final String ALGORITHM_ECB = "AES/ECB/PKCS5Padding";

    /**
     * 加密算法（固定长度）.
     */
    public static final String ALGORITHM_FIXED = "AES/CBC/NoPadding";
    
    /**
     * AES CBC 加密（使用默认 IV：16 字节全零）.
     * @param dcr 明文字符串（UTF-8 编码）
     * @param key 密钥字符串（UTF-8 编码，长度必须为 16、24 或 32 字符）
     * @return 密文（小写 hex 编码）
     * @throws IllegalArgumentException 明文或密钥为 null 或空字符串，或密钥长度不合法
     * @throws GeneralSecurityException 加密过程中发生错误
     */
    public static String encrypt(String dcr, String key) throws GeneralSecurityException {
        if (StringUtil.isNull(dcr)) {
            throw new IllegalArgumentException("加密内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("AES KEY不能为空");
        }
        byte[] dcrb = dcr.getBytes(StandardCharsets.UTF_8);
        byte[] keys = key.getBytes(StandardCharsets.UTF_8);
        byte[] data = encrypt(dcrb, keys);
        return StringUtil.byte2hex(data);
    }

    /**
     * AES CBC 解密（使用默认 IV：16 字节全零）.
     * @param ecr 密文字符串（小写 hex 编码）
     * @param key 密钥字符串（UTF-8 编码，长度必须为 16、24 或 32 字符）
     * @return 明文（UTF-8 字符串）
     * @throws IllegalArgumentException 密文或密钥为 null 或空字符串，或密钥长度不合法
     * @throws GeneralSecurityException 解密过程中发生错误
     */
    public static String decrypt(String ecr, String key) throws GeneralSecurityException {
        if (StringUtil.isNull(ecr)) {
            throw new IllegalArgumentException("解密内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("AES KEY不能为空");
        }
        byte[] ecpt = StringUtil.hex2byte(ecr);
        byte[] keys = key.getBytes(StandardCharsets.UTF_8);
        byte[] data = decrypt(ecpt, keys);
        return new String(data, StandardCharsets.UTF_8);
    }
    
    /**
     * AES CBC 加密（指定 IV）.
     * @param data 明文字符串（UTF-8 编码）
     * @param key  密钥字符串（UTF-8 编码，长度必须为 16、24 或 32 字符）
     * @param iv   初始向量字符串（UTF-8 编码，长度必须为 16 字符）
     * @return 密文（小写 hex 编码）
     * @throws IllegalArgumentException 明文或密钥为 null 或空字符串，或 IV 为 null，或密钥长度不合法，或 IV 长度不是 16 字节
     * @throws GeneralSecurityException 加密过程中发生错误
     */
    public static String encrypt(String data, String key, String iv) throws GeneralSecurityException {
        if (StringUtil.isNull(data)) {
            throw new IllegalArgumentException("加密内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("AES KEY不能为空");
        }
        if (iv == null) {
            throw new IllegalArgumentException("初始向量不能为空");
        }
        byte[] ivBytes = iv.getBytes(StandardCharsets.UTF_8);
        checkIv(ivBytes);
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        checkKey(keyBytes);
        // 加密
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
        Cipher cipher = Cipher.getInstance(ALGORITHM_CBC);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(dataBytes);
        return StringUtil.byte2hex(encrypted);
    }

    /**
     * AES CBC 解密（指定 IV）.
     * @param cipherHex 密文字符串（小写 hex 编码）
     * @param key       密钥字符串（UTF-8 编码，长度必须为 16、24 或 32 字符）
     * @param iv        初始向量字符串（UTF-8 编码，长度必须为 16 字符）
     * @return 明文（UTF-8 字符串）
     * @throws IllegalArgumentException 密文或密钥为 null 或空字符串，或 IV 为 null，或密钥长度不合法，或 IV 长度不是 16 字节
     * @throws GeneralSecurityException 解密过程中发生错误
     */
    public static String decrypt(String cipherHex, String key, String iv) throws GeneralSecurityException {
        if (StringUtil.isNull(cipherHex)) {
            throw new IllegalArgumentException("解密内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("AES KEY不能为空");
        }
        if (iv == null) {
            throw new IllegalArgumentException("初始向量不能为空");
        }
        byte[] ivBytes = iv.getBytes(StandardCharsets.UTF_8);
        checkIv(ivBytes);
        byte[] dataBytes = StringUtil.hex2byte(cipherHex);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        checkKey(keyBytes);
        // 解密
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
        Cipher cipher = Cipher.getInstance(ALGORITHM_CBC);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decrypted = cipher.doFinal(dataBytes);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * AES CBC 加密（指定 IV，byte[] 接口）.
     * @param data 明文 byte 数组，不能为 null 或空数组
     * @param key  密钥 byte 数组（长度必须为 16、24 或 32 字节）
     * @param iv   初始向量 byte 数组（长度必须为 16 字节）
     * @return 密文 byte 数组
     * @throws IllegalArgumentException 数据为 null 或空数组，或密钥为 null 或长度不合法，或 IV 为 null 或长度不是 16 字节
     * @throws GeneralSecurityException 加密过程中发生错误
     */
    public static byte[] encrypt(byte[] data, byte[] key, byte[] iv) throws GeneralSecurityException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        if (key == null) {
            throw new IllegalArgumentException("密钥不能为空");
        }
        checkKey(key);
        checkIv(iv);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance(ALGORITHM_CBC);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }

    /**
     * AES CBC 解密（指定 IV，byte[] 接口）.
     * @param data 密文 byte 数组，不能为 null 或空数组
     * @param key  密钥 byte 数组（长度必须为 16、24 或 32 字节）
     * @param iv   初始向量 byte 数组（长度必须为 16 字节）
     * @return 明文 byte 数组
     * @throws IllegalArgumentException 数据为 null 或空数组，或密钥为 null 或长度不合法，或 IV 为 null 或长度不是 16 字节
     * @throws GeneralSecurityException 解密过程中发生错误
     */
    public static byte[] decrypt(byte[] data, byte[] key, byte[] iv) throws GeneralSecurityException {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        if (key == null) {
            throw new IllegalArgumentException("密钥不能为空");
        }
        checkKey(key);
        checkIv(iv);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance(ALGORITHM_CBC);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }

    /**
     * AES CBC 加密（使用默认 IV：16 字节全零，byte[] 接口）.
     * @param data 明文 byte 数组，不能为 null 或空数组
     * @param keys 密钥 byte 数组（长度必须为 16、24 或 32 字节）
     * @return 密文 byte 数组
     * @throws IllegalArgumentException 数据或密钥为 null，或数据为空数组，或密钥长度不合法
     * @throws GeneralSecurityException 加密过程中发生错误
     */
    public static byte[] encrypt(byte[] data, byte[] keys) throws GeneralSecurityException {
        return encrypt(data, keys, ALGORITHM_CBC);
    }

    /**
     * AES CBC 解密（使用默认 IV：16 字节全零，byte[] 接口）.
     * @param data 密文 byte 数组，不能为 null 或空数组
     * @param keys 密钥 byte 数组（长度必须为 16、24 或 32 字节）
     * @return 明文 byte 数组
     * @throws IllegalArgumentException 数据或密钥为 null，或数据为空数组，或密钥长度不合法
     * @throws GeneralSecurityException 解密过程中发生错误
     */
    public static byte[] decrypt(byte[] data, byte[] keys) throws GeneralSecurityException {
        return decrypt(data, keys, ALGORITHM_CBC);
    }
    
    /**
     * 执行加密.
     * @param data 明文byte数组
     * @param keys 密钥byte数组
     * @param algorithm 算法模式
     * @return 密文byte数组
     */
    private static byte[] encrypt(byte[] data, byte[] keys, String algorithm) throws GeneralSecurityException {
        // 校验密钥
        if (data == null || keys == null) {
            throw new IllegalArgumentException("数据内容及密钥不可以为空");
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        checkKey(keys);
        // 加密算法
        SecretKeySpec keySpec = new SecretKeySpec(keys, "AES");
        Cipher cipher = Cipher.getInstance(algorithm);
        if (ALGORITHM_ECB.equals(algorithm)) {
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        } else {
            IvParameterSpec ivpSpec = new IvParameterSpec(new byte[BLOCK_SIZE]); // 初始向量，ECB模式时不需要
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivpSpec);
        }
        // 加密数据
        return cipher.doFinal(data);
    }
    
    /**
     * 执行解密.
     * @param data 密文byte数组
     * @param keys 密钥byte数组
     * @param algorithm 算法模式
     * @return 明文byte数组
     */
    private static byte[] decrypt(byte[] data, byte[] keys, String algorithm) throws GeneralSecurityException {
        // 检测密钥
        if (data == null || keys == null) {
            throw new IllegalArgumentException("数据内容及密钥不可以为空");
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        checkKey(keys);
        // 解密算法
        SecretKeySpec keySpec = new SecretKeySpec(keys, "AES");
        Cipher cipher = Cipher.getInstance(algorithm);
        if (ALGORITHM_ECB.equals(algorithm)) {
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
        } else {
            IvParameterSpec ivpSpec = new IvParameterSpec(new byte[BLOCK_SIZE]); // 初始向量，ECB模式时不需要
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivpSpec);
        }
        // 解密数据
        return cipher.doFinal(data);
    }
    
    /**
     * AES ECB 加密.
     * @param dcr 明文字符串（UTF-8 编码）
     * @param key 密钥字符串（UTF-8 编码，长度必须为 16、24 或 32 字符）
     * @return 密文（小写 hex 编码）
     * @throws IllegalArgumentException 明文或密钥为 null 或空字符串，或密钥长度不合法
     * @throws GeneralSecurityException 加密过程中发生错误
     */
    public static String encryptECB(String dcr, String key) throws GeneralSecurityException {
        if (StringUtil.isNull(dcr)) {
            throw new IllegalArgumentException("加密内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("AES KEY不能为空");
        }
        byte[] dcrb = dcr.getBytes(StandardCharsets.UTF_8);
        byte[] keys = key.getBytes(StandardCharsets.UTF_8);
        byte[] data = encryptECB(dcrb, keys);
        return StringUtil.byte2hex(data);
    }

    /**
     * AES ECB 解密.
     * @param ecr 密文字符串（小写 hex 编码）
     * @param key 密钥字符串（UTF-8 编码，长度必须为 16、24 或 32 字符）
     * @return 明文（UTF-8 字符串）
     * @throws IllegalArgumentException 密文或密钥为 null 或空字符串，或密钥长度不合法
     * @throws GeneralSecurityException 解密过程中发生错误
     */
    public static String decryptECB(String ecr, String key) throws GeneralSecurityException {
        if (StringUtil.isNull(ecr)) {
            throw new IllegalArgumentException("解密内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("AES KEY不能为空");
        }
        byte[] ecpt = StringUtil.hex2byte(ecr);
        byte[] keys = key.getBytes(StandardCharsets.UTF_8);
        byte[] data = decryptECB(ecpt, keys);
        return new String(data, StandardCharsets.UTF_8);
    }
    
    /**
     * AES ECB 加密（byte[] 接口）.
     * @param data 明文 byte 数组，不能为 null 或空数组
     * @param keys 密钥 byte 数组（长度必须为 16、24 或 32 字节）
     * @return 密文 byte 数组
     * @throws IllegalArgumentException 数据或密钥为 null，或数据为空数组，或密钥长度不合法
     * @throws GeneralSecurityException 加密过程中发生错误
     */
    public static byte[] encryptECB(byte[] data, byte[] keys) throws GeneralSecurityException {
        return encrypt(data, keys, ALGORITHM_ECB);
    }
    
    /**
     * AES ECB 解密（byte[] 接口）.
     * @param data 密文 byte 数组，不能为 null 或空数组
     * @param keys 密钥 byte 数组（长度必须为 16、24 或 32 字节）
     * @return 明文 byte 数组
     * @throws IllegalArgumentException 数据或密钥为 null，或数据为空数组，或密钥长度不合法
     * @throws GeneralSecurityException 解密过程中发生错误
     */
    public static byte[] decryptECB(byte[] data, byte[] keys) throws GeneralSecurityException {
        return decrypt(data, keys, ALGORITHM_ECB);
    }

    /**
     * AES CBC 固长加密（使用默认 IV：16 字节全零，NoPadding 模式）.
     * @param dcr 明文字符串（UTF-8 编码，字节长度必须大于等于 16）
     * @param key 密钥字符串（UTF-8 编码，长度必须为 16、24 或 32 字符）
     * @return 密文（小写 hex 编码，长度与明文一致）
     * @throws IllegalArgumentException 明文或密钥为 null 或空字符串，或密钥长度不合法，或明文字节长度不足 16
     * @throws GeneralSecurityException 加密过程中发生错误
     */
    public static String encryptFixed(String dcr, String key) throws GeneralSecurityException {
        if (StringUtil.isNull(dcr)) {
            throw new IllegalArgumentException("解密内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("AES KEY不能为空");
        }
        byte[] dcrb = dcr.getBytes(StandardCharsets.UTF_8);
        byte[] keys = key.getBytes(StandardCharsets.UTF_8);
        byte[] data = encryptFixed(dcrb, keys);
        return StringUtil.byte2hex(data);
    }

    /**
     * AES CBC 固长解密（使用默认 IV：16 字节全零，NoPadding 模式）.
     * @param ecr 密文字符串（小写 hex 编码）
     * @param key 密钥字符串（UTF-8 编码，长度必须为 16、24 或 32 字符）
     * @return 明文（UTF-8 字符串）
     * @throws IllegalArgumentException 密文或密钥为 null 或空字符串，或密钥长度不合法，或密文字节长度不足 16
     * @throws GeneralSecurityException 解密过程中发生错误
     */
    public static String decryptFixed(String ecr, String key) throws GeneralSecurityException {
        if (StringUtil.isNull(ecr)) {
            throw new IllegalArgumentException("解密内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("AES KEY不能为空");
        }
        byte[] ecpt = StringUtil.hex2byte(ecr);
        byte[] keys = key.getBytes(StandardCharsets.UTF_8);
        byte[] data = decryptFixed(ecpt, keys);
        return new String(data, StandardCharsets.UTF_8);
    }
    
    /**
     * AES CBC 固长加密（使用默认 IV：16 字节全零，NoPadding 模式，byte[] 接口）.
     * @param data 明文 byte 数组，长度必须大于等于 16 字节
     * @param keys 密钥 byte 数组（长度必须为 16、24 或 32 字节）
     * @return 密文 byte 数组（长度与明文一致，仅 16 整数倍部分被加密）
     * @throws IllegalArgumentException 数据或密钥为 null，或数据为空数组，或密钥长度不合法，或数据长度不足 16 字节
     * @throws GeneralSecurityException 加密过程中发生错误
     */
    public static byte[] encryptFixed(byte[] data, byte[] keys) throws GeneralSecurityException {
        // 检测密钥
        if (data == null || keys == null) {
            throw new IllegalArgumentException("数据内容及密钥不可以为空");
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        checkKey(keys);
        // 数据加密
        int len = data.length;
        int mod = len % BLOCK_SIZE;
        if (len == mod) {
            // 不足16位，无法加密
            throw new IllegalArgumentException("固长模式的加密数据长度必须大于等于16字节");
        } else if (mod == 0) {
            // 长度位16的整数倍，则整个加密
            return encrypt(data, keys, ALGORITHM_FIXED);
        } else {
            // 只加密长度位16整数倍的部分
            byte[] d1 = new byte[len - mod];
            System.arraycopy(data, 0, d1, 0, d1.length);
            byte[] d2 = encrypt(d1, keys, ALGORITHM_FIXED);
            // 不足16位的，不加密
            System.arraycopy(d2, 0, data, 0, d2.length);
            return data;
        }
    }
    
    /**
     * AES CBC 固长解密（使用默认 IV：16 字节全零，NoPadding 模式，byte[] 接口）.
     * @param data 密文 byte 数组，长度必须大于等于 16 字节
     * @param keys 密钥 byte 数组（长度必须为 16、24 或 32 字节）
     * @return 明文 byte 数组（长度与密文一致，仅 16 整数倍部分被解密）
     * @throws IllegalArgumentException 数据或密钥为 null，或数据为空数组，或密钥长度不合法，或数据长度不足 16 字节
     * @throws GeneralSecurityException 解密过程中发生错误
     */
    public static byte[] decryptFixed(byte[] data, byte[] keys) throws GeneralSecurityException {
        // 检测密钥
        if (data == null || keys == null) {
            throw new IllegalArgumentException("数据内容及密钥不可以为空");
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        checkKey(keys);
        // 数据解密
        int len = data.length;
        int mod = len % BLOCK_SIZE;
        if (len == mod) {
            // 不足16位，无法解密
            throw new IllegalArgumentException("固长模式的解密数据长度必须大于等于16字节");
        } else if (mod == 0) {
            // 长度位16的整数倍，则整个解密
            return decrypt(data, keys, ALGORITHM_FIXED);
        } else {
            // 只解密长度位16整数倍的部分
            byte[] d1 = new byte[len - mod];
            System.arraycopy(data, 0, d1, 0, d1.length);
            byte[] d2 = decrypt(d1, keys, ALGORITHM_FIXED);
            // 不足16位的，不解密
            System.arraycopy(d2, 0, data, 0, d2.length);
            return data;
        }
    }
    
    /**
     * 检测密钥. <br>
     * @param keys 密钥
     */
    private static void checkKey(byte[] keys) {
        if ((keys.length != 16) && (keys.length != 24) && (keys.length != 32)) {
            throw new IllegalArgumentException("AES KEY长度必须是16，24，32");
        }
    }

    /**
     * 检测初始向量. <br>
     * @param iv 初始向量
     * @throws IllegalArgumentException IV 为 null 或长度不为 16 字节
     */
    private static void checkIv(byte[] iv) {
        if (iv == null || iv.length != BLOCK_SIZE) {
            throw new IllegalArgumentException("初始向量长度必须为16字节");
        }
    }
    
    // ===== 密钥生成 =====

    /**
     * 生成随机 AES 密钥（默认 128 位，hex 编码）.
     * @return 32 字符小写 hex 字符串（对应 128 位即 16 字节密钥）
     * @see #generateKey(int)
     */
    public static String generateKey() {
        return generateKey(128);
    }

    /**
     * 生成随机 AES 密钥（指定位数，hex 编码）.
     * @param bits 密钥位数，仅支持 128、192、256
     * @return 小写 hex 字符串（128 位返回 32 字符，192 位返回 48 字符，256 位返回 64 字符）
     * @throws IllegalArgumentException 密钥位数不是 128、192 或 256
     */
    public static String generateKey(int bits) {
        byte[] keyBytes = generateKeyBytes(bits);
        return StringUtil.byte2hex(keyBytes);
    }

    /**
     * 生成随机 AES 密钥（byte[] 接口）.
     * @param bits 密钥位数，仅支持 128、192、256
     * @return 对应长度的 byte 数组（128 位返回 16 字节，192 位返回 24 字节，256 位返回 32 字节）
     * @throws IllegalArgumentException 密钥位数不是 128、192 或 256
     */
    public static byte[] generateKeyBytes(int bits) {
        if (bits != 128 && bits != 192 && bits != 256) {
            throw new IllegalArgumentException("AES密钥位数必须是128、192或256");
        }
        byte[] key = new byte[bits / 8];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
