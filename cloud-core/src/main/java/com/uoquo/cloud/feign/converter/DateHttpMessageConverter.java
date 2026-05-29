/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.feign.converter;

import com.uoquo.utils.DateUtil;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.StreamUtils;

/**
 * 描述：日期处理. <br>
 * 日期：2018-03-21 20:19 <br>
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
public class DateHttpMessageConverter extends AbstractHttpMessageConverter<Date> {

    private final Charset charset;
    
    public DateHttpMessageConverter() {
        this.charset = StandardCharsets.UTF_8;
    }
    
    @Override
    protected boolean supports(@NonNull Class<?> clazz) {
        return Date.class == clazz;
    }

    @NonNull
    @Override
    protected Date readInternal(@NonNull Class<? extends Date> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        String text = StreamUtils.copyToString(inputMessage.getBody(), charset);
        return Objects.requireNonNull(DateUtil.parse(text));
    }

    @Override
    protected void writeInternal(Date t, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        String str = (t == null) ? null : String.valueOf(t.getTime()); // 采用时间戳，保证传递过程中不失真
        StreamUtils.copy(str, charset, outputMessage.getBody());
    }
}
