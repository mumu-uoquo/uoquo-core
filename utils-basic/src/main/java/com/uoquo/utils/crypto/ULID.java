/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.crypto;

import com.uoquo.utils.DateUtil;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据ID（推荐）
 * 优点：毫秒级时序，与数据中心无关。
 * 参考：<a href="https://www.likecs.com/show-308629867.html">https://www.likecs.com/show-308629867.html</a>
 * 标准：6 byte（时间戳 10889年） + 10 byte（随机数） ，共 16 byte（128 bit），转换为base32为 26 字符
 * 改造：5.5 byte（时间戳 2527年）+ 4.5 byte（随机数），共 10 byte（80  bit），转换为base32为 16 字符
 * 补充：20251121 高位固定填1，防止生成字串为数字开头，可用时间将减半(16位：2248年，26位：无影响)
 */
public class ULID  implements Comparable<ULID>{

    /**
     * 生成的ID长度（目前只支持16和26）
     */
    private final int ULID_BYTE_LEN;
    
    /**
     * Crockford Base32 编码表。
     */
    private static final char[] ENCODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /**
     * Crockford Base32 解码表。
     */
    private static final long[] DECODE_ALPHABET = new long[128];
    static {
        Arrays.fill(DECODE_ALPHABET, -1);
        for (int i = 0; i < ENCODE_ALPHABET.length; i++) {
            DECODE_ALPHABET[ENCODE_ALPHABET[i]] = i;
        }
        // Upper case OIL
        DECODE_ALPHABET['O'] = 0x00;
        DECODE_ALPHABET['I'] = 0x01;
        DECODE_ALPHABET['L'] = 0x01;
    }

    /**
     * 上一次时间戳（ms）
     */
    private final AtomicLong lastStamp = new AtomicLong(0);

    /**
     * 高有效位
     * 当ID长度为16时：高44 bit为时间戳
     * 当ID长度为26时：高48 bit为时间戳，低16 bit为随机数
     */
    private long msb;

    /**
     * 低有效位
     * 当ID长度为16时：仅低36 bit有效
     * 当ID长度为26时：所有64 bit有效
     */
    private long lsb;

    /**
     * 时间戳占位长度
     */
    private int TIME_BIT_LEN;
    
    /**
     * 随机数在高有效位中的占位长度（超过64的部分）
     */
    private int RANDOM_MSB_BIT_LEN;
    
    /**
     * 随机数在低有效数中的占位长度
     */
    private int RANDOM_LSB_BIT_LEN;

    /**
     * 低有效位的最大值（用于判断是否需要等待下一毫秒）
     */
    private long LSB_MASK;

    /**
     * 取INT时的低有效位的最大值
     */
    private long INT_LSB_MASK;

    // 以下数值可以在使用时再计算，为了减少计算量，所以在构造方法中提前计算
    // 时间戳左移位数
    private int timeBitLeftMoveLen;

    // lsb 字节长度
    private int lsbByteLen;
    // lsb 起始下标
    private int lsbOffset;
    // lsb 剩余位数
    private int lsbModBitLen;
    // lsb 剩余位数的最大值
    private long lsbModMaxVal;
    // lsb 剩余位数需右移的长度
    private int lsbModBitRightMoveLen;

    // mdl 字节长度
    private int mdlByteLen;

    // msb 有效数据需右移的长度
    private int msbBitRightMoveLen;
    // msb 字节长度
    private int msbByteLen;
    // msb 剩余位数
    private int msbModBitLen;
    // msb 剩余位数的最大值
    private long msbModMaxVal;

    /**
     * 构造方法（默认 16 字符长度）
     */
    public ULID() {
        this(16);
    }

