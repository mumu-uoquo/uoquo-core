/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 描述：数据处理工具类. <br>
 * 日期：2019-03-20 19:09 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2019-03-20     Administrator.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
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
     * @param s shot数字
     */
    public static byte[] getBytes(short s)  {
        return getBytes(s, true);
    }
    
    /**
     * short转字节数组 . <br>
     * @param s shot数字
     * @param asc true:小端格式 false：大端格式
     */
    public static byte[] getBytes(short s, boolean asc)  {
        byte[] buf = new byte[2];
        if (asc) {
            for (int i = 0; i < buf.length; i++) {
                buf[i] = (byte) (s & 0xff);
                s >>= 8;
            }
        } else {
            for (int i = buf.length - 1; i >= 0; i--) {
                buf[i] = (byte) (s & 0xff);
                s >>= 8;
            }
        }
            
        return buf;
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
        byte[] buf = new byte[4];
        if (asc) {
            for (int i = 0; i < buf.length; i++) {
                buf[i] = (byte) (s & 0xff);
                s >>= 8;
            }
        } else {
            for (int i = buf.length - 1; i >= 0; i--) {
                buf[i] = (byte) (s & 0xff);
                s >>= 8;
            }
        }
        return buf;
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
        byte[] buf = new byte[8];
        if (asc) {
            for (int i = 0; i < buf.length; i++) {
                buf[i] = (byte) (s & 0xff);
                s >>= 8;
            }
        } else {
            for (int i = buf.length - 1; i >= 0; i--) {
                buf[i] = (byte) (s & 0xff);
                s >>= 8;
            }
        }
        return buf;
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
     * 字节数组转short.
     * @param buf 数组
     * @param asc true:小端格式 false：大端格式
     */
    public static short getShort(byte[] buf, boolean asc) {
        if (buf == null) {
            throw new IllegalArgumentException("byte array is null!");
        }
        int len = 2;
        if (buf.length < len) {
            throw new IllegalArgumentException("byte array size mast >= 2 !");
        }
        short r = 0;
        if (asc) {
            for (int i = len -1; i >= 0; i--) {
                r <<= 8;
                r |= (buf[i] & 0xff);
            }
        } else {
            for (int i = 0; i < len; i--) {
                r <<= 8;
                r |= (buf[i] & 0xff);
            }
        }
        return r;
    }

    /**
     * 字节数组转无符号short（默认小端）. <br>
     * @param buf 数组
     */
    public static long getUnsignedInt(byte[] buf) {
        return getUnsignedInt(buf, true);
    }

    /**
     * 字节数组转无符号short.
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
     * 字节数组转int.
     * @param buf 数组
     * @param asc true:小端格式 false：大端格式
     */
    public static int getInt(byte[] buf, boolean asc) {
        if (buf == null) {
            throw new IllegalArgumentException("byte array is null!");
        }
        int len = 4;
        if (buf.length < len) {
            throw new IllegalArgumentException("byte array size mast >= 4 !");
        }
        int r = 0;
        if (asc) {
            for (int i = len - 1; i >= 0; i--) {
                r <<= 8;
                r |= (buf[i] & 0xff);
            }
        } else {
            for (int i = 0; i < len; i--) {
                r <<= 8;
                r |= (buf[i] & 0xff);
            }
        }
        return r;
    }
    
    /**
     * 字节数组转long（默认小端）. <br>
     * @param buf 数组
     */
    public static long getLong(byte[] buf) {
        return getLong(buf, true);
    }
    
    /**
     * 字节数组转long.
     * @param buf 数组
     * @param asc true:小端格式 false：大端格式
     */
    public static long getLong(byte[] buf, boolean asc) {
        if (buf == null) {
            throw new IllegalArgumentException("byte array is null!");
        }
        int len = 8;
        if (buf.length < len) {
            throw new IllegalArgumentException("byte array size mast >= 8 !");
        }
        long r = 0;
        if (asc) {
            for (int i = len - 1; i >= 0; i--) {
                r <<= 8;
                r |= (buf[i] & 0xff);
            }
        } else {
            for (int i = 0; i < len; i--) {
                r <<= 8;
                r |= (buf[i] & 0xff);
            }
        }
        return r;
    }
    
    /** 
     * 将byte转换为一个长度为8的byte数组.
     */
    public static byte[] to8Array(byte b) {
        byte[] array = new byte[8];
        /*
        for (int i = 7; i >= 0; i--) {
            array[i] = (byte)(b & 1);
            b = (byte) (b >> 1);
        }
        return array;
        */
        for (int i = 0; i <= 7; i++) {
            array[i] = (byte)(b & 1);
            b = (byte) (b >> 1);
        }
        return array;
    }
    
    /**
     * 8位差分解压.<br>
     * 原理：每个导联的第一个数据记录原始数据（2字节），其余数据为差分记录（1字节，即：8位）
     * @param data    4s的数据包（不含48字节头信息）
     * @param leadLen 有效导联个数
     * @return 解压后的数据包
     */
    public static byte[] unpackDifference(byte[] data, int leadLen) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // 每个导联的上一个值
        List<Short> preVal = new ArrayList<>(leadLen);
        for (int k = 0; k < leadLen; k++) {
            preVal.add((short) 0);
        }
        // 每个导联的第一个值（2字节存储）
        int pos = 0; // 读取data数据的位置
        for (int k = 0; k < leadLen; k++) {
            byte[] temp = new byte[]{data[pos++], data[pos++]};
            out.write(temp);
            preVal.set(k, DataUtil.getShort(temp));
        }
        // 按差分法计算剩余值（差分数值1字节存储）
        while (pos < data.length) {
            for (int k = 0; k < leadLen; k++) {
                short v = preVal.get(k); // 第K导联的上一个采样数值
                v += data[pos++];        // 第K导联的当前采样数值 = 上一数值 + 差分数值
                preVal.set(k, v);
                out.write(DataUtil.getBytes(v, true));
            }
        }
        return out.toByteArray();
    }
    
    /**
     * 12位差分解压.<br>
     * 原理：每个导联的前两个数据记录原始数据（2字节），其余数据为差分记录（1.5字节，即：12位），差分数据按212格式存储
     * @param data    4s的数据包（不含48字节头信息）
     * @param leadLen 有效导联个数
     */
    public static byte[] unpack212(byte[] data, int leadLen) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // 每个导联的上一个值
        List<Short> preVal = new ArrayList<>(leadLen);
        for (int k = 0; k < leadLen; k++) {
            preVal.add((short) 0);
        }
        // 每个导联的前两个值（每个值2字节存储）
        int pos = 0; // 读取data数据的位置
        for (int i = 0; i < 2; i++) {
            for (int k = 0; k < leadLen; k++) {
                byte[] temp = new byte[]{data[pos++], data[pos++]};
                out.write(temp);
                preVal.set(k, DataUtil.getShort(temp));
            }
        }
        
        List<Short> list1 = new ArrayList<>(leadLen); // 采样点 N   的数据
        List<Short> list2 = new ArrayList<>(leadLen); // 采样点 N+1 的数据
        while (pos < data.length) {
            list1.clear();
            list2.clear();
            // 解析一组数据
            for (int k = 0; k < leadLen; k++) {
                short val = preVal.get(k); // 第K导联的上一个采样数值
                // 212解压（即：将3个字节解压位2个差分数值）
                List<Integer> diff = unpack212(data[pos++], data[pos++], data[pos++]);
                // 第一个数值
                val += diff.get(0).shortValue();
                list1.add(val);
                // 第二个数值
                val += diff.get(1).shortValue();
                list2.add(val);
                // 最后一个数值作为下一次计算的基数
                preVal.set(k, val);
            }
            // 将数据输出
            for (Short item : list1) {
                out.write(DataUtil.getBytes(item, true));
            }
            for (Short item : list2) {
                out.write(DataUtil.getBytes(item, true));
            }
        }
        return out.toByteArray();
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
        // 第一个数值
        int aa = a & 0xff; // 将a转换为无符号类型， 0xff：1111 1111
        int ab = b & 0x0f; // b的低4位， 0x0f：0000 1111
        int v1 = ((ab << 8) | aa) & 0x7ff; // 0x7ff：‭0111 1111 1111
        // b的低4位符号位（即：b的第4位）
        if ((b & 0x08) > 0) { // 0x08：0000 1000
            v1 = -v1;
        }
        
        // 第二个数值
        int cc = c & 0xff;
        int cb = b & 0xf0; // b的高4位， 0xf0：1111 0000
        int v2 = ((cb << 4) | cc) & 0x7ff;
        // b的高4位符号位（即：b的第8位）
        if ((b & 0x80) > 0) { // 0x80：1000 0000
            v2 = -v2;
        }
        
        //返回两个值
        return Arrays.asList(v1, v2);
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
     * 212压缩（含符号位）.<br>
     * 注：会有精度损失！！
     * @param a 数据1
     * @param b 数据2
     */
    public static byte[] pack212(short a, short b) {
        //TODO
        return null;
    }
    
    /**
     * 212压缩（无符号位）.<br>
     * 注：会有精度损失！！
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
