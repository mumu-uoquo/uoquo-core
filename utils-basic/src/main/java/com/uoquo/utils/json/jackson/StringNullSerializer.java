/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.json.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

import java.io.IOException;

public class StringNullSerializer extends StdScalarSerializer<Object> {

    public static String EMPTY_STRING = "";
    public static StringNullSerializer INSTANCE = new StringNullSerializer();

    public StringNullSerializer() {
        super(Object.class);
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeString(EMPTY_STRING);
        } else {
            gen.writeString(value.toString());
        }
    }
    @Override
    public final void serializeWithType(Object value, JsonGenerator gen, SerializerProvider provider, TypeSerializer typeSer) throws IOException {
        if (value == null) {
            gen.writeString(EMPTY_STRING);
        } else {
            gen.writeString(value.toString());
        }
    }
}
