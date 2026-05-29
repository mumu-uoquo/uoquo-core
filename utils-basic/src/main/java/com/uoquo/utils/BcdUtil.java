/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 描述：二进码十进数，BCD编码转换. <br>
 * 参考：https://baike.baidu.com/item/bcd/13009167<br>
 * 日期：2018-04-10 10:57 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-04-10     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class BcdUtil {
    private static final Logger log = LoggerFactory.getLogger(BcdUtil.class);
    
    private static final byte[] EMPTY_BYTE = new byte[0];
    
    /**
     * BCD码转为10进制串(阿拉伯数字).<br>
     * 注：字符串偶数长度时会去除首位0，有需要的地方请自行补充修复
     * @param array BCD码
     * @return 10进制串
     */
    public static String bcd2str(byte[] array) {
        if ((array == null) || (array.length <= 0)) {
            return "";
        }
        StringBuilder temp = new StringBuilder(array.length * 2);
        for (int i = 0; i < array.length; i++) {
            temp.append((byte) ((array[i] & 0xf0) >>> 4));
            temp.append((byte) (array[i] & 0x0f));
        }
        // 如果是偶数长度，并且首位为0，说明是补码，去除首位0
        // 注：该操作会去除本身就是偶数长度且首位为0的情况
        String str = temp.toString();
        if ((str.length() % 2 == 0) && str.startsWith("0")) {
            return str.substring(1);
        }
        return str;
    }

    /**
     * 10进制串转为BCD码.
     * @param str 10进制串
     * @return BCD码
     */
    public static byte[] str2bcd(String str) {
        if (StringUtil.isNull(str)) {
            return EMPTY_BYTE;
        }
        // 补码：奇数长度前补0
        if ((str.length() % 2) != 0) {
            str = "0" + str;
        }
        int len = str.length() / 2;
        byte[] rsb = new byte[len];  // 返回的byte[]
        byte[] scb = str.getBytes(); // 原始的byte[]
        // 编码转换
        for (int p = 0; p < scb.length / 2; p++) {
            int p0 = scb2bcd(scb[2 * p]);
            int p1 = scb2bcd(scb[2 * p + 1]);
            rsb[p] = (byte) ((p0 << 4) + p1);
        }
        return rsb;
    }
    
    /**
     * 转码.
     * @param m 待转码字符
     * @return 转码后的字符
     */
    private static int scb2bcd(byte m) {
        if ((m >= '0') && (m <= '9')) {
            return m - '0';
        } else if ((m >= 'a') && (m <= 'z')) {
            return m - 'a' + 0x0a;
        } else {
            return m - 'A' + 0x0a;
        }
    }
    
    /**
     * BCD码转换为日期.<br>
     * 注：
     * <ul>
     *   <li>array长度为6时，格式为yyMMddHHmmss，需补齐年的前两位</li>
     *   <li>array长度为7时，格式为yyyyMMddHHmmss</li>
     * </ul>
     * @param array BCD码
     * @return 日期
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
     * </ul>
     * @param array BCD码
     * @param refer 参考时间（当需要补齐年的前两位时，以该时间作为基准）
     * @return 日期
     */
    public static Date bcd2date(byte[] array, Date refer) {
        // 入参合理化判断，只转换长度为6或者7的bcd码
        if (array == null) {
            return null;
        } else if ((array.length != 6) && (array.length != 7)) {
            return null;
        }
        // bcd转为字符串
        String str = bcd2str(array);
        // 补齐年的前两位
        if (str.length() == 11) {
            str = "0" + str;
        }
        // 拼接年的前两位
        SimpleDateFormat sft = new SimpleDateFormat("yyyyMMddHHmmss");
        if (str.length() == 12) {
            refer = (refer == null) ? new Date() : refer;
            String temp = sft.format(refer);
            int century = Integer.parseInt(temp.substring(0, 2));
            int year    = Integer.parseInt(temp.substring(2, 4));
            int hex = Integer.parseInt(StringUtil.byte2hex(array[0]));
            if (hex > year) {
                str = (century - 1) + str;
            } else {
                str = century + str;
            }
        }
        // 转换日期
        try {
            return sft.parse(str);
        } catch (ParseException e) {
            log.error("parse bcd to date error. str={}", str, e);
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
        SimpleDateFormat sft = new SimpleDateFormat("yyyyMMddHHmmss");
        String temp = sft.format(date);
        return flag ? str2bcd(temp) : str2bcd(temp.substring(2));
    }
}