    /**
     * 构造方法（仅支持 16 和 26）
     */
    public ULID(int len) {
        this.ULID_BYTE_LEN = len;
        // 初始数据
        if (ULID_BYTE_LEN == 16) {
            TIME_BIT_LEN   = 44; // 5.5 byte
            RANDOM_MSB_BIT_LEN = 0;
            RANDOM_LSB_BIT_LEN = 36; // 4.5 byte
        } else {
            TIME_BIT_LEN   = 48; // 6  byte
            RANDOM_MSB_BIT_LEN = 16;
            RANDOM_LSB_BIT_LEN = 64; // RANDOM_MSB_BIT_LEN + RANDOM_LSB_BIT_LEN = 10 byte
        }
        LSB_MASK     = -1L >>> (Long.BYTES * 8 - RANDOM_LSB_BIT_LEN);
        INT_LSB_MASK = -1L >>> (Long.BYTES * 8 - 12);

        // 以下数值可以在使用时再计算，为了减少计算量，所以提到了构造方法中
        // 时间戳左移位数
        timeBitLeftMoveLen = Long.BYTES * 8 - TIME_BIT_LEN;
        // ID串中，最低有效位的字节长度
        lsbByteLen = RANDOM_LSB_BIT_LEN / 5;
        // ID串中，最低有效位的起始下标
        lsbOffset  = ULID_BYTE_LEN - lsbByteLen;
        // 最低有效位中多余 bit 的标记
        lsbModBitLen = RANDOM_LSB_BIT_LEN % 5;
        lsbModMaxVal = -1L >>> (Long.BYTES * 8 - lsbModBitLen);
        lsbModBitRightMoveLen = RANDOM_LSB_BIT_LEN - lsbModBitLen; // RANDOM_LSB_BIT_LEN / 5 * 5
        // ID串中，中间拼接MSB和LSB的那一个字节
        mdlByteLen = 1;
        // 最高有效位的字节长度
        // ID串中，最高有效位的字节长度
        msbByteLen   = ULID_BYTE_LEN - mdlByteLen - lsbByteLen;
        // 最高有效位中需要与最低有效位多余bit构成一个字节的内容
        msbModBitLen = 5 - lsbModBitLen;
        msbModMaxVal = -1L >>> (Long.BYTES * 8  - msbModBitLen);
        // 高有效为中有效数据需右移的长度
        msbBitRightMoveLen = Long.BYTES * 8 - TIME_BIT_LEN - RANDOM_MSB_BIT_LEN;
    }

    /**
     * 获取下一个ID值
     * 效率：每毫秒约2200个
     */
    public String nextId() {
        long time = this.getThisTime(1);
        if ((time != lastStamp.get()) || (this.lsb == 0)) {
            // 不同毫秒，则获取不同随机数
            this.generateData(time);
        } else {
            // 相同毫秒，随机数加1（用于保证时序）
            this.lsb += 1;
        }
        // 随机位过大时，等待下一毫秒
        if ((this.lsb & LSB_MASK) == LSB_MASK) {
            this.generateData(this.getNextTime(1));
        }
        return (ULID_BYTE_LEN == 16) ? toString4Len16() : toString4Len26();
    }

    public String nextId(Date date) {
        long time = date.getTime();
        this.generateData(time);
        return (ULID_BYTE_LEN == 16) ? toString4Len16() : toString4Len26();
    }

    /**
     * 获取下一个int值（19 bite时间戳，12 bite随机数）
     * 效率：每毫秒约4个（目前按500毫秒一组数据）
     * 注：72H内不会重复，否则有重复风险
     */
    public int nextInt() {
        int step = 500; // 每500ms作为一组
        long time = this.getThisTime(step);
        if ((time != lastStamp.get()) || (this.lsb == 0)) {
            // 不同毫秒，则获取不同随机数
            this.generateData(time);
        } else {
            // 相同毫秒，随机数加1（用于保证时序）
            this.lsb += 1;
        }
        // 随机位过大时，等待下一毫秒
        if ((this.lsb & INT_LSB_MASK) == INT_LSB_MASK) {
            time = this.getNextTime(step);
            this.generateData(time);
        }
        // 拼接整数
        int r = 0;
        // 时间戳
        r |= (int) ((time / step) & 0x7FFFF);
        r <<= 12;
        // 随机数
        r |= (int) (this.lsb & 0xFFF);
        return r;
    }

    /**
     * 获取下一毫秒的数值.
     */
    private long getNextTime(int step) {
        long mill = getThisTime(step);
        while (mill <= lastStamp.get()) {
            mill = getThisTime(step);
        }
        return mill;
    }

    /**
     * 获取当前的时间戳.
     * step = 1： 毫秒
     * step = 1000：秒
     */
    private long getThisTime(int step) {
        long millis = Clock.systemUTC().millis();
        return millis / step * step;
    }

