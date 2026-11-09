/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.crypto;

import com.uoquo.utils.DateUtil;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/**
 * 数据ID（推荐）
 * <br>优点：毫秒级时序，与数据中心无关。
 * <br>参考：<a href="https://www.likecs.com/show-308629867.html">https://www.likecs.com/show-308629867.html</a>
 * <br>标准：6 byte（时间戳 10889年） + 10 byte（随机数） ，共 16 byte（128 bit），转换为base32为 26 字符
 * <br>改造：5.5 byte（时间戳 2527年）+ 4.5 byte（随机数），共 10 byte（80  bit），转换为base32为 16 字符
 * <pre>
 * 补充：
 *   20251121：高位固定填1，防止生成字串为数字开头，可用时间将减半(16位：2248年，26位：无影响)
 *   随机源：使用 {@link SecureRandom}（不可预测）。同一毫秒内，随机数按随机步长递增（而非固定+1），
 *           既保证时序，又避免由一个ID推算出相邻ID。
 *   首字符：16位和26位的首字符均为字母（16位：G ~ Z，26位：G ~ Q），不会以数字开头。
 *   熵：16位仅有 36 bit 随机数，26位有 80 bit 随机数。时间戳部分不属于保密信息。
 *   线程安全：nextId() / nextInt() 为同步方法，同一实例可被多线程共享，且在时钟回拨时仍保持递增。
 *             nextId(Date) 不修改实例状态。toString()、getTimestamp()、equals() 等读取方法未加锁，仅用于单线程场景（如 parse 的结果）。
 * </pre>
 */
public class ULID  implements Comparable<ULID>{

    /**
     * 生成的ID长度（目前只支持16和26）
     */
    private final int ULID_BYTE_LEN;

    /**
     * 时间戳（ms）的上限（不含）。
     * 16位：高位固定填1，实际只有 43 bit 可用；26位：48 bit
     */
    private final long MAX_TIME;

