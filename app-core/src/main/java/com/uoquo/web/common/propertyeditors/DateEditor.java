/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.common.propertyeditors;

import com.uoquo.utils.DateUtil;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.jspecify.annotations.Nullable;

/**
 * 描述：spring解析日期型参数处理类. <br>
 * 日期：2018-01-30 19:06 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-30     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class DateEditor extends CustomDateEditor {

    public DateEditor() {
        super(new SimpleDateFormat(DateUtil.FORMAT_TIMESTAMP), true);
    }
    
    @Override
    public void setAsText(@Nullable String text) throws IllegalArgumentException {
        String input = (text != null ? text.trim() : null);
        try {
            Date date = DateUtil.parse(input);
            input = DateUtil.toString(date, DateUtil.FORMAT_TIMESTAMP);
        } catch (Exception e) {
        }
        super.setAsText(input);
    }
}
