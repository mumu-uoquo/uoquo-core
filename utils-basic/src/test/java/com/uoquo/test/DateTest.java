/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test;

import com.uoquo.utils.DateUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class DateTest {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Test
    public void test2String() {
        Date thisTime = new Date();
//        Date thisTime = DateUtil.parse("2019-02-22 00:34:23.089");
        System.out.println(DateUtil.toString(thisTime, DateUtil.FORMAT_UTC));
        System.out.println(DateUtil.toString(DateUtil.getDayStart(thisTime),   DateUtil.FORMAT_UTC));
        System.out.println(DateUtil.toString(DateUtil.getDayEnd(thisTime),     DateUtil.FORMAT_UTC));
        System.out.println(DateUtil.toString(DateUtil.getWeekStart(thisTime),  DateUtil.FORMAT_UTC));
        System.out.println(DateUtil.toString(DateUtil.getWeekEnd(thisTime),    DateUtil.FORMAT_UTC));
        System.out.println(DateUtil.toString(DateUtil.getMonthStart(thisTime), DateUtil.FORMAT_TIMESTAMP));
        System.out.println(DateUtil.toString(DateUtil.getMonthEnd(thisTime),   DateUtil.FORMAT_TIMESTAMP));
        System.out.println(DateUtil.toString(DateUtil.getYearStart(thisTime),  DateUtil.FORMAT_TIMESTAMP));
        System.out.println(DateUtil.toString(DateUtil.getYearEnd(thisTime),    DateUtil.FORMAT_TIMESTAMP));
    }

    @Test
    public void testString2Date() {
        Date date = new Date();
        // 转换到东8区（+8:00，即480分钟），格式化为"yyyy-MM-dd HH:mm:ss"
        System.out.println(DateUtil.toString(date, DateUtil.FORMAT_DATE_TIME));
        String result0 = DateUtil.toString(date, DateUtil.FORMAT_DATE_TIME, 480);
        System.out.println(result0);  // 输出东8区当前时间
        String result = DateUtil.toString(date, DateUtil.FORMAT_DATE_TIME, 0);
        System.out.println(result); // 输出西8区当前时间
        String result2 = DateUtil.toString(date, DateUtil.FORMAT_DATE_TIME, -480);
        System.out.println(result2); // 输出西8区当前时间
    }

    @Test
    public void testDate() {
        // 测试基本功能
        Date now = new Date();
        System.out.println("当前时间: " + DateUtil.toString(now, DateUtil.FORMAT_DATE_TIME));

        // 测试时区转换
        String utcTime = DateUtil.toString(now, DateUtil.FORMAT_UTC);
        System.out.println("UTC时间: " + utcTime);

        // 测试日期计算
        Date tomorrow = DateUtil.addDays(now, 1);
        System.out.println("明天: " + DateUtil.toString(tomorrow, DateUtil.FORMAT_DATE));

        // 测试解析
        String dateStr = "2024-01-15 14:30:00";
        Date parsedDate = DateUtil.parse(dateStr);
        System.out.println("解析日期: " + DateUtil.toString(parsedDate, DateUtil.FORMAT_DATE_TIME));

        // 测试时间戳
        long timestamp = now.getTime();
        Date fromTimestamp = DateUtil.parse(String.valueOf(timestamp));
        System.out.println("时间戳转换: " + DateUtil.toString(fromTimestamp, DateUtil.FORMAT_DATE_TIME));

        // 测试友好时间
        Date fiveMinutesAgo = DateUtil.addMinutes(now, -5);
        System.out.println("友好时间显示: " + DateUtil.getFriendlyTime(fiveMinutesAgo));
    }
}
