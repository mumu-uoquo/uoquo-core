/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 描述：二进码十进数，BCD编码转换. <br>
 * 参考：https://baike.baidu.com/item/bcd/13009167<br>
 * 日期：2018-04-10 10:57 <br>
 * 约定：
 * <ul>
 *   <li>每个字节存放两位，高半字节在前；奇数位的数字串在<b>左侧</b>补一个0（如"123"编码为01 23）.</li>
 *   <li>数字串允许包含0-9、a-f、A-F，其中a-f对应半字节10-15，解码时输出小写字母.</li>
 *   <li>入参不合法（非法字符、位数与字节数不匹配等）抛出{@link IllegalArgumentException}；
 *       日期转换失败返回null；空入参返回空串、空数组或null.</li>
 *   <li>日期与BCD的互转均使用系统默认时区.</li>
 * </ul>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-04-10     xuhz.           创建
 * 1.1          2026-11-10     uoquo team       修复非法字符/非ASCII字符编码错误、A-F半字节解码错误、
 *                                              日期宽松解析、跨年世纪误判；增加按位数解码bcd2str(array, digits)；
 *                                              日期转换改用java.time
 * </pre>
 * @since   JDK 1.8
 * @version 1.1
 * @author  uoquo team
 */
public class BcdUtil {
    private static final Logger log = LoggerFactory.getLogger(BcdUtil.class);
    
    private static final byte[] EMPTY_BYTE = new byte[0];

    private static final char[] HEX_CHAR = "0123456789abcdef".toCharArray();

