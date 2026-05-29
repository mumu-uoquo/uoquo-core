/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils;

import com.uoquo.utils.crypto.SnowFlake;
import com.uoquo.utils.crypto.ULID;

import java.util.Date;
import java.util.UUID;

/**
 * 描述：ID 生成器. <br>
 * 备注：
 * 日期：2018-03-23 22:54 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-03-23     xuhz.           雪花算法获取ID
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class IDGenerator {

    /**
     * 62进制字符（按ascii顺序）
     */
    private static final char[] SYMBOLS = {
            '0','1','2','3','4','5','6','7','8','9',
            'A','B','C','D','E','F','G','H','I','J',
            'K','L','M','N','O','P','Q','R','S','T',
            'U','V','W','X','Y','Z','a','b','c','d',
            'e','f','g','h','i','j','k','l','m','n',
            'o','p','q','r','s','t','u','v','w','x',
            'y','z'};

    /**
     * SnowFlake 单例
     */
    private static SnowFlake snowFlakeInstance = null;
    private static SnowFlake instanceSnowFlake() {
        if (snowFlakeInstance == null) {
            synchronized (IDGenerator.class) {
                if (snowFlakeInstance == null) {
                    snowFlakeInstance = new SnowFlake(Config.APP_CODE, Config.APP_NODE, true);
                }
            }
        }
        return snowFlakeInstance;
    }

    /**
     * 获取下一个整型ID.<br>
     * @return long ID
     */
    public static long getNextLong() {
        return instanceSnowFlake().nextId();
    }

    /**
     * 获取下一个整型ID.<br>
     * @return int ID
     */
    public static int getNextInt() {
        /* =================================
         * 备注：采用CRC32算法计算Long，注意计算后的int是非时序的。<br>
         * 如果需要时序的int，请采用redis、db等分块自增的模式
         * ================================= */
//        byte[] data = DataUtil.getBytes(instanceSnowFlake().nextId());
//        CRC32 crc32 = new CRC32();
//        crc32.update(data, 0, data.length);
//        return (int) (crc32.getValue() & 0x7FFFFFFF);

        return instanceUlid().nextInt();
    }

    /**
     * 获取下一个字符串ID（10位）.<br>
     * 注：将Long转为62进制的字符串
     * @return String ID
     */
    public static String getNextString() {
        // 采用36进制得到的字符串长度为11，因此采用62进制，字符串长度为10
//        return Long.toString(instance().nextId(), 36);
        int base = SYMBOLS.length;
        long decimal = instanceSnowFlake().nextId();
        StringBuilder sb = new StringBuilder();
        while (decimal >= 1) {
            long mod = decimal % base;
            sb.append(SYMBOLS[(int)mod]);
            decimal = decimal / base;
        }
        return sb.reverse().toString();
    }

    /**
     * 获取下一个UUID.<br>
     * @return String 32位字符串
     */
    public static String getUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    /**
     * ULID 单例.
     */
    private static ULID ulidInstance = null;
    private static ULID instanceUlid() {
        if (ulidInstance == null) {
            synchronized (IDGenerator.class) {
                if (ulidInstance == null) {
                    ulidInstance = new ULID(16);
                }
            }
        }
        return ulidInstance;
    }

    /**
     * 获取下一个时序ULID（推荐）
     * @return String 16位字符串
     */
    public static String getNextULID() {
        return instanceUlid().nextId();
    }

    /**
     * 获取指定时间的ULID
     */
    public static String getULID(Date date) {
        if (date == null) {
            return instanceUlid().nextId();
        } else {
            // 每次新建对象，防止污染单例的属性
            return new ULID(16).nextId(date);
        }
    }
}
