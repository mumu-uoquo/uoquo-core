/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils;

import java.util.Arrays;
import java.util.List;

/**
 * 描述：数据处理工具类. <br>
 * 约定：
 * <ul>
 *   <li>字节序参数 asc：true 为小端（低位字节在前），false 为大端（高位字节在前）；不带该参数的方法默认小端.</li>
 *   <li>入参不合法（null、长度不足、导联数不合法、数据长度与格式不匹配等）抛出{@link IllegalArgumentException}.</li>
 * </ul>
 * 日期：2019-03-20 19:09 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2019-03-20     Administrator.           创建
 * 1.1          2026-11-10     uoquo team       修复大端读取抛出数组越界；差分/212解压增加入参校验（导联数不合法时不再死循环）；
 *                                              实现pack212；解压不再产生装箱对象和临时数组；去掉多余的throws声明
 * </pre>
 * @since   JDK 1.8
 * @version 1.1
 * @author  uoquo team
 */
public class DataUtil {
    
    /**
     * 转换为无符号型数据.
     * @param b 字节数据
     */
    public static short getUnsignedByte(byte b) {
        return (short)(b & 0xFF);
    }
    
    /**
     * short转字节数组（默认小端）. <br>
     * @param s short数字
     */
    public static byte[] getBytes(short s)  {
        return getBytes(s, true);
    }
    
    /**
     * short转字节数组 . <br>
     * @param s short数字
     * @param asc true:小端格式 false：大端格式
     */
    public static byte[] getBytes(short s, boolean asc)  {
        return toBytes(s, 2, asc);
    }
    
    /**
     * int转字节数组（默认小端）. <br>
     * @param s int值
     */
    public static byte[] getBytes(int s) {
        return getBytes(s, true);
    }
    
    /**
     * int转字节数组.<br>
     * @param s int值
     * @param asc true:小端格式 false：大端格式
     */
    public static byte[] getBytes(int s, boolean asc) {
        return toBytes(s, 4, asc);
    }
    
    /**
     * long转字节数组（默认小端）. <br>
     * @param s long值
     */
    public static byte[] getBytes(long s) {
        return getBytes(s, true);
    }
    
    /**
     * long转字节数组.
     * @param s long值
     * @param asc true:小端格式 false：大端格式
     */
    public static byte[] getBytes(long s, boolean asc) {
        return toBytes(s, 8, asc);
    }

    /**
     * 字节数组转无符号short（默认小端）. <br>
     * @param buf 数组
     */
    public static int getUnsignedShort(byte[] buf) {
        return getUnsignedShort(buf, true);
    }

    /**
     * 字节数组转无符号short.
     * @param buf 数组
     * @param asc true:小端格式 false：大端格式
     */
    public static int getUnsignedShort(byte[] buf, boolean asc) {
        short temp = getShort(buf, asc);
        return temp & 0xFFFF;
    }
    
    /**
     * 字节数组转short（默认小端）. <br>
     * @param buf 数组
     */
    public static short getShort(byte[] buf) {
        return getShort(buf, true);
    }
    
    /**
     * 字节数组转short（读取前2个字节）.
     * @param buf 数组
     * @param asc true:小端格式 false：大端格式
     * @throws IllegalArgumentException 数组为null或者长度小于2
     */
    public static short getShort(byte[] buf, boolean asc) {
        return (short) toNumber(buf, 2, asc);
    }

    /**
     * 字节数组转无符号int（默认小端）. <br>
     * @param buf 数组
     */
    public static long getUnsignedInt(byte[] buf) {
        return getUnsignedInt(buf, true);
    }

    /**
     * 字节数组转无符号int.
     * @param buf 数组
     * @param asc true:小端格式 false：大端格式
     */
    public static long getUnsignedInt(byte[] buf, boolean asc) {
        int temp = getInt(buf, asc);
        return temp & 0xFFFFFFFFL;
    }

    /**
     * 字节数组转int（默认小端）. <br>
     * @param buf 数组
     */
    public static int getInt(byte[] buf) {
        return getInt(buf, true);
    }
    
