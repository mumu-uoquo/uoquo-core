/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.json.jackson;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;

import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.uoquo.utils.DateUtil;
import com.uoquo.utils.StringUtil;

import org.springframework.format.annotation.DateTimeFormat;

import java.io.IOException;
import java.util.Date;

/**
 * 日期反序列化.
 * 备注：如果直接继承Deserializer，需要重写serializeWithType方法，否则会序列化出错
 */
public class DateContextualDeserializer extends StdScalarDeserializer<Date> implements ContextualDeserializer {
    protected final String patten;
    public DateContextualDeserializer() { this(null); }
    public DateContextualDeserializer(String patten) {
        super(Date.class);
        this.patten = patten;
    }

    @Override
    public Date deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_STRING)) {
            String str = parser.getText().trim();
            if (this.patten == null) {
                return DateUtil.parse(str);
            } else {
                return DateUtil.parse(str, this.patten);
            }
        }
        return null;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext prov, BeanProperty property)
            throws JsonMappingException
    {
        // jackson
        JsonFormat.Value format = findFormatOverrides(prov, property, handledType());
        if ((format != null) && format.hasPattern()) {
            return new DateContextualDeserializer(format.getPattern());
        }
        // DateTimeFormat
        if (property != null) {
            DateTimeFormat df = property.getAnnotation(DateTimeFormat.class);
            if ((df != null) && StringUtil.notNull(df.pattern())) {
                return new DateContextualDeserializer(df.pattern());
            }
        }
        return this;
    }
}