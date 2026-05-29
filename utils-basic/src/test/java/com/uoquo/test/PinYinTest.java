/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test;

import com.uoquo.utils.PinYinUtil;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class PinYinTest {

    @Test
    public void testPinYin() {
        String str = "你好";
        String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray('单');
        for (int i = 0; i < pinyinArray.length; ++i) {
            System.out.println(pinyinArray[i]);
        }
    }

    @Test
    public void testDuoYinZi() {
        HanyuPinyinOutputFormat format= new HanyuPinyinOutputFormat();
//        format.setCaseType(HanyuPinyinCaseType.UPPERCASE);
        format.setToneType(HanyuPinyinToneType.WITH_TONE_NUMBER);
        format.setVCharType(HanyuPinyinVCharType.WITH_U_UNICODE);
        try {
            String[] pinyin = PinyinHelper.toHanyuPinyinStringArray('重', format);
            System.out.println(Arrays.toString(pinyin)); // 输出 [zhong4, chong2]
        } catch (BadHanyuPinyinOutputFormatCombination e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testPinYinUtil() {
        String str = "hello 重庆A街道922B号";
        List<String> pinyinList = PinYinUtil.getPinyinList(str);
        String pinyin = PinYinUtil.getPinYin4FirstChar(str);
        System.out.println(pinyinList);
        System.out.println(pinyin);
    }
}