    /**
     * 字节数组转int（读取前4个字节）.
     * @param buf 数组
     * @param asc true:小端格式 false：大端格式
     * @throws IllegalArgumentException 数组为null或者长度小于4
     */
    public static int getInt(byte[] buf, boolean asc) {
        return (int) toNumber(buf, 4, asc);
    }
    
    /**
     * 字节数组转long（默认小端）. <br>
     * @param buf 数组
     */
    public static long getLong(byte[] buf) {
        return getLong(buf, true);
    }
    
    /**
     * 字节数组转long（读取前8个字节）.
     * @param buf 数组
     * @param asc true:小端格式 false：大端格式
     * @throws IllegalArgumentException 数组为null或者长度小于8
     */
    public static long getLong(byte[] buf, boolean asc) {
        return toNumber(buf, 8, asc);
    }

    /**
     * 数字转字节数组（取低len个字节）.
     */
    private static byte[] toBytes(long value, int len, boolean asc) {
        byte[] buf = new byte[len];
        for (int i = 0; i < len; i++) {
            byte b = (byte) (value >>> (8 * i)); // 第i个字节（i=0为最低位）
            buf[asc ? i : len - 1 - i] = b;
        }
        return buf;
    }

    /**
     * 字节数组的前len个字节转数字.
     */
    private static long toNumber(byte[] buf, int len, boolean asc) {
        if (buf == null) {
            throw new IllegalArgumentException("byte array is null!");
        }
        if (buf.length < len) {
            throw new IllegalArgumentException("byte array size must be >= " + len + " !");
        }
        long r = 0;
        if (asc) {
            for (int i = len - 1; i >= 0; i--) {
                r = (r << 8) | (buf[i] & 0xffL);
            }
        } else {
            for (int i = 0; i < len; i++) {
                r = (r << 8) | (buf[i] & 0xffL);
            }
        }
        return r;
    }
    
    /** 
     * 将byte转换为一个长度为8的byte数组，每个元素为0或1.<br>
     * 顺序：低位在前，即 array[0] 为最低位（bit0），array[7] 为最高位（bit7）.
     */
    public static byte[] to8Array(byte b) {
        byte[] array = new byte[8];
        for (int i = 0; i <= 7; i++) {
            array[i] = (byte) ((b >> i) & 1);
        }
        return array;
    }
    
    /**
     * 8位差分解压.<br>
     * 原理：每个导联的第一个数据记录原始数据（2字节，小端），其余数据为差分记录（1字节，即：8位），
     * 按采样点依次存放（采样点1的各导联差分，采样点2的各导联差分，...）.<br>
     * 输出：所有采样值，每个值2字节（小端），顺序同输入，输出长度 = 2 * data.length - 2 * leadLen.
     * @param data    4s的数据包（不含48字节头信息）
     * @param leadLen 有效导联个数（必须大于0）
     * @return 解压后的数据包
     * @throws IllegalArgumentException data为null，导联数不合法，或者数据长度与格式不匹配
     */
    public static byte[] unpackDifference(byte[] data, int leadLen) {
        if (data == null) {
            throw new IllegalArgumentException("data is null!");
        } else if (leadLen <= 0) {
            throw new IllegalArgumentException("leadLen must be > 0 !");
        }
        int head = 2 * leadLen; // 每个导联的第一个值（2字节存储）
        if (data.length < head) {
            throw new IllegalArgumentException("data size must be >= " + head + " (2 * leadLen) !");
        } else if ((data.length - head) % leadLen != 0) {
            throw new IllegalArgumentException("difference data size must be a multiple of leadLen !");
        }
        byte[] out  = new byte[2 * data.length - head];
        short[] pre = new short[leadLen]; // 每个导联的上一个值
        int pos = 0; // 读取data数据的位置
        int idx = 0; // 写入out的位置
        for (int k = 0; k < leadLen; k++) {
            out[idx++] = data[pos];
            out[idx++] = data[pos + 1];
            pre[k] = (short) (((data[pos + 1] & 0xff) << 8) | (data[pos] & 0xff));
            pos += 2;
        }
        // 按差分法计算剩余值（差分数值1字节存储）
        while (pos < data.length) {
            for (int k = 0; k < leadLen; k++) {
                pre[k] += data[pos++]; // 第K导联的当前采样数值 = 上一数值 + 差分数值
                out[idx++] = (byte) pre[k];
                out[idx++] = (byte) (pre[k] >> 8);
            }
        }
        return out;
    }
    