    /**
     * 生成指定时间戳的数据
     */
    private void generateData(long time) {
        lastStamp.set(time);
        if (ULID_BYTE_LEN == 16) {
            // 20 = Long.BYTES - TIME_BIT_LEN; // 64 - 44
            this.msb = time << timeBitLeftMoveLen;
        } else {
            long random = generateRandomness();
            // 16 = Long.BYTES - TIME_BIT_LEN; // 64 - 48
            this.msb = (time << timeBitLeftMoveLen) | (random & 0xFFFFL);
        }
        this.lsb = generateRandomness();
    }

    /**
     * 生成随机数（可使用UUID等的随机数）
     */
    private long generateRandomness() {
        return ThreadLocalRandom.current().nextLong();
    }

    /**
     * 将生成的ID转换为可读字符串
     * 备注：仅适用于生成16字符，性能较好
     */
    private String toString4Len16() {
        byte[] bytes = new byte[16];
        int idx = bytes.length;
        // 1. 拼接随机串
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb        & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>>  5 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 10 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 15 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 20 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 25 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 30 & 0x1F)];
        // 2. 拼接中间1字节（ttttr：时间戳的 0 ~ 3 bit 与随机数的第 35 bit）
        long msb = this.msb >>> 20;
        long mdl = ((msb & 0x0F) << 1) | (this.lsb >>> 35 & 0x01);
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (mdl & 0x1F)];
        // 3. 拼接时间戳
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>>  4 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>>  9 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 14 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 19 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 24 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 29 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 34 & 0x1F)];
        // 高位填1，防止生成的字符串为数字开头
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 39 & 0x1F | 0x10)];
        // 4. 转换为字符串
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    /**
     * 将生成的ID转换为可读字符串
     * 备注：仅适用于生成16字符，性能较好
     */
    private String toString4Len26() {
        byte[] bytes = new byte[26];
        int idx = bytes.length;
        // 1. 拼接随机串
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb        & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>>  5 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 10 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 15 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 20 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 25 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 30 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 35 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 40 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 45 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 50 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (this.lsb >>> 55 & 0x1F)];
        // 2. 拼接中间1字节（trrrr：时间戳的第 0 bit 与随机数的 60 ~ 63 bit）
        long msb = this.msb;
        long mdl = ((msb & 0x01) << 4) | (this.lsb >>> 60 & 0x0F);
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (mdl & 0x1F)];
        // 3. 拼接高位字串
        // 3.1 拼接随机串
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>>  1 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>>  6 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 11 & 0x1F)];
        // 3.2 拼接时间戳
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 16 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 21 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 26 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 31 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 36 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 41 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 46 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 51 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 56 & 0x1F)];
        // 高位填1，防止生成的字符串为数字开头
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (msb >>> 61 & 0x1F | 0x10)];
        // 4. 转换为字符串
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    /**
     * 对byte数据进行base32编码
     * @param val 待处理对象
     * @param buf  转换后的数组（输出）
     * @param offset 转换的数据在buf中的起始下标
     * @param len  待转换的buf长度
     * @param alphabet 转换码表
     * @param prefix   最前一个字符是否补1，避免出现数字开头
     */
    private void encodeBase32(long val, byte[] buf, int offset, int len, char[] alphabet, boolean prefix) {
        int charPos = offset + len;
        do {
            buf[--charPos] = (byte) alphabet[(int) (val & 0x1F)];
            val >>>= 5;
            if (prefix && (charPos == offset + 1)) {
                buf[--charPos] = (byte) alphabet[(int) (val & 0x1F | 0x10)];
            }
        } while (charPos > offset);
    }

    /**
     * 根据现有的ID字符串解析为对象
     */
    public static ULID parse(String value) {
        // 字符串长度判断
        if (Objects.isNull(value)) {
            throw new IllegalArgumentException("Invalid length of ULID");
        }
        int len = value.length();
        if ((len != 16) && (len != 26)) {
            throw new IllegalArgumentException("Invalid length of ULID");
        }
        // 字符有效性判断
        char[] chars = value.toUpperCase().toCharArray();
        for (char c : chars) {
            if (DECODE_ALPHABET[c] == -1) {
                throw new IllegalArgumentException("Invalid ULID canonical string for char '" + c + "'");
            }
        }
        // 解码
        ULID ulid = new ULID(len);
        if (len == 16) {
            long timestamp  = decodeBase32(0, 0, 9, 40, DECODE_ALPHABET, chars);
            long randomness = decodeBase32(0, 9, 16, 30, DECODE_ALPHABET, chars);
            // 需将高位补填的1去掉
            ulid.msb = (timestamp & 0x7FFFFFFFFFEL) << 19;
            ulid.lsb = ((timestamp & 0x1) << 35) | randomness;
        } else {
            long timestamp = decodeBase32(0, 0, 10, 45, DECODE_ALPHABET, chars);
            long highRandomness = decodeBase32(0, 10, 14, 15, DECODE_ALPHABET, chars);
            long lowRandomness  = decodeBase32(0, 14, 26, 55, DECODE_ALPHABET, chars);
            // 在左移16bit时，高位补填的1会自动丢弃，因此不需要单独处理
            ulid.msb = (timestamp << 16) | (highRandomness >>> 4);
            ulid.lsb = (highRandomness << 60) | lowRandomness;
        }
        return ulid;
    }

    /**
     * 对buf进行base32解码
     * @param val 初始数值
     * @param start 起始下标
     * @param end 结束下标
     * @param index bit偏移量
     * @param table 转换码表
     * @param buf 待转换数据
     */
    private static long decodeBase32(long val, int start, int end, int index, long[] table, char[] buf) {
        for (int i = start; i < end; i++) {
            val |= table[buf[i]] << index;
            index = index - 5;
        }
        return val;
    }

    /**
     * 获取ID的时间戳
     */
    public long getTimestamp() {
        return this.msb >>> timeBitLeftMoveLen;
    }

    /**
     * 获取ID的年份
     * @return 年份字符串（如：2025）
     */
    public String getYear() {
        return DateUtil.toString(new Date(this.getTimestamp()), DateUtil.FORMAT_YEAR);
    }

    /**
     * 获取ID的年月
     * @return 年月字符串（如：202503）
     */
    public String getYearAndMonth() {
        return DateUtil.toString(new Date(this.getTimestamp()), "yyyyMM");
    }

    @Override
    public String toString() {
//        return (ULID_BYTE_LEN == 16) ? toString4Len16() : toString4Len26();
        // 将 lsb 和 msb 从低到高，依按从后往前放入 bytes
        byte[] bytes = new byte[ULID_BYTE_LEN];
        // 1. 拼接随机串
        // 16：转换随机数的 0 ~ 34 bit
        // 26：转换随机数的 0 ~ 59 bit:
        encodeBase32(this.lsb, bytes, lsbOffset, lsbByteLen, ULID.ENCODE_ALPHABET, false);

        // 2. 拼接中间1字节
        // 16：拼接时间戳的 0 ~ 3 bit 与随机数的第 35 bit（ttttr）
        // 26：拼接时间戳的第 0 bit 与随机数的 60 ~ 63 bit（trrrr）
        long msb = this.msb >>> msbBitRightMoveLen;
        long ml = msb & this.msbModMaxVal;
        long mr = (this.lsb >>> lsbModBitRightMoveLen) & lsbModMaxVal;
        encodeBase32((ml << lsbModBitLen) | mr, bytes, msbByteLen, mdlByteLen, ULID.ENCODE_ALPHABET, false);

        // 3. 拼接时间戳
        // 16：转换时间戳的 4 ~ 43 bit，44固定填1
        // 26：转换时间戳的 1 ~ 62 bit，63固定填1
        encodeBase32((msb >>> msbModBitLen), bytes, 0, msbByteLen, ULID.ENCODE_ALPHABET, true);
        // 4. 转换为字符串
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    @Override
    public int compareTo(ULID ulid) {
        int msbFlag = Long.compare(this.msb, ulid.msb);
        return msbFlag != 0 ? msbFlag : Long.compare(this.lsb, ulid.lsb);
    }

    @Override
    public boolean equals(Object obj) {
        if ((Objects.isNull(obj)) || (obj.getClass() != ULID.class)) {
            return false;
        }
        ULID id = (ULID) obj;
        return (this.msb == id.msb && this.lsb == id.lsb);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(this.msb ^ this.lsb);
    }
}
