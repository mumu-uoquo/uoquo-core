/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils;
import java.util.*;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 描述：拼音工具类
 * @author uoquo
 */
public class PinYinUtil {
    private static final Logger log = LoggerFactory.getLogger(PinYinUtil.class);

    /**
     * 获取拼音首字母
     * @param src  中文字串
     * @return 所有文字的首字母字符串
     */
    public static String getPinYin4FirstChar(String src) {
        List<String> list = getPinyinList(src);
        // 提取首字母
        StringBuilder sb = new StringBuilder();
        for (String str : list) {
            sb.append(str.charAt(0));
        }
        return sb.toString().toUpperCase();
    }

    /**
     * 获取汉字拼音（多音字获取首个拼音）
     * @param src  中文字串
     * @return 拼音集合
     */
    public static List<String> getPinyinList(String src) {
        List<String> result = new ArrayList<>();
        if (StringUtil.isNull(src)) {
            return result;
        }
        // 汉语拼音格式（大小写，音标方式等）
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_U_AND_COLON);
        // 拼音转换
        char[] srcChar = src.toCharArray();
        StringBuilder item = new StringBuilder();
        for (char c : srcChar) {
            if (String.valueOf(c).matches("[\\u4E00-\\u9FA5]+")) {
                // 中文
                if (!item.isEmpty()) {
                    result.add(item.toString());
                    item.setLength(0);
                }
                try {
                    String[] temp = PinyinHelper.toHanyuPinyinStringArray(c, format);
                    if (log.isDebugEnabled()) {
                        log.debug("汉字[{}]转拼音{}", c, Arrays.toString(temp));
                    }
                    result.add(temp[0]);
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    log.warn("汉字[{}]转拼音出错", c, e);
                }
            } else if (((int) c >= 65 && (int) c <= 90)
                    || ((int) c >= 97 && (int) c <= 122)) {
                // 英文
                item.append(c);
            } else if ((int) c >= 48 && (int) c <= 57) {
                // 数字
                item.append(c);
            } else {
                if (!item.isEmpty()) {
                    result.add(item.toString());
                    item.setLength(0);
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("字符串[{}]转拼音集合{}", src, result);
        }
        return result;
    }

}