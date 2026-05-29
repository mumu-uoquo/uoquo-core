/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.crypto;

import com.uoquo.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * OTP（OneTimePassword）
 * 获取一次性密码（动态令牌）
 * 核心使用 MAC（Message Authentication Code） 来计算认证信息
 * <ul>
 *     <li>TOTP：Time-Based One-Time Password，RFC6238，时间因子，支持 HMAC-SHA-1 / HMAC-SHA256 / HMAC-SHA512 算法</li>
 *     <li>HOTP：HMAC-Based One-Time Password，RFC4226，事件因子（计数器），支持 HMAC-SHA-1 算法</li>
 * </ul>
 */
public class OTPUtils {
    protected static final Logger log = LoggerFactory.getLogger(OTPUtils.class);

    /**
     * 用于生成字母和数组组合的验证码（去除了 01 和 AEILOSUZ 这些容易混淆的数据）
     */
    private static final char[] STEAMCHARS = new char[] {
            '2', '3', '4', '5', '6', '7', '8', '9', 'B', 'C',
            'D', 'F', 'G', 'H', 'J', 'K', 'M', 'N', 'P', 'Q',
            'R', 'T', 'V', 'W', 'X', 'Y'
    };

    /**
     * 支持的算法
     */
    public enum HashAlgorithm {
        HmacSHA1, HmacSHA256, HmacSHA512
    }

    /**
     * 默认算法
     */
    public static final HashAlgorithm DEFAULT_ALGORITHM = HashAlgorithm.HmacSHA1;

    /**
     * 默认时间跨度（秒）
     */
    public static final int TOTP_DEFAULT_PERIOD = 30;

    /**
     * 计算消息的HASH内容（HmacSHA1）
     * @param key  秘钥
     * @param data 内容
     * @return 20字节的hash值
     */
    public static byte[] generateHash(byte[] key, byte[] data)
            throws NoSuchAlgorithmException, InvalidKeyException {
        return generateHash(DEFAULT_ALGORITHM, key, data);
    }

    /**
     * 计算消息的HASH内容<br>
     * 可以根据返回的字节内容自己处理完Base32等格式的内容
     * @param algorithm 算法
     * @param key  秘钥
     * @param data 内容
     * @return 20字节的hash值
     */
    public static byte[] generateHash(HashAlgorithm algorithm, byte[] key, byte[] data)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(algorithm.toString());
        mac.init(new SecretKeySpec(key, algorithm.toString()));
        return mac.doFinal(data);
    }

    /**
     * TOTP（Time-Based One-Time Password）
     * @param secret 秘钥
     * @param digits 生成位数
     * @return 纯数字
     */
    public static String TOTP(byte[] secret, int digits) {
        return TOTP(secret, TOTP_DEFAULT_PERIOD, System.currentTimeMillis() / 1000, digits, DEFAULT_ALGORITHM);
    }

    /**
     * TOTP（Time-Based One-Time Password）
     * @param secret 秘钥
     * @param digits 生成位数
     * @return 动态令牌（含大写字母和数字）
     */
    public static String TOTPSteam(byte[] secret, int digits) {
        return TOTPSteam(secret, TOTP_DEFAULT_PERIOD, System.currentTimeMillis() / 1000, digits, DEFAULT_ALGORITHM);
    }

    /**
     * TOTP（Time-Based One-Time Password）
     * @param secret  秘钥
     * @param period  有效时长（秒，如：3分钟）
     * @param time    时间戳（秒，如：当前时间）
     * @param digits  生成位数
     * @param algorithm 所用算法（如：HmacSHA1）
     * @return 纯数字
     */
    public static String TOTP(byte[] secret, int period, long time, int digits, HashAlgorithm algorithm) {
        int fullToken = OTP(secret, (time / period), algorithm);
        int div = (int) Math.pow(10, digits);
        return formatNumber2String(fullToken % div, digits);
    }

    /**
     * TOTP（Time-Based One-Time Password）
     * @param secret  秘钥
     * @param period  有效时长（秒，如：3分钟）
     * @param time    时间戳（秒，如：当前时间）
     * @param digits  生成位数
     * @param algorithm 所用算法（如：HmacSHA1）
     * @return 动态令牌（含大写字母和数字）
     */
    public static String TOTPSteam(byte[] secret, int period, long time, int digits, HashAlgorithm algorithm) {
        int fullToken = OTP(secret, (time / period), algorithm);
        return formatNumber2Steam(fullToken, digits);
    }

    /**
     * HOTP（HMAC-Based One-Time Password）
     * @return 纯数字
     */
    public static String HOTP(byte[] secret, int counter, int digits) {
        int fullToken = OTP(secret, counter, HashAlgorithm.HmacSHA1);
        int div = (int) Math.pow(10, digits);
        return formatNumber2String(fullToken % div, digits);
    }

    /**
     * HOTP（HMAC-Based One-Time Password）
     * @return 动态令牌（含大写字母和数字）
     */
    public static String HOTPSteam(byte[] secret, int counter, int digits) {
        int fullToken = OTP(secret, counter, HashAlgorithm.HmacSHA1);
        return formatNumber2Steam(fullToken, digits);
    }

    /**
     * 计算OTP值
     */
    private static int OTP(byte[] secret, long counter, HashAlgorithm algorithm) {
        int r = 0;
        try {
            // 1. 计算hash内容（长度：20）
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            byte[] hash = generateHash(algorithm, secret, data);
            // 2. 截位
            // 以最后字节作为偏移量（范围：0 ~ 15）
            int offset = hash[hash.length - 1] & 0xF;
            // 截取hash中的4字节组成int
            int binary = (hash[offset]  & 0x7F) << 24;
            binary |= (hash[offset + 1] & 0xFF) << 16;
            binary |= (hash[offset + 2] & 0xFF) << 8;
            binary |= (hash[offset + 3] & 0xFF);
            r = binary;
        } catch (Exception e) {
            log.error("计算OTP值失败: secret={}, counter={}, algorithm={}", StringUtil.byte2hex(secret), counter, algorithm, e);
        }
        return r;
    }

    /**
     * 将数字格式化（纯数字）
     */
    private static String formatNumber2String(int token, int digits) {
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.ENGLISH);
        numberFormat.setMinimumIntegerDigits(digits);
        numberFormat.setGroupingUsed(false);
        return numberFormat.format(token);
    }

    /**
     * 将数字格式化（大写字母+数字）
     */
    private static String formatNumber2Steam(int token, int digits) {
        StringBuilder tokenBuilder = new StringBuilder();
        for (int i = 0; i < digits; i++) {
            tokenBuilder.append(STEAMCHARS[token % STEAMCHARS.length]);
            token /= STEAMCHARS.length;
        }
        return tokenBuilder.toString();
    }
}
