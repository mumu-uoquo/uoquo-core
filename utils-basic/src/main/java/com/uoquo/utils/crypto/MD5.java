/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.crypto;

import com.uoquo.utils.FileUtil;
import com.uoquo.utils.StringUtil;

import java.io.File;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MD5 哈希摘要工具类.
 * <p>提供 MD5 哈希摘要计算功能，支持字符串、字节数组和文件的 MD5 计算。</p>
 * <p>备注：MD5 已不推荐用于安全场景，仅适用于数据完整性校验。</p>
 *
 * <table border="1">
 *   <caption>变更记录</caption>
 *   <tr><th>版本</th><th>日期</th><th>描述</th></tr>
 *   <tr><td>1.0</td><td>2018-01-29</td><td>基础 MD5 计算</td></tr>
 *   <tr><td>2.0</td><td>优化版本</td><td>统一异常处理、输入校验、修复加盐 Bug</td></tr>
 * </table>
 *
 * @author uoquo team
 * @version 2.0
 * @since 1.0
 */
public class MD5 {
    protected static final Logger log = LoggerFactory.getLogger(MD5.class);

    /**
     * 私有构造函数，防止实例化.
     */
    private MD5() {}

    /**
     * MD5 哈希摘要（加盐）.
     * <p>当 salt 不为 null 且不为空时，将 salt 拼接到 src 末尾后计算 MD5(src + salt)；
     * 当 salt 为 null 或空时，仅对 src 计算 MD5。</p>
     *
     * @param src  明文字符串（UTF-8 编码），不能为 null 或空
     * @param salt 盐值字符串（可为 null，为 null 或空时不加盐）
     * @return 32 字符小写 hex 摘要字符串
     * @throws IllegalArgumentException 当 src 为 null 或空字符串时
     * @throws IllegalStateException    当 MD5 算法不可用或摘要计算异常时
     */
    public static String encrypt(String src, String salt) {
        if (StringUtil.isNull(src)) {
            throw new IllegalArgumentException("原始数据不能为空");
        }
        if (StringUtil.notNull(salt)) {
            src += salt;
        }
        return encrypt(src);
    }
    
    /**
     * MD5 哈希摘要（String 接口）.
     * <p>对明文字符串进行 MD5 哈希计算，返回 32 字符小写十六进制摘要。</p>
     *
     * @param src 明文字符串（UTF-8 编码），不能为 null 或空
     * @return 32 字符小写 hex 摘要字符串
     * @throws IllegalArgumentException 当 src 为 null 或空字符串时
     * @throws IllegalStateException    当 MD5 算法不可用或摘要计算异常时
     */
    public static String encrypt(String src) {
        if (StringUtil.isNull(src)) {
            throw new IllegalArgumentException("原始数据不能为空");
        }
        // 加密
        byte[] mess = src.getBytes(StandardCharsets.UTF_8);
        return encrypt(mess);
    }
    
    /**
     * MD5 哈希摘要（byte[] 接口）.
     * <p>对字节数组进行 MD5 哈希计算，返回 32 字符小写十六进制摘要。</p>
     *
     * @param mess 数据字节数组，不能为 null 或空数组
     * @return 32 字符小写 hex 摘要字符串
     * @throws IllegalArgumentException 当 mess 为 null 或空数组（length == 0）时
     * @throws IllegalStateException    当 MD5 算法不可用或摘要计算异常时
     */
    public static String encrypt(byte[] mess) {
        if (mess == null || mess.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(mess);
            return StringUtil.byte2hex(hash);
        } catch (Exception e) {
            log.error("[{}]的MD5计算出错.", mess, e);
            throw new IllegalStateException("MD5摘要计算失败", e);
        }
    }
    
    /**
     * 对文件完整内容计算 MD5.
     * <p>读取文件全部内容进行 MD5 计算，不加盐，不加入文件长度。</p>
     *
     * @param filePath 文件绝对路径或相对路径
     * @return 32 字符小写 hex 摘要字符串
     * @throws IllegalArgumentException 当文件不存在、不是有效文件、不可读或为空文件时
     * @throws IllegalStateException    当 MD5 算法不可用或摘要计算异常时
     */
    public static String encryptFileAll(String filePath) {
        File file = new File(filePath);
        return encryptFile(file, null, 1, file.length(), true);
    }
    
    /**
     * 对文件分段计算 MD5（默认策略）.
     * <p>策略：将文件分为 1000 段，每段获取 100 字节（大概 97K 数据），
     * 并将文件长度和盐值一同参与计算。</p>
     *
     * @param filePath 文件绝对路径或相对路径
     * @param salt     盐值字符串（可为 null，为 null 时不加盐）
     * @return 32 字符小写 hex 摘要字符串
     * @throws IllegalArgumentException 当文件不存在、不是有效文件、不可读或为空文件时
     * @throws IllegalStateException    当 MD5 算法不可用或摘要计算异常时
     */
    public static String encryptFile(String filePath, String salt) {
        return encryptFile(new File(filePath), salt, 1000, 100, false);
    }
    
