/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.feign.converter;

import com.uoquo.utils.StringUtil;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.StreamUtils;

/**
 * 描述：数字处理. <br>
 * 日期：2018-03-21 20:29 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-03-21     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class NumberHttpMessageConverter extends AbstractHttpMessageConverter<Number> {

    private final Charset charset;
    
    public NumberHttpMessageConverter() {
        this.charset = StandardCharsets.UTF_8;
    }
    
    @Override
    protected boolean supports(@NonNull Class<?> clazz) {
        return Number.class.isAssignableFrom(clazz);
    }

    @Override
    protected Number readInternal(@NonNull Class<? extends Number> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        String text = StreamUtils.copyToString(inputMessage.getBody(), charset);
        if (StringUtil.isNull(text)) {
            return null;
        }
        try {
            if (Long.class.isAssignableFrom(clazz)) {
                return Long.parseLong(text);
            } else if (Double.class.isAssignableFrom(clazz)) {
                return Double.parseDouble(text);
            } else if (Integer.class.isAssignableFrom(clazz)) {
                return Integer.parseInt(text);
            } else if (Short.class.isAssignableFrom(clazz)) {
                return Short.parseShort(text);
            } else if (Float.class.isAssignableFrom(clazz)) {
                return Float.parseFloat(text);
            }
        } catch (Exception e) {
            // do nothing
        }
        return null;
    }

    @Override
    protected void writeInternal(Number t, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        String str = String.valueOf(t);
        StreamUtils.copy(str, charset, outputMessage.getBody());
    }
}
