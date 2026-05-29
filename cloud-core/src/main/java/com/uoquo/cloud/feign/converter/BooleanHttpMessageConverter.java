/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.feign.converter;

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
 * 描述：boolean处理. <br>
 * 日期：2018-03-21 20:58 <br>
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
public class BooleanHttpMessageConverter extends AbstractHttpMessageConverter<Boolean> {

    private final Charset charset;
    
    public BooleanHttpMessageConverter() {
        this.charset = StandardCharsets.UTF_8;
    }
    
    @Override
    protected boolean supports(@NonNull Class<?> clazz) {
        return Boolean.class == clazz;
    }

    @NonNull
    @Override
    protected Boolean readInternal(@NonNull Class<? extends Boolean> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        String text = StreamUtils.copyToString(inputMessage.getBody(), charset);
        return Boolean.parseBoolean(text);
    }

    @Override
    protected void writeInternal(Boolean t, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        StreamUtils.copy(t.toString(), charset, outputMessage.getBody());
    }
}