    /**
     * 对文件分段计算 MD5（自定义分段参数）.
     * <p>原理：将文件分为 part 段，每段获取 len 字节，将组合后的字节数组进行 MD5 计算。
     * 策略：将文件分段后，从后往前读取每段开始的 len 字节进行计算，
     * 并将文件长度和盐值一同参与计算。</p>
     *
     * @param filePath 文件绝对路径或相对路径
     * @param salt     盐值字符串（可为 null，为 null 时不加盐）
     * @param part     分段个数（小于等于 0 时默认为 1000）
     * @param len      每段读取的字节数（小于等于 0 时默认为 100）
     * @return 32 字符小写 hex 摘要字符串
     * @throws IllegalArgumentException 当文件不存在、不是有效文件、不可读或为空文件时
     * @throws IllegalStateException    当 MD5 算法不可用或摘要计算异常时
     */
    public static String encryptFile(String filePath, String salt, int part, long len) {
        return encryptFile(new File(filePath), salt, part, len, false);
    }
    
    /**
     * 对文件分段计算 MD5（完整参数版本）.
     * <p>原理：将文件分为 part 段，每段获取 len 字节，将组合后的字节数组进行 MD5 计算。
     * 策略：将文件分段后，从后往前读取每段开始的 len 字节进行计算。</p>
     *
     * @param file     目标文件对象
     * @param salt     盐值字符串（可为 null，为 null 时不加盐）
     * @param part     分段个数（小于等于 0 时默认为 1000）
     * @param len      每段读取的字节数（小于等于 0 时默认为 100）
     * @param onlyData 是否仅数据参与计算（true：仅数据内容参与计算；false：数据内容及文件长度均参与计算）
     * @return 32 字符小写 hex 摘要字符串
     * @throws IllegalArgumentException 当文件不存在、不是有效文件、不可读、为空文件或读取文件出错时
     * @throws IllegalStateException    当 MD5 算法不可用或摘要计算异常时
     */
    public static String encryptFile(File file, String salt, int part, long len, boolean onlyData) {
        // 参数合法化
        part = (part <= 0) ? 1000 : part;
        len  = (len  <= 0) ? 100  : len;
        if (!file.exists()) {
            throw new IllegalArgumentException(String.format("[%s]不存在", file.getAbsolutePath()));
        } else if (!file.isFile()) {
            throw new IllegalArgumentException(String.format("[%s]不是有效文件，无法计算MD5", file.getAbsolutePath()));
        } else if (!file.canRead()) {
            throw new IllegalArgumentException(String.format("[%s]不是可读文件，无法计算MD5", file.getAbsolutePath()));
        } else if (file.length() == 0) {
            throw new IllegalArgumentException(String.format("[%s]为空文件，无法计算MD5", file.getAbsolutePath()));
        }
        // 初始化每组长度
        int capacity = onlyData ? 0 : 8; // 计算文件最终的字节数（初始为文件大小所占字节数）
        if (StringUtil.notNull(salt)) {
            capacity += salt.getBytes(StandardCharsets.UTF_8).length;
        }
        // 精度处理！！（注：需要注意小数位，否则无限循环的小数divide会抛异常）
        BigDecimal fl = BigDecimal.valueOf(file.length());
        BigDecimal dv = fl.divide(BigDecimal.valueOf(part), RoundingMode.CEILING);
        int partLen  = (int)Math.ceil(dv.doubleValue()); // 每份的长度（最小为1字节）
        if (partLen <= len) {
            len = partLen; // 每组的字节，按实际数据计算
            capacity += file.length(); // 读取整个文件计算
        } else if (len * part >= file.length()) {
            capacity += file.length(); // 读取整个文件计算
        } else {
            capacity += len * part;
        }
        // 将文件长度转为字节数组添加到
        ByteBuffer buffer = ByteBuffer.allocate(capacity);
        if (!onlyData) {
            buffer.putLong(file.length()); // 加入数据长度
        }
        if (StringUtil.notNull(salt)) {
            buffer.put(salt.getBytes(StandardCharsets.UTF_8)); // 加盐
        }
        // 读取文件（从最后一段往前读取）
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            // TODO 如果文件 < 20M 建议一次读入，否则有性能会降低
            for (part--; part >= 0; part--) {
                long pos  = part * partLen;
                long temp = file.length() - pos;
                if (temp <= 0) {
                    continue;
                }
                temp = Math.min(temp, len);
                byte[] byts = new byte[(int)temp];
                raf.seek(pos);
                raf.read(byts);
                buffer.put(byts);
            }
        } catch (Exception e) {
            log.error("文件[{}]MD5计算：读取文件出错.", file.getAbsolutePath(), e);
            throw new IllegalArgumentException(String.format("[%s]计算MD5出错", file.getAbsolutePath()), e);
        } finally {
            FileUtil.close(raf);
        }
        // 因为 partLen 做了向上取整，所以可能存在没填充满的情况
        if (buffer.position() != buffer.limit()) {
            byte[] arr = new byte[buffer.position()];
            buffer.flip().get(arr);
            return encrypt(arr);
        } else {
            return encrypt(buffer.array());
        }
    }
}
