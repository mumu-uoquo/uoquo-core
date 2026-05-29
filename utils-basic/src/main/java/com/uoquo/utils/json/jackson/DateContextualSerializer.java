/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.json.jackson;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.uoquo.utils.DateUtil;
import com.uoquo.utils.StringUtil;

import org.springframework.format.annotation.DateTimeFormat;

import java.io.IOException;
import java.util.Date;

/**
 * 日期序列化.
 * 备注：如果直接继承JsonSerializer，需要重写serializeWithType方法，否则会序列化出错
 */
public class DateContextualSerializer extends StdScalarSerializer<Date> implements ContextualSerializer {
    protected final String patten;
    public DateContextualSerializer() { this(DateUtil.FORMAT_TIMESTAMP_LONG); }
    public DateContextualSerializer(String patten) {
        super(Date.class);
        this.patten = patten;
    }

    @Override
    public void serialize(Date value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        String text = DateUtil.toString(value, this.patten);
        jgen.writeString(text);
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
            throws JsonMappingException
    {
        // jackson
        JsonFormat.Value format = findFormatOverrides(prov, property, handledType());
        if ((format != null) && format.hasPattern()) {
            return new DateContextualSerializer(format.getPattern());
        }
        // DateTimeFormat
        if (property != null) {
            DateTimeFormat df = property.getAnnotation(DateTimeFormat.class);
            if ((df != null) && StringUtil.notNull(df.pattern())) {
                return new DateContextualSerializer(df.pattern());
            }
        }
        //return new DateContextualSerializer();
        return this;
    }

}