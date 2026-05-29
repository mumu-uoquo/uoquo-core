/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.json.jackson;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.CacheProvider;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.databind.ser.SerializerFactory;

import java.io.Serial;

public class StringNullSerializerProvider extends DefaultSerializerProvider {
    @Serial
    private static final long serialVersionUID = 1L;

    public StringNullSerializerProvider() {
    }

    public StringNullSerializerProvider(StringNullSerializerProvider src) {
        super(src);
    }

    public StringNullSerializerProvider(StringNullSerializerProvider src, CacheProvider cacheProvider) {
        super(src, cacheProvider);
    }

    public StringNullSerializerProvider(SerializerProvider src, SerializationConfig config, SerializerFactory f) {
        super(src, config, f);
    }

    @Override
    public DefaultSerializerProvider copy() {
        return (this.getClass() != StringNullSerializerProvider.class ? super.copy() : new StringNullSerializerProvider(this));
    }

    @Override
    public DefaultSerializerProvider withCaches(CacheProvider cacheProvider) {
        return new StringNullSerializerProvider(this, cacheProvider);
    }

    @Override
    public StringNullSerializerProvider createInstance(SerializationConfig config, SerializerFactory jsf) {
        return new StringNullSerializerProvider(this, config, jsf);
    }

    @Override
    public JsonSerializer<Object> findNullValueSerializer(BeanProperty property) throws JsonMappingException {
        if (property.getType().getRawClass().equals(String.class)) {
            return StringNullSerializer.INSTANCE;
        } else {
            return super.findNullValueSerializer(property);
        }
    }
}
