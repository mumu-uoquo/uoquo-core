/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 描述：拼音工具类. <br>
 * 将字符串拆分为"词"的列表，规则：
 * <ul>
 *   <li>每个汉字转为一个小写的、不带音调的拼音（多音字取第一个读音），如：超 → chao.</li>
 *   <li>连续的英文字母为一个单词，保持原有大小写，如：admin、A.</li>
 *   <li>连续的数字为一个数字串，数字之间的连字符“-”保留在数字串中，如：37、1-203.
 *       英文字母与数字相邻时分开，如：admin1 → admin、1.</li>
 *   <li>全角的英文字母、数字按半角处理.</li>
 *   <li>其他字符（空格、标点、符号、表情等）作为分隔符，不输出；没有拼音的汉字同样作为分隔符.</li>
 * </ul>
 * 例：<br>
 * 超前路37号A座1-203室 → [chao, qian, lu, 37, hao, A, zuo, 1-203, shi]，首字母：CQL3HAZ1S<br>
 * 张三admin 1 → [zhang, san, admin, 1]，首字母：ZSA1<br>
 * 日期：2025 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2025           uoquo            创建
 * 1.1          2026-11-10     uoquo team       英文单词与数字分开；数字间的连字符保留；支持全角字符；
 *                                              修复无拼音的汉字NPE；去掉逐字符的正则匹配；ü输出为v，不再输出“u:”
 * </pre>
 * @version 1.1
 * @author uoquo
 */
public class PinYinUtil {
    private static final Logger log = LoggerFactory.getLogger(PinYinUtil.class);

    /** 汉语拼音格式：小写、无音调、ü输出为v（如：绿 → lv）. 创建后不再修改，可以被多线程共用. */
    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();

    /** 汉字的拼音缓存（没有拼音的汉字保存为空串）. */
    private static final Map<Character, String> PINYIN_CACHE = new ConcurrentHashMap<>();
    private static final String NO_PINYIN = "";

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    /**
     * 获取拼音和单词的首字母（大写）.<br>
     * 每个拼音、英文单词、数字串各取第一个字符，如：张三admin 1 → ZSA1.
     * @param src  中文字串
     * @return 首字母字符串；空串返回空字符串
     */
    public static String getPinYin4FirstChar(String src) {
        List<String> list = getPinyinList(src);
        StringBuilder sb = new StringBuilder(list.size());
        for (String item : list) {
            sb.append(Character.toUpperCase(item.charAt(0)));
        }
        return sb.toString();
    }

    /**
     * 获取字串中汉字的拼音、英文单词和数字（多音字获取首个拼音），规则见类说明.<br>
     * 如：超前路37号A座1-203室 → [chao, qian, lu, 37, hao, A, zuo, 1-203, shi]
     * @param src  中文字串
     * @return 拼音、英文单词、数字串的集合；空串返回空集合
     */
    public static List<String> getPinyinList(String src) {
        List<String> result = new ArrayList<>();
        if (StringUtil.isNull(src)) {
            return result;
        }
        final int kindNone = 0, kindLetter = 1, kindDigit = 2;
        StringBuilder item = new StringBuilder();
        int kind = kindNone; // 当前正在累积的内容类型
        int len  = src.length();
        for (int i = 0; i < len; ) {
            int raw = src.codePointAt(i);
            int cp  = toHalfWidth(raw);
            i += Character.charCount(raw);
            if (isAsciiLetter(cp)) { // 英文
                if (kind != kindLetter) {
                    flush(result, item);
                    kind = kindLetter;
                }
                item.append((char) cp);
            } else if (isAsciiDigit(cp)) { // 数字
                if (kind != kindDigit) {
                    flush(result, item);
                    kind = kindDigit;
                }
                item.append((char) cp);
            } else if ((cp == '-') && (kind == kindDigit) && (i < len) && isAsciiDigit(toHalfWidth(src.codePointAt(i)))) {
                item.append('-'); // 数字之间的连字符（如：1-203）
            } else {
                flush(result, item);
                kind = kindNone;
                if (isHan(cp)) { // 中文
                    String pinyin = toPinyin((char) cp);
                    if (pinyin != null) {
                        result.add(pinyin);
                    }
                }
            }
        }
        flush(result, item);
        if (log.isDebugEnabled()) {
            log.debug("字符串[{}]转拼音集合{}", src, result);
        }
        return result;
    }

    /**
     * 汉字转拼音（多音字取第一个）.
     * @return 拼音，没有拼音则返回null
     */
    private static String toPinyin(char c) {
        // pinyin4j每次查询都要解析字典数据，较慢；汉字总数有限（BMP内约2万个），缓存查询结果
        String pinyin = PINYIN_CACHE.computeIfAbsent(c, key -> {
            try {
                String[] pinyins = PinyinHelper.toHanyuPinyinStringArray(key, FORMAT);
                return ((pinyins == null) || (pinyins.length == 0)) ? NO_PINYIN : pinyins[0];
            } catch (BadHanyuPinyinOutputFormatCombination e) {
                log.warn("汉字[{}]转拼音出错", key, e);
                return NO_PINYIN;
            }
        });
        return pinyin.isEmpty() ? null : pinyin;
    }

    /** 将已累积的内容作为一个词输出. */
    private static void flush(List<String> result, StringBuilder item) {
        if (item.length() > 0) {
            result.add(item.toString());
            item.setLength(0);
        }
    }

    /** 是否基本平面（BMP）内的汉字（pinyin4j只支持BMP字符）. */
    private static boolean isHan(int cp) {
        return (cp <= 0xFFFF) && (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    private static boolean isAsciiLetter(int cp) {
        return (cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z');
    }

    private static boolean isAsciiDigit(int cp) {
        return cp >= '0' && cp <= '9';
    }

    /** 全角的英文字母、数字、连字符转为半角. */
    private static int toHalfWidth(int cp) {
        if ((cp >= 0xFF21 && cp <= 0xFF3A) || (cp >= 0xFF41 && cp <= 0xFF5A) || (cp >= 0xFF10 && cp <= 0xFF19)) {
            return cp - 0xFEE0; // 全角ＡＺａｚ０９ 与 ASCII 相差 0xFEE0
        }
        return (cp == 0xFF0D) ? '-' : cp; // 全角连字符“－”
    }
}
