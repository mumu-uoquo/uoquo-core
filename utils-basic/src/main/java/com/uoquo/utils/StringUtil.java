/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 描述：字符串工具类. <br>
 * 日期：2018-01-18 16:28 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-18     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class StringUtil {
    private static final Logger log = LoggerFactory.getLogger(StringUtil.class);

    public static final byte[] EMPTY_BYTE_ARRAY = {};

    /**
     * 十六进制字符
     */
    private static final char[] HEX_CHAR = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    /**
     * 随机字符串初始值
     */
    private static final Map<Integer, String> RANDOM_CHAR = new HashMap<>();
    static {
        RANDOM_CHAR.put(1, "0123456789");
        RANDOM_CHAR.put(2, "abcdefghijklmnopqrstuvwxyz");
        RANDOM_CHAR.put(3, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        RANDOM_CHAR.put(4, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
        RANDOM_CHAR.put(5, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
        RANDOM_CHAR.put(9999, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&()_-.><{}[]|/,=");
    }

    /**
     * 检测字符串是否为空（或者“null”字符串）.
     * @param str 待检测字符串
     * @return true：null、"null"、空字符串，false：其他
     */
    public static boolean isNull(Object str) {
        if (str == null) {
            return true;
        }
        String temp = str.toString();
        return (temp.trim().isEmpty()) || ("null".equalsIgnoreCase(temp.trim()));
    }

    /**
     * 检测字符串是否为空（或者“null”字符串）.
     * @param str 待检测字符串
     * @return false：null、"null"、空字符串，true：其他
     */
    public static boolean notNull(Object str) {
        return !isNull(str);
    }
    
    /**
     * 取字符串的前toCount个字符.<br>
     * 支持中文
     * @param str  被处理字符串
     * @param len  截取后的长度
     * @param suffix 后缀字符串
     * @return 截取后的字符串
     */
    public static String subString(String str, int len, String suffix) {
        if (isNull(str)) {
            return "";
        }
        // 字符串长度小于等于要保留的长度，返回原字串
        char[] tempChar = str.toCharArray();
        if (len >= tempChar.length) {
            return str;
        }
        // 字符串长度大于要保留的长度，截取字串
        StringBuilder reStr = new StringBuilder();
        int reInt = 0;
        for (int kk = 0; (kk < tempChar.length && len > reInt); kk++) {
            String s1 = String.valueOf(tempChar[kk]);
            byte[] b = s1.getBytes(StandardCharsets.UTF_8);
            reInt += b.length;
            reStr.append(tempChar[kk]);
        }
        if (len == reInt || (len == reInt - 1) || (len < reInt)) {
            reStr.append(suffix);
        }
        return reStr.toString();
    }

    /**
     * 将字符串按逗号或分号分割.
     * @param str 待分割字符串
     * @return 分割后的字符串数组
     */
    public static List<String> split(String str) {
        return split(str, ",;");
    }

    /**
     * 将字符串按多个分隔符进行切分.
     * <p>
     * 示例：StringUtils.split("1,2;3 4", " ,;");
     * 返回: ["1","2","3","4"]
     * </p>
     * 
     * @param str 待分割字符串
     * @param seperators 分割符（如“,;”）
     * @return 分割后的字符串数组
     */
    public static List<String> split(String str, String seperators) {
        List<String> result = new ArrayList<>();
        StringTokenizer tokenlizer = new StringTokenizer(str, seperators);
        while (tokenlizer.hasMoreElements()) {
            Object temp = tokenlizer.nextElement();
            result.add(temp.toString());
        }
        return result;
    }
    
    /**
     * 将多个对象拼接为SQL使用的字符串.<br>
     * 注：会将字符串前后添加单引号
     * @param list 待拼接的 list
     * @return 拼接后的字符串
     */
    public static String join4Sql(List<?> list) {
        if ((list == null) || list.isEmpty()) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            if (item instanceof String) {
                sb.append("'").append(item).append("'");
            } else if (item instanceof Number) {
                sb.append(item);
            } else {
                sb.append("'").append(item).append("'");
            }
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        
        return sb.toString();
    }

    /**
     * 随机字符串（混合）.
     * @param len  长度
     * @return 混合随机字符串
     */
    public static String getRandomString(int len) {
        return getRandomString(len, null);
    }

    /**
     * 随机字符串. <br>
     * 类型（1:数字、2:小写字母、3：大写字母、4：所有字母、5：字母+数字、其他（默认）：字母+数字+特殊字符）
     * @param len  长度
     * @param type 类型（1:数字、2:小写字母、3：大写字母、4：所有字母、5：字母+数字、其他（默认）：字母+数字+特殊字符）
     * @return 随机字符串
     */
    public static String getRandomString(int len, Integer type) {
        if (type == null) {
            type = 9999;
        }
        // 基础字串
        String base = RANDOM_CHAR.get(type);
        if (base == null) {
            base = RANDOM_CHAR.get(9999);
        }
        // 随机处理
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int number = random.nextInt(base.length());
            sb.append(base.charAt(number));
        }
        return sb.toString();
    }

    /**
     * 通用脱敏
     * @param value 待脱敏的字符串
     * @param prefixLen 前缀保留长度
     * @param suffixLen 后缀保留长度
     */
    public static String desensitize(String value, int prefixLen, int suffixLen) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (prefixLen < 0) {
            prefixLen = 1;
        }
        if (suffixLen < 0) {
            suffixLen = 1;
        }
        String replacement = "*";
        int len = prefixLen + suffixLen;
        if (value.length() < prefixLen) {
            return replacement.repeat(value.length());
        } else if (value.length() <= len) {
            return value.substring(0, prefixLen) + replacement.repeat(value.length() - prefixLen);
        }
        int replacementLen = value.length() - len;
        return value.substring(0, prefixLen) + replacement.repeat(replacementLen) + value.substring(value.length() - suffixLen);
    }

    /**
     * 转换成十六进制字符串.
     * @param data 待转换的byte
     * @return 转换后的字符串
     */
    public static String byte2hex(byte data) {
        return String.valueOf(HEX_CHAR[(data & 0xf0) >>> 4]) + HEX_CHAR[data & 0x0f];
    }

    /**
     * 转换成十六进制字符串.
     * @param data 待转换的byte数组
     * @return 转换后的字符串
     */
    public static String byte2hex(byte[] data) {
        if ((data == null) || (data.length == 0)) {
            return null;
        }

        StringBuilder temp = new StringBuilder(data.length * 2);
        for (byte datum : data) {
            // temp.append(String.format("%02x", data[i]));
            // 经测试，位移的方式快30多倍
            temp.append(HEX_CHAR[(datum & 0xf0) >>> 4]);
            temp.append(HEX_CHAR[datum & 0x0f]);
        }
        return temp.toString();
    }

    /**
     * 字符串转换为十六进制byte数组.
     * @param strhex 待转换的字符串
     * @return 转换后的十六进制byte数组
     */
    public static byte[] hex2byte(String strhex) {
        if (isNull(strhex)) {
            return EMPTY_BYTE_ARRAY;
        }
        if (strhex.length() % 2 == 1) {
            log.warn("字符串[{}]不是偶数长度，自动首位补0", strhex);
            strhex = "0" + strhex;
        }

        char[] chars = strhex.toLowerCase().toCharArray();
        byte[] bytes = new byte[chars.length / 2];
        for (int i = 0, k = 0; i < chars.length; ) {
            // bytes[i] = (byte) Integer.parseInt(strhex.substring(i++, ++i), 16);
            // 经测试，位移的方式快4倍左右
            int m = hexChar2Decimal(chars[i++]);
            int n = hexChar2Decimal(chars[i++]);
            bytes[k++] = (byte)((m << 4) | n);
        }
        return bytes;
    }

    /**
     * 16进制字符转换为10进制数.
     * @param n 待转换的16进制字符
     * @return 转换后的十进制数
     */
    public static int hexChar2Decimal(char n) {
        if (n >= '0' && n <= '9') {
            return n - '0';
        } else if (n >= 'a' && n <= 'z') {
            return n - 'a' + 10;
        } else if (n >= 'A' && n <= 'Z') {
            return n - 'A' + 10;
        } else {
            throw new IllegalArgumentException("传入的非16进制字符，无法转换为byte数组");
        }
    }

    /**
     * 输出二进制字符串
     * @param val 待转换的long值
     */
    public static String toBinaryString(long val) {
        return String.format("%64s", Long.toBinaryString(val)).replace(' ', '0');
    }

    /**
     * 输出二进制字符串
     * @param val 待转换的int值
     */
    public static String toBinaryString(int val) {
        return String.format("%32s", Integer.toBinaryString(val)).replace(' ', '0');
    }

    /**
     * 输出二进制字符串
     * @param val 待转换的short值
     */
    public static String toBinaryString(short val) {
        return String.format("%16s", Integer.toBinaryString(val & 0xFFFF)).replace(' ', '0');
    }

    /**
     * 输出二进制字符串
     * @param val 待转换的byte值
     */
    public static String toBinaryString(byte val) {
        return String.format("%16s", Integer.toBinaryString(val & 0xFF)).replace(' ', '0');
    }
    
    /**
     * 将异常堆栈拼接位字符串.<br>
     * @param ex 异常信息
     */
    public static String getStackTrace(Throwable ex) {
        if (ex == null) {
            return null;
        }

        try (
            StringWriter sw = new StringWriter();
            PrintWriter  pw = new PrintWriter(sw);
        ) {
            ex.printStackTrace(pw);
            return sw.toString();
        } catch (Exception e) {
            log.warn("转换异常信息为字符串出错. {}", ex, e);
            return null;
        }
    }
}
