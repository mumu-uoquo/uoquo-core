/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.crypto;

import com.uoquo.utils.DataUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Calendar;

/**
 * 序列号工具
 * <pre>
 * 共 10 字节，最终用 Base32 转为 16 字符
 *
 *  2 byte          7 byte       1 byte
 * --------   -----------------  -------
 * 批次号      OTP(机器码，订单号）  CRC校验
 *
 * </pre>
 */
public class SerialNumUtil {
    private static final Logger log = LoggerFactory.getLogger(SerialNumUtil.class);

    /**
     * 生成序列号（当前时间）
     * @param salesType  销售类型（1：试用，2：包年）
     * @param machineCode 机器码
     * @param orderNumber 订单号
     */
    public static String generateSerial(int salesType, String machineCode, String orderNumber) {
        return generateSerial(salesType, 0, machineCode, orderNumber);
    }

    /**
     * 生成序列号（当前时间）
     * @param salesType  销售类型（1：试用，2：包年）
     * @param expire      有效期（月）
     * @param machineCode 机器码
     * @param orderNumber 订单号
     */
    public static String generateSerial(int salesType, int expire, String machineCode, String orderNumber) {
        Calendar calendar = Calendar.getInstance();
        if (expire >= 0) {
            calendar.add(Calendar.MONTH, expire);
        }
        int year  = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        return generateSerial(salesType, year, month, machineCode, orderNumber);
    }

    /**
     * 生成序列号（到期时间）
     * @param salesType  销售类型（1：试用，2：包年）
     * @param year  有效期-年（yyyy）
     * @param month 有效期-月（[1,12]）
     * @param machineCode 机器码
     * @param orderNumber 订单号
     */
    public static String generateSerial(int salesType, int year, int month, String machineCode, String orderNumber) {
        if (salesType < 0 || salesType > 3){
            throw new IllegalArgumentException("类型只能是 0,1,2,3");
        }

        byte head = (byte)(getRandomInt(0, 100) & 0x03);
        short batch  = getBatchNumber(salesType, head, year, month);
        short random = getBaseSerial(head);
        short serial = joinBatchSerial(head, batch, random);
        try {
            byte[] bytes = new byte[10];
            // 批次
            byte[] arr = DataUtil.getBytes(serial);
            bytes[0] = arr[0];
            bytes[1] = arr[1];
            // OTP
            int digits = 7;
            byte[] code = machineCode.getBytes(StandardCharsets.UTF_8);
            byte[] data = orderNumber.getBytes(StandardCharsets.UTF_8);
            byte[] hash = OTPUtils.generateHash(code, data);
            int offset = hash[hash.length - 1] & (hash.length - digits);
            System.arraycopy(hash, offset, bytes, 2, digits);
            // CRC
            byte[] temp = new byte[9];
            System.arraycopy(bytes, 0, temp, 0, temp.length);
            bytes[9] = CRCUtil.crc8Standard(temp);
            return Base32.encode(bytes);
        } catch (Exception e) {
            log.error("生成序列号出错[type={}, code={}, order={}]", salesType, machineCode, orderNumber, e);
            return null;
        }
    }

    /**
     * 获取销售类型
     * @param serial 序列号
     */
    public static int getType(String serial) {
        // 解析批次号
        byte[] bytes = Base32.decode(serial);
        short batch  = DataUtil.getShort(bytes);
        byte head = (byte) ((batch & 0xC000) >>> 14);
        int lidx  = 2 + head;
        batch = (short) (batch << lidx);
        batch = (short) ((batch & 0xFFFF) >>> 6);
        // 解析销售类型
        if (head % 2 == 0) {
            // YYYYTTMMMM
            batch = (short) (batch >>> 4);
            batch = (short) (batch & 0x03);
        } else {
            // TTMMMMYYYY
            batch = (short) (batch >>> 8);
        }
        return batch;
    }

    /**
     * 获取批次年份（16年内）
     * @param serial 序列号
     */
    public static int getYear(String serial) {
        // 解析批次号
        short batch = parseYear(serial);
        // 转换年
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        byte temp  = (byte)(year % 16);
        if (temp >= batch) {
            return year - (temp - batch);
        } else {
            return year - (16 + temp - batch);
        }
    }

    /**
     * 获取批次到期年份（16年内）
     * @param serial 序列号
     */
    public static int getExpireYear(String serial) {
        // 解析批次号
        short batch = parseYear(serial);
        // 转换年
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        byte temp  = (byte)(year % 16);
        if (temp >= batch) {
            return year + (temp - batch);
        } else {
            return year + (16 + temp - batch);
        }
    }

