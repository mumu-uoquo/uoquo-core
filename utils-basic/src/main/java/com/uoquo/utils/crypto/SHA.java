/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.crypto;

import com.uoquo.utils.StringUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 描述：SHA 系列哈希摘要算法工具类（小写的密文字串）. <br>
 * 备注：支持 SHA-1/224/256/384/512 摘要及 HMAC-SHA256 消息认证码计算，
 * 参考 <a href="https://docs.oracle.com/en/java/javase/13/docs/specs/security/standard-names.html#messagedigest-algorithms">messagedigest-algorithms</a> <br>
 * 日期：2018-02-24 17:28 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-02-24     xuhz.           创建
 * 2.0          2025-01-01     uoquo team      增加 byte[] 接口、HMAC-SHA256 支持、输入校验、Javadoc 规范化
 * </pre>
 * @since JDK 1.8
 * @version 2.0
 * @author  uoquo team
 */
public class SHA {
    // 日志
    protected static final Logger log = LoggerFactory.getLogger(SHA.class);

    /**
     * 私有构造函数，防止工具类被实例化.
     */
    private SHA() {}

    // ===== String 接口 =====

    /**
     * SHA-1 哈希摘要（String 接口）.
     * @param msg 明文字符串（UTF-8 编码）
     * @return 40 字符小写 hex 摘要
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static String sha1(String msg) {
        return encrypt(msg, "SHA-1");
    }

    /**
     * SHA-224 哈希摘要（String 接口）.
     * @param msg 明文字符串（UTF-8 编码）
     * @return 56 字符小写 hex 摘要
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static String sha224(String msg) {
        return encrypt(msg, "SHA-224");
    }

    /**
     * SHA-256 哈希摘要（String 接口）.
     * @param msg 明文字符串（UTF-8 编码）
     * @return 64 字符小写 hex 摘要
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static String sha256(String msg) {
        return encrypt(msg, "SHA-256");
    }

    /**
     * SHA-384 哈希摘要（String 接口）.
     * @param msg 明文字符串（UTF-8 编码）
     * @return 96 字符小写 hex 摘要
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static String sha384(String msg) {
        return encrypt(msg, "SHA-384");
    }

    /**
     * SHA-512 哈希摘要（String 接口）.
     * @param msg 明文字符串（UTF-8 编码）
     * @return 128 字符小写 hex 摘要
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static String sha512(String msg) {
        return encrypt(msg, "SHA-512");
    }

    // ===== byte[] 接口 =====

    /**
     * SHA-1 哈希摘要（byte[] 接口）.
     * @param data 数据字节数组
     * @return 20 字节摘要
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static byte[] sha1(byte[] data) {
        return digestBytes(data, "SHA-1");
    }

    /**
     * SHA-224 哈希摘要（byte[] 接口）.
     * @param data 数据字节数组
     * @return 28 字节摘要
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static byte[] sha224(byte[] data) {
        return digestBytes(data, "SHA-224");
    }

    /**
     * SHA-256 哈希摘要（byte[] 接口）.
     * @param data 数据字节数组
     * @return 32 字节摘要
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static byte[] sha256(byte[] data) {
        return digestBytes(data, "SHA-256");
    }

    /**
     * SHA-384 哈希摘要（byte[] 接口）.
     * @param data 数据字节数组
     * @return 48 字节摘要
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static byte[] sha384(byte[] data) {
        return digestBytes(data, "SHA-384");
    }

    /**
     * SHA-512 哈希摘要（byte[] 接口）.
     * @param data 数据字节数组
     * @return 64 字节摘要
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static byte[] sha512(byte[] data) {
        return digestBytes(data, "SHA-512");
    }

    // ===== HMAC =====

    /**
     * HMAC-SHA256 计算（String 接口）.
     * @param msg 消息字符串（UTF-8 编码）
     * @param key 密钥字符串（UTF-8 编码）
     * @return 64 字符小写 hex HMAC 值
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static String hmacSha256(String msg, String key) {
        if (StringUtil.isNull(msg)) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("密钥不能为空");
        }
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] result = hmacSha256(data, keyBytes);
        return StringUtil.byte2hex(result);
    }

    /**
     * HMAC-SHA256 计算（byte[] 接口）.
     * @param data 消息字节数组
     * @param key  密钥字节数组
     * @return 32 字节 HMAC 值
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static byte[] hmacSha256(byte[] data, byte[] key) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("密钥不能为空");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
            mac.init(secretKeySpec);
            return mac.doFinal(data);
        } catch (Exception e) {
            log.error("HMAC-SHA256计算出错", e);
            throw new IllegalStateException("HMAC-SHA256算法不可用", e);
        }
    }

    // ===== 内部方法 =====

    /**
     * SHA 哈希摘要内部计算（String 接口）.
     * @param msg       明文字符串（UTF-8 编码）
     * @param algorithm 算法模式（SHA-1/SHA-224/SHA-256/SHA-384/SHA-512）
     * @return 小写 hex 摘要字符串
     * @throws IllegalArgumentException 参数为 null 或空
     * @throws IllegalStateException    算法不可用
     */
    private static String encrypt(String msg, String algorithm) {
        if (StringUtil.isNull(msg)) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        byte[] digest = digestBytes(data, algorithm);
        return StringUtil.byte2hex(digest);
    }

    /**
     * SHA 哈希摘要内部计算（byte[] 接口）.
     * @param data      数据字节数组
     * @param algorithm 算法模式（SHA-1/SHA-224/SHA-256/SHA-384/SHA-512）
     * @return 摘要字节数组
     * @throws IllegalArgumentException 参数为 null 或空
     * @throws IllegalStateException    算法不可用
     */
    private static byte[] digestBytes(byte[] data, String algorithm) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            digest.update(data);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA计算出错, 算法: {}", algorithm, e);
            throw new IllegalStateException("SHA算法不可用: " + algorithm, e);
        }
    }
}