    /** 日期格式（严格模式，非法日期如13月、2月30日会解析失败）. */
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT);

    /** 两位年份推算世纪时，允许比参考时间晚的年数（容忍设备时钟略快，避免跨年时被判为上个世纪）. */
    private static final int FUTURE_YEAR_TOLERANCE = 1;
    
    /**
     * BCD码转为10进制串(阿拉伯数字).<br>
     * 注：字符串偶数长度且首位为0时会去除首位0（视为补码），因此本身以0开头的偶数位数字串会丢失前导0.
     * 需要保留前导0时，请使用{@link #bcd2str(byte[], int)}指定原始位数.
     * @param array BCD码
     * @return 10进制串
     */
    public static String bcd2str(byte[] array) {
        if ((array == null) || (array.length <= 0)) {
            return "";
        }
        String str = toDigits(array);
        // 如果是偶数长度，并且首位为0，说明是补码，去除首位0
        // 注：该操作会去除本身就是偶数长度且首位为0的情况
        if (str.charAt(0) == '0') {
            return str.substring(1);
        }
        return str;
    }

    /**
     * BCD码转为10进制串(阿拉伯数字)，按原始位数还原，不会丢失前导0.<br>
     * 位数为奇数时，去掉左侧补的0（该补位半字节必须为0）；位数为偶数时原样输出.
     * @param array  BCD码
     * @param digits 原始数字位数，必须等于 array.length * 2 或 array.length * 2 - 1
     * @return 10进制串
     * @throws IllegalArgumentException 位数与字节数不匹配，或补位半字节不为0
     */
    public static String bcd2str(byte[] array, int digits) {
        if ((array == null) || (array.length <= 0)) {
            return "";
        }
        int full = array.length * 2;
        if ((digits != full) && (digits != full - 1)) {
            throw new IllegalArgumentException("BCD位数[" + digits + "]与字节数[" + array.length + "]不匹配.");
        }
        String str = toDigits(array);
        if (digits == full) {
            return str;
        }
        if (str.charAt(0) != '0') {
            throw new IllegalArgumentException("BCD补位半字节必须为0.");
        }
        return str.substring(1);
    }

    /**
     * 10进制串转为BCD码.<br>
     * 奇数长度时在左侧补0.
     * @param str 10进制串（允许0-9、a-f、A-F）
     * @return BCD码
     * @throws IllegalArgumentException 包含非法字符
     */
    public static byte[] str2bcd(String str) {
        if (StringUtil.isNull(str)) {
            return EMPTY_BYTE;
        }
        int len = str.length();
        int offset = len % 2; // 奇数长度时，左侧补一个0半字节
        byte[] result = new byte[(len + 1) / 2];
        for (int i = 0; i < len; i++) {
            int nibble = nibble(str.charAt(i), i);
            int pos = (i + offset) / 2;
            if ((i + offset) % 2 == 0) {
                result[pos] = (byte) (nibble << 4); // 高半字节
            } else {
                result[pos] |= (byte) nibble;       // 低半字节
            }
        }
        return result;
    }

    /**
     * 字符转半字节（仅限ASCII的0-9、a-f、A-F）.
     * @param c     待转码字符
     * @param index 字符位置（用于异常信息）
     * @return 半字节值（0-15）
     */
    private static int nibble(char c, int index) {
        if ((c >= '0') && (c <= '9')) {
            return c - '0';
        } else if ((c >= 'a') && (c <= 'f')) {
            return c - 'a' + 0x0a;
        } else if ((c >= 'A') && (c <= 'F')) {
            return c - 'A' + 0x0a;
        }
        throw new IllegalArgumentException("BCD字符串只能包含0-9、a-f、A-F，非法字符位置: " + index);
    }

    /**
     * BCD码逐半字节转为字符（不做任何去零处理），结果长度为 array.length * 2.
     */
    private static String toDigits(byte[] array) {
        char[] chars = new char[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            chars[2 * i]     = HEX_CHAR[(array[i] & 0xf0) >>> 4];
            chars[2 * i + 1] = HEX_CHAR[array[i] & 0x0f];
        }
        return new String(chars);
    }
    
    /**
     * BCD码转换为日期.<br>
     * 注：
     * <ul>
     *   <li>array长度为6时，格式为yyMMddHHmmss，需补齐年的前两位</li>
     *   <li>array长度为7时，格式为yyyyMMddHHmmss</li>
     * </ul>
     * @param array BCD码
     * @return 日期；入参长度不对、包含非十进制半字节或日期不合法时返回null
     */
    public static Date bcd2date(byte[] array) {
        return bcd2date(array, new Date());
    }
    
    /**
     * BCD码转换为日期.<br>
     * 注：
     * <ul>
     *   <li>array长度为6时，格式为yyMMddHHmmss，需补齐年的前两位</li>
     *   <li>array长度为7时，格式为yyyyMMddHHmmss</li>
     *   <li>补齐年的前两位时，以参考时间所在世纪为准；若推算出的年份比参考时间晚超过1年，则取上一个世纪.
     *       例如参考时间为2026年，则26、27取20xx，28及以后取19xx</li>
     * </ul>
     * @param array BCD码
     * @param refer 参考时间（当需要补齐年的前两位时，以该时间作为基准，null时取当前时间）
     * @return 日期；入参长度不对、包含非十进制半字节或日期不合法时返回null
     */
    public static Date bcd2date(byte[] array, Date refer) {
        // 入参合理化判断，只转换长度为6或者7的bcd码
        if ((array == null) || ((array.length != 6) && (array.length != 7))) {
            return null;
        }
        // 逐半字节转为字符串，不做去零处理
        String str = toDigits(array);
        // 补齐年的前两位
        if (str.length() == 12) {
            ZoneId zone = ZoneId.systemDefault();
            long referMillis = (refer == null) ? System.currentTimeMillis() : refer.getTime();
            int referYear = LocalDateTime.ofInstant(Instant.ofEpochMilli(referMillis), zone).getYear();
            // 非十进制半字节在此处无法转为数字，直接视为不合法
            int yy;
            try {
                yy = Integer.parseInt(str.substring(0, 2));
            } catch (NumberFormatException e) {
                log.warn("parse bcd to date error. str={}", str);
                return null;
            }
            int year = (referYear / 100) * 100 + yy;
            if (year > referYear + FUTURE_YEAR_TOLERANCE) {
                year -= 100;
            }
            str = String.format("%04d", year) + str.substring(2);
        }
        // 转换日期
        try {
            LocalDateTime time = LocalDateTime.parse(str, DATE_FORMATTER);
            return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            log.warn("parse bcd to date error. str={}, reason={}", str, e.getMessage());
            return null;
        }
    }
    
    /**
     * 日期转换为6位BCD码.<br>
     * 日期格式: yyMMddHHmmss， BCD格式: yMdhms
     * @param date 日期
     * @return BCD码
     */
    public static byte[] date2bcd(Date date) {
        return date2bcd(date, false);
    }
    
    /**
     * 日期转换为BCD码.<br>
     * 格式：
     * <ul>
     *   <li>flag == true,  日期格式: yyyyMMddHHmmss， BCD格式: yyMdhms</li>
     *   <li>flag == false, 日期格式:   yyMMddHHmmss， BCD格式:  yMdhms</li>
     * </ul>
     * @param date 日期
     * @param flag 是否保留年的前两位（true：保留，false：去除）
     * @return BCD码
     */
    public static byte[] date2bcd(Date date, boolean flag) {
        if (date == null) {
            return EMPTY_BYTE;
        }
        // 使用getTime而不是toInstant，兼容java.sql.Date等子类
        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());
        String temp = time.format(DATE_FORMATTER);
        return flag ? str2bcd(temp) : str2bcd(temp.substring(2));
    }
}