    /**
     * 12位差分解压.<br>
     * 原理：每个导联的前两个数据记录原始数据（2字节，小端），其余数据为差分记录（1.5字节，即：12位），差分数据按212格式存储.<br>
     * 输入顺序：采样点1的各导联原始值，采样点2的各导联原始值，之后每组（3 * leadLen 字节）为连续两个采样点的各导联差分.<br>
     * 输出：所有采样值，每个值2字节（小端），每组差分输出为：采样点N的各导联值，采样点N+1的各导联值.
     * @param data    4s的数据包（不含48字节头信息）
     * @param leadLen 有效导联个数（必须大于0）
     * @return 解压后的数据包
     * @throws IllegalArgumentException data为null，导联数不合法，或者数据长度与格式不匹配
     */
    public static byte[] unpack212(byte[] data, int leadLen) {
        if (data == null) {
            throw new IllegalArgumentException("data is null!");
        } else if (leadLen <= 0) {
            throw new IllegalArgumentException("leadLen must be > 0 !");
        }
        int head  = 4 * leadLen; // 每个导联的前两个值（每个值2字节存储）
        int group = 3 * leadLen; // 一组差分：每个导联3字节（2个差分数值）
        if (data.length < head) {
            throw new IllegalArgumentException("data size must be >= " + head + " (4 * leadLen) !");
        } else if ((data.length - head) % group != 0) {
            throw new IllegalArgumentException("difference data size must be a multiple of " + group + " (3 * leadLen) !");
        }
        int groups = (data.length - head) / group;
        byte[] out  = new byte[head + groups * head];
        short[] pre = new short[leadLen]; // 每个导联的上一个值
        int pos = 0; // 读取data数据的位置
        int idx = 0; // 写入out的位置
        for (int i = 0; i < 2; i++) {
            for (int k = 0; k < leadLen; k++) {
                out[idx++] = data[pos];
                out[idx++] = data[pos + 1];
                pre[k] = (short) (((data[pos + 1] & 0xff) << 8) | (data[pos] & 0xff));
                pos += 2;
            }
        }
        int second = 2 * leadLen; // 采样点 N+1 的数据，相对于采样点 N 的偏移
        while (pos < data.length) {
            for (int k = 0; k < leadLen; k++) {
                // 212解压（即：将3个字节解压位2个差分数值）
                int d1 = value1(data[pos], data[pos + 1]);
                int d2 = value2(data[pos + 1], data[pos + 2]);
                pos += 3;
                short s1 = (short) (pre[k] + d1); // 采样点 N   的数据
                short s2 = (short) (s1 + d2);     // 采样点 N+1 的数据
                pre[k] = s2;                      // 最后一个数值作为下一次计算的基数
                out[idx + 2 * k]              = (byte) s1;
                out[idx + 2 * k + 1]          = (byte) (s1 >> 8);
                out[idx + second + 2 * k]     = (byte) s2;
                out[idx + second + 2 * k + 1] = (byte) (s2 >> 8);
            }
            idx += 4 * leadLen;
        }
        return out;
    }
    
    /**
     * 212压缩解压缩（含符号位）.<br>
     * 备注：目前我们的心电采样都是short类型，因此转换为short数据
     * 原理：a和c为两个数据的低八位，b的低四位为第一个数据的高四位，b的高四位为第二个数据的高四位，
     *      每个数据的最高位为符号位（即：b字节的第四位（从右往左）和第八位为两个数据的符号位），
     *      符号位=1时，数据值 = (-1) * (data & 0x7ff)
     *      符号位=0时，数据值 = (data & 0x7ff)
     * @param a 第一个字节
     * @param b 第二个字节
     * @param c 第三个字节
     */
    public static List<Integer> unpack212(byte a, byte b, byte c) {
        return Arrays.asList(value1(a, b), value2(b, c));
    }

