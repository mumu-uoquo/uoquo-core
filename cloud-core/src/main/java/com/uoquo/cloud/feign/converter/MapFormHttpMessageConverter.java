/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.feign.converter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

/**
 * 描述：将Map转为 www-form-urlencoded 发送数据. <br>
 * 日期：2018-03-12 21:49 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-03-12     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class MapFormHttpMessageConverter implements HttpMessageConverter<Map<String, ?>> {

    protected FormHttpMessageConverter formHttpMessageConverter;

    public MapFormHttpMessageConverter() {
        this.formHttpMessageConverter = new MultipartFormHttpMessageConverter();
    }
    
    public void addPartConverter(HttpMessageConverter<?> partConverter) {
        this.formHttpMessageConverter.addPartConverter(partConverter);
    }
    
    @Override
    public boolean canRead(@NonNull Class<?> clazz, MediaType mediaType) {
        return formHttpMessageConverter.canRead(clazz, mediaType);
    }

    @NonNull
    @Override
    public List<MediaType> getSupportedMediaTypes() {
        return formHttpMessageConverter.getSupportedMediaTypes();
    }
    
    @Override
    public boolean canWrite(@NonNull Class<?> clazz, MediaType mediaType) {
        if (!Map.class.isAssignableFrom(clazz)) {
            return false;
        }
        if (mediaType == null || MediaType.ALL.equals(mediaType)) {
            return true;
        }
        for (MediaType supportedMediaType : getSupportedMediaTypes()) {
            if (supportedMediaType.isCompatibleWith(mediaType)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    @Override
    public Map<String, ?> read(@NonNull Class<? extends Map<String, ?>> clazz, @NonNull HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return formHttpMessageConverter.read(null, inputMessage);
    }
    
    @Override
    public void write(@NonNull Map<String, ?> map, MediaType contentType, @NonNull HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        MultiValueMap<String, Object> multiMap = null;
        if (map != null) {
            if (map instanceof MultiValueMap) {
                multiMap = (MultiValueMap<String, Object>) map;
            } else {
                multiMap = new LinkedMultiValueMap<>();
                for (Map.Entry<String, ?> entry : map.entrySet()) {
                    multiMap.add(entry.getKey(), entry.getValue());
                }
            }
        }
        formHttpMessageConverter.write(multiMap, contentType, outputMessage);
    }
    
    /**
     * 自定义Form表单处理.
     */
    public static class MultipartFormHttpMessageConverter extends FormHttpMessageConverter {
        
        /**
         * 构造函数.
         */
        public MultipartFormHttpMessageConverter() {
            super();
            // 日期
            addPartConverter(new DateHttpMessageConverter());
            // 数字
            addPartConverter(new NumberHttpMessageConverter());
            // boolean
            addPartConverter(new BooleanHttpMessageConverter());
            
            setCharset(StandardCharsets.UTF_8);
        }
        
        @Override
        protected String getFilename(@NonNull Object part) {
            String rt = super.getFilename(part);
            if ((rt == null) && (part instanceof MultipartFile)) {
                return ((MultipartFile) part).getOriginalFilename();
            }
            return null;
        }
    }
}