    /**
     * 同一毫秒内，随机数递增步长的掩码（步长范围为 [1, STEP_MASK + 1]）。
     * 16位：2^20（每毫秒平均可容纳约6万个ID，高于单线程生成速度）；26位：2^32
     */
    private final long STEP_MASK;

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
            // 同时支持小写，解析时无需再 toUpperCase（避免受默认 Locale 影响）
            DECODE_ALPHABET[Character.toLowerCase(ENCODE_ALPHABET[i])] = i;
        }
        // OIL（大小写均支持）
        DECODE_ALPHABET['O'] = 0x00;
        DECODE_ALPHABET['o'] = 0x00;
        DECODE_ALPHABET['I'] = 0x01;
        DECODE_ALPHABET['i'] = 0x01;
        DECODE_ALPHABET['L'] = 0x01;
        DECODE_ALPHABET['l'] = 0x01;
    }

    /**
     * nextId() 上一次使用的时间戳（ms），受 this 锁保护。
     * 时钟回拨时以该值为准，保证 ID 不倒退。
     */
    private long lastStamp = 0;

    /**
     * nextInt() 上一次使用的时间窗口（ms，按步长对齐），受 this 锁保护。
     */
    private long lastIntStamp = 0;

    /**
     * nextInt() 当前窗口内的序列号（12 bit），受 this 锁保护。
     */
    private int intSeq = 0;

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
     * 低有效位的最大值（用于判断随机空间是否耗尽，耗尽后需等待下一毫秒）
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
        if ((len != 16) && (len != 26)) {
            throw new IllegalArgumentException("ULID length only supports 16 or 26, but was " + len);
        }
        this.ULID_BYTE_LEN = len;
        // 初始数据
        if (ULID_BYTE_LEN == 16) {
            TIME_BIT_LEN   = 44; // 5.5 byte
            MAX_TIME = 1L << 43; // 最高位固定填1，占用了1 bit
            STEP_MASK = (1L << 20) - 1;
            RANDOM_MSB_BIT_LEN = 0;
            RANDOM_LSB_BIT_LEN = 36; // 4.5 byte
        } else {
            TIME_BIT_LEN   = 48; // 6  byte
            MAX_TIME = 1L << 48;
            STEP_MASK = (1L << 32) - 1;
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
    public synchronized String nextId() {
        // 时钟回拨时，沿用上一次的时间戳，保证ID不倒退
        long time = Math.max(this.getThisTime(1), this.lastStamp);
        if (time != this.lastStamp) {
            // 不同毫秒，则获取不同随机数
            this.generateData(time);
            this.lastStamp = time;
        } else {
            // 相同毫秒，随机数增加一个随机步长（用于保证时序，同时避免相邻ID可被推算）
            long step = this.nextStep();
            // 剩余空间（无符号数，lsb 始终不超过 LSB_MASK）
            long room = LSB_MASK - this.lsb;
            if (Long.compareUnsigned(step, room) <= 0) {
                this.lsb += step;
            } else {
                // 本毫秒的随机空间已耗尽，等待下一毫秒
                time = this.getNextTime(1, this.lastStamp);
                this.generateData(time);
                this.lastStamp = time;
            }
        }
        return this.encode(this.msb, this.lsb);
    }

    /**
     * 获取指定时间的ID值.
     * <br>不会修改当前实例的状态，因此不影响 {@link #nextId()} 的时序。
     * @throws IllegalArgumentException 时间超出可表示的范围（16位：[0, 2^43)ms，26位：[0, 2^48)ms）
     */
    public String nextId(Date date) {
        long time = date.getTime();
        if (time < 0 || time >= MAX_TIME) {
            throw new IllegalArgumentException("Timestamp out of range for ULID(" + ULID_BYTE_LEN + "): " + time);
        }
        return this.encode(this.buildMsb(time), this.generateRandomness() & LSB_MASK);
    }

    private String encode(long hi, long lo) {
        return (ULID_BYTE_LEN == 16) ? toString4Len16(hi, lo) : toString4Len26(hi, lo);
    }

    /**
     * 获取下一个int值（19 bite时间戳，12 bite随机数）
     * 效率：每毫秒约4个（目前按500毫秒一组数据）
     * 注：72H内不会重复，否则有重复风险
     */
    public synchronized int nextInt() {
        int step = 500; // 每500ms作为一组
        // 时钟回拨时，沿用上一次的时间窗口，保证不倒退
        long time = Math.max(this.getThisTime(step), this.lastIntStamp);
        if (time != this.lastIntStamp) {
            // 不同窗口，则获取不同随机数
            this.lastIntStamp = time;
            this.intSeq = (int) (this.generateRandomness() & INT_LSB_MASK);
        } else {
            // 相同窗口，序列号加1（用于保证时序）
            this.intSeq += 1;
        }
        // 序列号过大时，等待下一个窗口
        while (this.intSeq >= INT_LSB_MASK) {
            time = this.getNextTime(step, this.lastIntStamp);
            this.lastIntStamp = time;
            this.intSeq = (int) (this.generateRandomness() & INT_LSB_MASK);
        }
        // 拼接整数
        int r = 0;
        // 时间戳
        r |= (int) ((time / step) & 0x7FFFF);
        r <<= 12;
        // 随机数
        r |= this.intSeq;
        return r;
    }

    /**
     * 获取下一个时间窗口的数值（必须大于 last）.
     * @param step 窗口步长（ms）
     * @param last 上一次使用的时间戳
     */
    private long getNextTime(int step, long last) {
        long mill = getThisTime(step);
        while (mill <= last) {
            if (step > 1) {
                LockSupport.parkNanos(200_000L);
            } else {
                Thread.onSpinWait();
            }
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
        this.msb = this.buildMsb(time);
        // 仅保留有效位（16位ID只用低36 bit），保证与 parse 的结果一致
        this.lsb = generateRandomness() & LSB_MASK;
    }

    /**
     * 构造高有效位：时间戳（+ 26位ID时的16 bit随机数）
     */
    private long buildMsb(long time) {
        if (ULID_BYTE_LEN == 16) {
            // 20 = Long.BYTES - TIME_BIT_LEN; // 64 - 44
            return time << timeBitLeftMoveLen;
        }
        // 16 = Long.BYTES - TIME_BIT_LEN; // 64 - 48
        return (time << timeBitLeftMoveLen) | (SecureRandomPool.nextInt() & 0xFFFFL);
    }

    /**
     * 生成随机数（密码学安全）
     */
    private long generateRandomness() {
        return SecureRandomPool.nextLong();
    }

    /**
     * 生成同一毫秒内的随机递增步长，范围 [1, STEP_MASK + 1]
     */
    private long nextStep() {
        return (SecureRandomPool.nextInt() & STEP_MASK) + 1;
    }

    /**
     * 安全随机数缓冲池.
     * <br>每次从 {@link SecureRandom} 批量取一批随机字节，再逐段消费，避免每个ID都产生一次 SecureRandom 调用的开销。
     * 已消费的字节会立即清零。
     */
    private static final class SecureRandomPool {
        private static final SecureRandom RANDOM = new SecureRandom();
        private static final byte[] BUF = new byte[1024];
        private static int pos = BUF.length;

        private static synchronized long take(int n) {
            if (pos + n > BUF.length) {
                RANDOM.nextBytes(BUF);
                pos = 0;
            }
            long v = 0;
            for (int i = 0; i < n; i++) {
                v = (v << 8) | (BUF[pos + i] & 0xFFL);
                BUF[pos + i] = 0;
            }
            pos += n;
            return v;
        }

        /** 返回 64 bit 随机数 */
        static long nextLong() {
            return take(8);
        }

        /** 返回 32 bit 随机数（无符号，0 ~ 2^32-1） */
        static long nextInt() {
            return take(4);
        }
    }

    /**
     * 将生成的ID转换为可读字符串
     * 备注：仅适用于生成16字符，性能较好
     */
    private String toString4Len16(long hi, long lo) {
        byte[] bytes = new byte[16];
        int idx = bytes.length;
        // 1. 拼接随机串
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo        & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>>  5 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 10 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 15 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 20 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 25 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 30 & 0x1F)];
        // 2. 拼接中间1字节（ttttr：时间戳的 0 ~ 3 bit 与随机数的第 35 bit）
        long msb = hi >>> 20;
        long mdl = ((msb & 0x0F) << 1) | (lo >>> 35 & 0x01);
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
     * 备注：仅适用于生成26字符，性能较好
     */
    private String toString4Len26(long hi, long lo) {
        byte[] bytes = new byte[26];
        int idx = bytes.length;
        // 1. 拼接随机串
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo        & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>>  5 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 10 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 15 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 20 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 25 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 30 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 35 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 40 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 45 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 50 & 0x1F)];
        bytes[--idx] = (byte) ULID.ENCODE_ALPHABET[(int) (lo >>> 55 & 0x1F)];
        // 2. 拼接中间1字节（trrrr：随机数的第 0 bit 与随机数的 60 ~ 63 bit）
        long msb = hi;
        long mdl = ((msb & 0x01) << 4) | (lo >>> 60 & 0x0F);
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
            throw new IllegalArgumentException("ULID must not be null");
        }
        int len = value.length();
        if ((len != 16) && (len != 26)) {
            throw new IllegalArgumentException("Invalid length of ULID");
        }
        // 字符有效性判断（码表已包含大小写，非 ASCII 字符直接判为非法）
        char[] chars = value.toCharArray();
        for (char c : chars) {
            if ((c >= DECODE_ALPHABET.length) || (DECODE_ALPHABET[c] == -1)) {
                throw new IllegalArgumentException("Invalid ULID canonical string for char '" + c + "'");
            }
        }
        // 解码
        ULID ulid = new ULID(len);
        if (len == 16) {
            long timestamp  = decodeBase32(0, 0, 9, 40, DECODE_ALPHABET, chars);
            long randomness = decodeBase32(0, 9, 16, 30, DECODE_ALPHABET, chars);
            // 需将高位补填的1去掉：timestamp 的第 0 bit 属于随机数，第 1 ~ 43 bit 为时间戳，第 44 bit 为补填的1
            ulid.msb = (timestamp & 0xFFFFFFFFFFEL) << 19;
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
        // 按无符号比较，保证与字符串的字典序一致（26位ID的 lsb 最高位可能为1）
        int msbFlag = Long.compareUnsigned(this.msb, ulid.msb);
        return msbFlag != 0 ? msbFlag : Long.compareUnsigned(this.lsb, ulid.lsb);
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