    /**
     * 解析批次号中的年份
     */
    private static short parseYear(String serial) {
        byte[] bytes = Base32.decode(serial);
        short batch  = DataUtil.getShort(bytes);
        byte head = (byte) ((batch & 0xC000) >>> 14);
        int lidx  = 2 + head;
        batch = (short) (batch << lidx);
        batch = (short) ((batch & 0xFFFF) >>> 6);
        // 解析年
        if (head % 2 == 0) {
            // YYYYTTMMMM
            batch = (short) (batch >>> 6);
        } else {
            // TTMMMMYYYY
            batch = (short) (batch & 0x0F);
        }
        return batch;
    }

    /**
     * 获取批次月份（从1开始）
     * @param serial 序列号
     */
    public static int getMonth(String serial) {
        // 解析批次号
        byte[] bytes = Base32.decode(serial);
        short batch  = DataUtil.getShort(bytes);
        byte head = (byte) ((batch & 0xC000) >>> 14);
        int lidx  = 2 + head;
        batch = (short) (batch << lidx);
        batch = (short) ((batch & 0xFFFF) >>> 6);
        // 解析月
        if (head % 2 == 0) {
            // YYYYTTMMMM
            batch = (short) (batch & 0x0F);
        } else {
            // TTMMMMYYYY
            batch = (short) (batch >>> 4);
            batch = (short) (batch & 0x0F);
        }
        return batch;
    }

    /**
     * 批次号（10 bit)
     * <pre>
     *     偶：YYYYTTMMMM
     *     奇：TTMMMMYYYY
     * </pre>
     * @param type 销售类型
     * @param head 批次头
     * @param yearInt 年份（yyyy）
     * @param monthInt 月份（[1,12]）
     */
    private static short getBatchNumber(int type, int head, int yearInt, int monthInt) {
        Calendar calendar = Calendar.getInstance();
        if (yearInt <= 0) {
            yearInt = calendar.get(Calendar.YEAR);
        }
        if (monthInt <= 0 || monthInt > 12) {
            monthInt = calendar.get(Calendar.MONTH) + 1;  // 1月份为0
        }
        byte year  = (byte)(yearInt % 16); // 只有4bit（0x0F）
        byte month = (byte)(monthInt);
        short batch = 0x0000;
        if (head % 2 == 0) {
            // YTM
            batch = (short) (batch | (year << 6));
            batch = (short) (batch | (type << 4));
            batch = (short) (batch | month);
        } else {
            // TMY
            batch = (short) (batch | (type  << 8));
            batch = (short) (batch | (month << 4));
            batch = (short) (batch | year);
        }
        return batch;
    }

    /**
     * 生成基础序列号（HHxxxxxxxxxxxxxx）
     * @param hidx 批次头
     */
    private static short getBaseSerial(byte hidx){
        short head = hidx;
        short random = (short) getRandomInt(10_000, 65_535);
        random = (short) (random | 0xC000);         // 将前 2 bit 变为 11
        head   = (short) (((head << 14) | 0x3FFF)); // 将 (14个0)HH 变为 HH(14个1)
        return (short) (random & head); // 将random的前2 bit存 hidx 值（HHxxxxxxxxxxxxxx）
    }

    /**
     * 生成真正的批次号<br>
     * 有 6 bit 空余，其中 开头 2bit 为随机序号，空余的 4bit 以序号作为下标插入bath
     * <pre>
     *   bath  = 000000YYYYTTMMMM
     *   serial= HHxxxxxxxxxxxxxx
     *   批次号关系如下
     *   head       batchserial
     *   -----  --------------------
     *   0x00    00YYYYTTMMMMxxxx
     *   0x01    01xTTMMMMYYYYxxx
     *   0x10    10xxYYYYTTMMMMxx
     *   0x11    11xxxTTMMMMYYYYx
     * </pre>
     * @param head   批次头
     * @param batch  批次号
     * @param serial 序列号
     */
    private static short joinBatchSerial(byte head, short batch, short serial) {
        int lidx = 2 + head;
        int bidx = 6 - lidx;                             // 设 head = 0x10;
        batch = (short) (batch << bidx);                 // 0000YYYYTTMMMM00
        batch = (short) (batch | (0x3F << (16 - lidx))); // 1111YYYYTTMMMM00
        batch = (short) (batch | (0x3F >>> lidx));       // 1111YYYYTTMMMM11
        serial = (short) (serial | (0x3FF << bidx));     // 10xx1111111111xx
        serial = (short) (serial & batch);               // 10xxYYYYTTMMMMxx
        return serial;
    }

    /**
     * 获取 [x1, x2]随机数
     */
    private static int getRandomInt(int x1, int x2){
        double f = Math.random() / Math.nextDown(1.0);
        double x = x1 * (1.0 - f) + x2 * f;
        return (int) x;
    }

}
