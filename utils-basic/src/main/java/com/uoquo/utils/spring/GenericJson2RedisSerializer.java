/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.spring;

import com.uoquo.utils.json.JsonUtil;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;

import org.jspecify.annotations.Nullable;

/**
 * 自定义 REDIS 数据的json序列化方法
 * 配合 RedisUtil 使用
 */
public class GenericJson2RedisSerializer implements RedisSerializer<Object> {

    static final byte[] EMPTY_ARRAY = new byte[0];

    @Override
    public byte[] serialize(@Nullable Object source) throws SerializationException {
        if (source == null) {
            return EMPTY_ARRAY;
        } else if (source instanceof byte[] bytes) {
            return bytes;
        }

        try (
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            OutputStreamWriter writer = new OutputStreamWriter(out);
        ) {
            JsonUtil.serializeWithType(source, writer);
            return out.toByteArray();
        } catch (Exception e) {
            throw new SerializationException("redis-json序列化出错", e);
        }
    }

    @Override
    public Object deserialize(@Nullable byte[] bytes) throws SerializationException {
        if (bytes == null) {
            return null;
        } else if (bytes.length == 0) {
            return null;
        } else {
            // 此处无法传入需要转换的对象类型，所以直接返回 byte[]，然后再 RedisUtil 工具类中转换
            return bytes;
        }
    }
}
