/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * 完整CRC校验工具类
 * 支持所有主流CRC-8、CRC-16标准模式，同时支持自定义参数，兼容Java 8+
 */
public class CRCUtil {

    // -------------------- 预定义CRC模式参数常量 --------------------
    // CRC-8 标准参数
    public static final CRCParams CRC8_STANDARD  = new CRCParams(8, 0x07, 0x00, false, false, 0x00);
    public static final CRCParams CRC8_ITU       = new CRCParams(8, 0x07, 0x00, false, false, 0x55);
    public static final CRCParams CRC8_CDMA2000  = new CRCParams(8, 0x9B, 0xFF, false, false, 0x00);
    public static final CRCParams CRC8_DARC      = new CRCParams(8, 0x39, 0x00, true, true, 0x00);
    public static final CRCParams CRC8_SAE_J1850 = new CRCParams(8, 0x1D, 0xFF, false, false, 0x00);
    public static final CRCParams CRC8_MAXIM     = new CRCParams(8, 0x31, 0x00, true, true, 0x00);

    // CRC-16 标准参数
    public static final CRCParams CRC16_IBM          = new CRCParams(16, 0x8005, 0x0000, true, true, 0x0000);
    public static final CRCParams CRC16_MODBUS       = new CRCParams(16, 0x8005, 0xFFFF, true, true, 0x0000);
    public static final CRCParams CRC16_CCITT_XMODEM = new CRCParams(16, 0x1021, 0x0000, false, false, 0x0000);
    public static final CRCParams CRC16_CCITT_FALSE  = new CRCParams(16, 0x1021, 0xFFFF, false, false, 0x0000);
    public static final CRCParams CRC16_CCITT_KERMIT = new CRCParams(16, 0x1021, 0x0000, true, true, 0x0000);
    public static final CRCParams CRC16_USB          = new CRCParams(16, 0x8005, 0xFFFF, true, true, 0xFFFF);
    public static final CRCParams CRC16_DNP          = new CRCParams(16, 0x3D65, 0x0000, true, true, 0xFFFF);
    public static final CRCParams CRC16_MAXIM        = new CRCParams(16, 0x8005, 0x0000, true, true, 0xFFFF);

    // 预存查表缓存，避免重复生成
    private static final Map<Integer, int[]> TABLE_CACHE_8  = new HashMap<>();
    private static final Map<Integer, int[]> TABLE_CACHE_16 = new HashMap<>();

