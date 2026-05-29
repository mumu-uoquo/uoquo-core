/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.crypto;

import com.uoquo.utils.StringUtil;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * 描述：SM3 哈希摘要算法工具类. <br>
 * 备注：基于 Bouncy Castle 实现，输出 256 位（32 字节）摘要，符合 GB/T 32905-2016 标准. <br>
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
public class SM3 {
    // 日志
    protected static final Logger log = LoggerFactory.getLogger(SM3.class);

    /**
     * 私有构造函数，防止实例化.
     */
    private SM3() {
    }

    /**
     * SM3 哈希摘要（String 接口）.
     * @param msg 明文字符串（UTF-8 编码）
     * @return 64 字符小写 hex 摘要
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static String hash(String msg) {
        if (StringUtil.isNull(msg)) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        BCProvider.ensureInitialized();
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        byte[] digest = hash(data);
        return StringUtil.byte2hex(digest);
    }

    /**
     * SM3 哈希摘要（byte[] 接口）.
     * @param data 数据字节数组
     * @return 32 字节摘要
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static byte[] hash(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        BCProvider.ensureInitialized();
        SM3Digest digest = new SM3Digest();
        digest.update(data, 0, data.length);
        byte[] result = new byte[digest.getDigestSize()];
        digest.doFinal(result, 0);
        return result;
    }

    /**
     * SM3 HMAC 计算（String 接口）.
     * @param msg 消息字符串（UTF-8 编码）
     * @param key 密钥字符串（UTF-8 编码）
     * @return 64 字符小写 hex HMAC 值
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static String hmac(String msg, String key) {
        if (StringUtil.isNull(msg)) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        if (StringUtil.isNull(key)) {
            throw new IllegalArgumentException("密钥不能为空");
        }
        BCProvider.ensureInitialized();
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] result = hmac(data, keyBytes);
        return StringUtil.byte2hex(result);
    }

    /**
     * SM3 HMAC 计算（byte[] 接口）.
     * @param data 消息字节数组
     * @param key  密钥字节数组
     * @return 32 字节 HMAC 值
     * @throws IllegalArgumentException 参数为 null 或空
     */
    public static byte[] hmac(byte[] data, byte[] key) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("密钥不能为空");
        }
        BCProvider.ensureInitialized();
        HMac hMac = new HMac(new SM3Digest());
        hMac.init(new KeyParameter(key));
        hMac.update(data, 0, data.length);
        byte[] result = new byte[hMac.getMacSize()];
        hMac.doFinal(result, 0);
        return result;
    }
}