    /**
     * 212的第一个数值（含符号位）：a为低八位，b的低三位为高三位，b的第4位为符号位.
     */
    private static int value1(byte a, byte b) {
        int v = (((b & 0x0f) << 8) | (a & 0xff)) & 0x7ff; // 0x7ff：0111 1111 1111
        return ((b & 0x08) != 0) ? -v : v;                // 0x08：0000 1000
    }

    /**
     * 212的第二个数值（含符号位）：c为低八位，b的高4位中的低三位为高三位，b的第8位为符号位.
     */
    private static int value2(byte b, byte c) {
        int v = (((b & 0xf0) << 4) | (c & 0xff)) & 0x7ff;
        return ((b & 0x80) != 0) ? -v : v;                // 0x80：1000 0000
    }
    
    /**
     * 212压缩解压缩（无符号位）.<br>
     * <pre>
     * 转换前：
     *       b1 = 1111 1111
     *       b2 = 3333 2222
     *       b3 = 4444 4444
     * 转换后：
     *       a = 2222 1111 1111 
     *       b = 3333 4444 4444
     * </pre>
     * @param a 第一个字节
     * @param b 第二个字节
     * @param c 第三个字节
     */
    public static List<Short> unpack212Unsign(byte a, byte b, byte c) {
        // 第一个数值
        int aa = a & 0xff; // 将a转换为无符号类型， 0xff：1111 1111
        int ab = b & 0x0f; // b的低4位， 0x0f：0000 1111
        int v1 = ((ab << 8) | aa) & 0xfff; // 0xfff：1111 1111 1111
        
        // 第二个数值
        int cc = c & 0xff;
        int cb = b & 0xf0; // b的高4位， 0xf0：1111 0000
        int v2 = ((cb << 4) | cc) & 0xfff;
        
        //返回两个值
        return Arrays.asList((short)v1, (short)v2);
    }
    
    /**
     * 212压缩（含符号位），是{@link #unpack212(byte, byte, byte)}的逆运算.<br>
     * 注：会有精度损失！！每个数据只保留11位幅值（0 ~ 2047），绝对值超过2047的数据按2047处理.
     * @param a 数据1
     * @param b 数据2
     * @return 3个字节：a的低八位，（b的高三位、符号位）和（a的高三位、符号位），b的低八位
     */
    public static byte[] pack212(short a, short b) {
        int ma = Math.min(Math.abs((int) a), 0x7ff); // 幅值（限制在11位以内）
        int mb = Math.min(Math.abs((int) b), 0x7ff);
        int mid = (((mb >>> 8) & 0x07) << 4) | ((ma >>> 8) & 0x07);
        if (a < 0) {
            mid |= 0x08; // a的符号位
        }
        if (b < 0) {
            mid |= 0x80; // b的符号位
        }
        return new byte[] { (byte) (ma & 0xff), (byte) mid, (byte) (mb & 0xff) };
    }
    
    /**
     * 212压缩（无符号位）.<br>
     * 注：会有精度损失！！每个数据只保留低12位，超过12位的高位被丢弃.
     * <pre>
     * 转换前：
     *       a = 2222 1111 1111 
     *       b = 3333 4444 4444
     * 转换后：
     *       b1 = 1111 1111
     *       b2 = 3333 2222
     *       b3 = 4444 4444
     * </pre>
     * @param a 数据1
     * @param b 数据2
     */
    public static byte[] pack212Unsign(short a, short b) {
        byte b1 = (byte) (a & 0xff);
        byte b2 = (byte) ((b & 0xf00) >>> 4 | (a & 0xf00) >>> 8);
        byte b3 = (byte) (b & 0xff);
        return new byte[] {b1, b2, b3};
    }
}