    // ==================== 公共API：标准模式快速调用 ====================
    /**
     * CRC-8 标准计算（默认标准：0x07多项式+0x00初始值）
     * 注：需要无符号值时，通过掩码还原（crcByte & 0xFF）
     */
    public static byte crc8Standard(byte[] data) {
        // 获取CRC-8结果（返回int，低8位存储完整结果）
        int crcResult = calculateByTable(data, CRC8_STANDARD);
        // 强转byte：仅保留低8位二进制，不丢失数据，语法完全可行
        return (byte) (crcResult & 0xFF);
    }
    public static byte crc8Standard(String data) {
        return crc8Standard(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * CRC-16 Modbus 工业标准计算
     * 注：需要无符号值时，通过掩码还原（crcShort & 0xFFFF）
     */
    public static short crc16Modbus(byte[] data) {
        // 获取CRC-16结果（返回int，低16位存储完整结果）
        int crcResult = calculateByTable(data, CRC16_MODBUS);
        // 强转short：仅保留低16位二进制，不丢失数据，语法完全可行
        return (short) (crcResult & 0xFFFF);
    }
    public static short crc16Modbus(String data) {
        return crc16Modbus(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * CRC-16 CCITT 通信标准计算
     */
    public static short crc16CCITT(byte[] data) {
        // 获取CRC-16结果（返回int，低16位存储完整结果）
        int crcResult = calculateByTable(data, CRC16_CCITT_FALSE);
        // 强转short：仅保留低16位二进制，不丢失数据，语法完全可行
        return (short) (crcResult & 0xFFFF);
    }
    public static short crc16CCITT(String data) {
        return crc16CCITT(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * JDK内置CRC32快速计算
     */
    public static long crc32(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        return crc32.getValue();
    }

    // ==================== 通用计算方法 ====================
    /**
     * 按指定CRC参数模型计算（查表法，性能更高，推荐生产环境使用）
     */
    public static int calculateByTable(byte[] data, CRCParams params) {
        int[] table = getLookupTable(params);
        int crc = params.init;
        
        for (byte b : data) {
            int currentByte = params.refIn ? reverseByte(b & 0xFF, 8) : (b & 0xFF);
            if (params.width == 8) {
                crc = table[(crc ^ currentByte) & 0xFF];
            } else if (params.width == 16) {
                crc = (crc << 8) ^ table[((crc >> 8) ^ currentByte) & 0xFF];
            }
        }
        
        if (params.refOut) {
            crc = reverseBits(crc, params.width);
        }
        return (crc ^ params.xorOut) & getMask(params.width);
    }

    /**
     * 逐位计算（直观易读，适合原理学习/调试）
     */
    public static int calculateByBit(byte[] data, CRCParams params) {
        int crc = params.init;
        int mask = 1 << (params.width - 1);
        int poly = params.poly;
        
        for (byte b : data) {
            int currentByte = params.refIn ? reverseByte(b & 0xFF, 8) : (b & 0xFF);
            crc ^= (currentByte << (params.width - 8));
            
            for (int i = 0; i < 8; i++) {
                if ((crc & mask) != 0) {
                    crc = (crc << 1) ^ poly;
                } else {
                    crc <<= 1;
                }
            }
        }
        
        if (params.refOut) {
            crc = reverseBits(crc, params.width);
        }
        return (crc ^ params.xorOut) & getMask(params.width);
    }

    /**
     * 计算文件的CRC值（支持任意CRC模式，大文件分块处理不占用内存）
     */
    public static long calculateFileCRC(File file, CRCParams params) throws IOException {
        int[] table = getLookupTable(params);
        long crc = params.init;
        int mask = getMask(params.width);
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                for (int i = 0; i < len; i++) {
                    byte b = buffer[i];
                    int currentByte = params.refIn ? reverseByte(b & 0xFF, 8) : (b & 0xFF);
                    if (params.width == 8) {
                        crc = table[(int) ((crc ^ currentByte) & 0xFF)];
                    } else if (params.width == 16) {
                        crc = (crc << 8) ^ table[(int) (((crc >> 8) ^ currentByte) & 0xFF)];
                    }
                }
            }
        }
        
        if (params.refOut) {
            crc = reverseBits((int)crc, params.width);
        }
        return (crc ^ params.xorOut) & mask;
    }

    // ==================== 内部工具方法 ====================
    // 获取掩码（对应位宽的全1掩码，用于截断多余高位）
    private static int getMask(int width) {
        return (width == 8) ? 0xFF : ((1 << width) - 1);
    }

    // 反转字节指定位数的比特顺序
    private static int reverseByte(int b, int bits) {
        int result = 0;
        for (int i = 0; i < bits; i++) {
            if ((b & (1 << i)) != 0) {
                result |= 1 << (bits - 1 - i);
            }
        }
        return result;
    }

    // 反转整个CRC值的比特顺序
    private static int reverseBits(int value, int bits) {
        int result = 0;
        for (int i = 0; i < bits; i++) {
            if ((value & (1 << i)) != 0) {
                result |= 1 << (bits - 1 - i);
            }
        }
        return result;
    }

    // 获取预计算的查表，缓存避免重复生成
    private static int[] getLookupTable(CRCParams params) {
        Map<Integer, int[]> cache = params.width == 8 ? TABLE_CACHE_8 : TABLE_CACHE_16;
        int key = params.poly;
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        
        int[] table = new int[256];
        int poly = params.poly;
        int width = params.width;
        
        for (int i = 0; i < 256; i++) {
            int crc = i << (width - 8);
            for (int j = 0; j < 8; j++) {
                if ((crc & (1 << (width - 1))) != 0) {
                    crc = (crc << 1) ^ poly;
                } else {
                    crc <<= 1;
                }
            }
            table[i] = crc & getMask(width);
        }
        
        cache.put(key, table);
        return table;
    }

    // ==================== CRC参数模型实体类 ====================
    public static class CRCParams {
        public final int width;     // CRC位宽：8或16
        public final int poly;      // 生成多项式
        public final int init;      // 初始值
        public final boolean refIn; // 是否反转输入字节
        public final boolean refOut;// 是否反转输出CRC
        public final int xorOut;    // 输出异或值

        public CRCParams(int width, int poly, int init, boolean refIn, boolean refOut, int xorOut) {
            this.width = width;
            this.poly = poly;
            this.init = init;
            this.refIn = refIn;
            this.refOut = refOut;
            this.xorOut = xorOut;
        }
    }
}
