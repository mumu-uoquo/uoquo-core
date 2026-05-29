/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.feign.processor;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.springframework.format.Formatter;

import com.uoquo.utils.DateUtil;

/**
 * 描述：feign日期参数格式化. <br>
 * 日期：2018-04-09 15:43 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-04-09     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class DateFormatter implements Formatter<Date> {
    
    @Override
    public Date parse(String text, Locale locale) throws ParseException {
        return DateUtil.parse(text);
    }

    @Override
    public String print(Date date, Locale locale) {
        if (date == null) {
            return "";
        }
        SimpleDateFormat sdf = new SimpleDateFormat(DateUtil.FORMAT_TIMESTAMP_LONG);
        return sdf.format(date);
    }
}
