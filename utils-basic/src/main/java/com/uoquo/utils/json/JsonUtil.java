/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.json;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.jackson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.support.NullValue;
import org.springframework.lang.NonNull;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

/**
 * 描述：JSON工具类. <br>
 * 备注：默认采用jackson作为JSON的底层处理
 * 原因：
 * <ul>
 *     <li>jackson支持保留类名，用于能更准确的进行反序列化</li>
 *     <li>jackson作为spring内置的json处理，依赖改动较少</li>
 * </ul>
 * 日期：2023-01-19 20:39 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-20     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class JsonUtil {
    private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);

    /**
     * 不带java类型的处理类（默认）
     */
    private static final ObjectMapper mapperNoType;

    /**
     * 带java类型的处理类
     */
    private static final ObjectMapper mapperWithType;

    static {
        // 含java类型的处理类
        mapperWithType = initialJackson(new ObjectMapper());
        mapperWithType.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        // 不含java类型的处理类
        mapperNoType = initialJackson(new ObjectMapper());
        mapperNoType.deactivateDefaultTyping();
    }

    /**
     * 初始化jackson信息
     */
    public static @NonNull ObjectMapper initialJackson(ObjectMapper mapper) {
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        String moduleName = "UOQUO-SimpleModule";
        // 根据名称判断是否已经做过module注册
        Optional<Object> moduleId = mapper.getRegisteredModuleIds().stream().filter(moduleName::equals).findFirst();
        if (moduleId.isPresent()) {
            log.debug("ObjectMapper 已经注入过分页等自定义序列化类");
            return mapper;
        }
        // 1. 自定义序列化和反序列化
        SimpleModule simpleModule = new SimpleModule(moduleName);
        // 1.1 将Long序列化为String，防止JS端的精度丢失
        simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
        simpleModule.addSerializer(Long.TYPE,  ToStringSerializer.instance);
        // 1.2 日期处理（默认"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"）
        // 20240724：由字符串改为时间戳
        simpleModule.addSerializer(Date .class,  new DateContextualSerializer());
        simpleModule.addDeserializer(Date.class, new DateContextualDeserializer());
        // 1.3 分页处理
//        simpleModule.addSerializer(PageList .class,  new PageListSerializer());
//        simpleModule.addDeserializer(PageList.class, new PageListDeserializer<>());
        simpleModule.addSerializer(new NullValueSerializer());
        // 1.4 自定义脱敏、加解密注解
        // 20260521：改为2.2通过 AnnotationIntrospector 精确路由 @Sensitive 字段，避免全局接管 String 类型导致的副作用（其他 String 字段仍走 Jackson 默认逻辑）
        // simpleModule.addSerializer(String .class,  new SensitiveSerializer());
        // simpleModule.addDeserializer(String.class, new SensitiveDeserializer());
        // 2. 设置配置
        // 2.1 将自定义规则加入jackson中
        mapper.registerModule(simpleModule);
        // 注册JSR310模块，支持Duration等Java8时间类型
        mapper.registerModule(new JavaTimeModule());
        // 2.2 注册 @Sensitive 注解内省器（与默认内省器合并，保持原有 Jackson 注解行为）
        AnnotationIntrospector sensitiveIntrospector = new SensitiveAnnotationIntrospector();
        mapper.setAnnotationIntrospector(AnnotationIntrospector.pair(
                sensitiveIntrospector,
                mapper.getSerializationConfig().getAnnotationIntrospector()));
        // 2.3 序列化
        // 格式化输出
        // mapper.enable(SerializationFeature.INDENT_OUTPUT);
        // 关闭空bean序列化失败检查，遇到无属性的对象会序列化为{}，不会抛出异常
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // Java 8 时间类型只保留毫秒（默认序列化为纳秒）
        mapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
        // 2.4 反序列化
        // 忽略多余的属性
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // 不允许浮点自动转整数（避免精度丢失）
        mapper.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        // Java 8时间类型反序列化时间戳时只保留毫秒（默认纳秒精度）
        mapper.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
        // 忽略类名错误（多态反序列化找不到/无法解析子类时）
        mapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        // 忽略类型错误（找不到Object Id对应对象时用null代替）
        mapper.configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY, false);
        // JSON为null反序列化到基本类型（int/double）时抛异常（便于发现null值绑定问题）
        mapper.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        log.info("ObjectMapper 完成注入分页等自定义序列化类");
        return mapper;
    }

    /**
     * 序列化（默认不含class）
     * @param source 待序列化对象
     */
    public static <T> String serialize(T source) {
        if (source == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        serialize(mapperNoType, writer, source);
        return writer.toString();
    }

    /**
     * 序列化（默认不含class）
     * @param source 待序列化对象
     * @param writer 输出流
     */
    public static <T> void serialize(T source, Writer writer) {
        if (source == null) {
            return;
        }
        serialize(mapperNoType, writer, source);
    }

    /**
     * 序列化（仅jackson含class，其他都输输出普通json字符串）
     * @param source 待序列化对象
     */
    public static <T> String serializeWithType(T source) {
        if (source == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        serialize(mapperWithType, writer, source);
        return writer.toString();
    }

    /**
     * 序列化（仅jackson含class，其他都输输出普通json字符串）
     * @param source 待序列化对象
     * @param writer 输出流
     */
    public static <T> void serializeWithType(T source, Writer writer) {
        if (source == null) {
            return;
        }
        serialize(mapperWithType, writer, source);
    }

    /**
     * 序列化
     */
    private static <T> void serialize(ObjectMapper mapper, Writer writer, T source) {
        try {
            mapper.writeValue(writer, source);
        } catch (IOException e) {
            // 因为异常继续抛给了调用方，此时仅debug时记录日志
            log.debug("序列化失败：source = {}", source.toString(), e);
            throw new IllegalArgumentException(String.format("对象[%s]序列化失败.", source.toString()), e);
        }
    }

    /**
     * 反序列化
     * 说明：主要用于jackson序列化后含class的情况，其他返回的都是 LinkMap
     * @param content 待转换json字符串（含class属性）
     */
    public static <T> T deserialize(String content) {
        if (StringUtil.isNull(content)) {
            return null;
        }

        try {
            if (content.startsWith("[")) {
                JavaType javaType = mapperNoType.getTypeFactory().constructCollectionLikeType(List.class, Object.class);
                return deserialize(content, javaType);
            }
            T result = (T) mapperWithType.readValue(content, Object.class);
            if (result == null) {
                result = (T) mapperNoType.readValue(content, Object.class);
            }
            return result;
        } catch (IOException e) {
            // 因为异常继续抛给了调用方，此时仅debug时记录日志
            log.debug("JSON反序列化出错. content={}.", content);
            throw new IllegalArgumentException(String.format("对象[%s]反序列化失败.", content), e);
        }
    }

    /**
     * 反序列化
     * @param content 待转换json字符串（不含class）
     * @param clazz   待转换的对象类型
     */
    public static <T> T deserialize(String content, Class<T> clazz) {
        if (clazz == null) {
            return deserialize(content);
        }
        // 如果 content 含 @class 属性则用class接收后，再赋值给clazz对象
//        Object obj =

        JavaType javaType = mapperNoType.getTypeFactory().constructType(clazz);
        return deserialize(content, javaType);
    }

    /**
     * 反序列化
     * @param content 待转换json字符串（不含class）
     * @param type    待转换的对象类型
     */
    public static <T> T deserialize(String content, Type type) {
        if (type == null) {
            return deserialize(content);
        }
        JavaType javaType = mapperNoType.getTypeFactory().constructType(type);
        return deserialize(content, javaType);
    }

    /**
     * 反序列化
     * @param content 待转换json字符串（不含class）
     * @param outerClazz  最外层对象类型
     * @param innerClazz  内层对象类型
     */
    public static <T, E> T deserialize(String content, Class<T> outerClazz, Class<E>... innerClazz) {
        JavaType javaType = mapperNoType.getTypeFactory().constructParametricType(outerClazz, innerClazz);
        return deserialize(content, javaType);
    }

    /**
     * 反序列化
     * 说明：主要用于jackson序列化后含class的情况，其他返回的都是 LinkMap
     * 备注：仅支持 serializeWithType 方法序列化后的内容
     * @param reader 待转换json字符串（含class）
     */
    public static <T> T deserialize(Reader reader) {
        if (reader == null) {
            return null;
        }
        try {
            T result = (T) mapperWithType.readValue(reader, Object.class);
            if (result == null) {
                result = (T) mapperNoType.readValue(reader, Object.class);
            }
            return result;
        } catch (IOException e) {
            // 因为异常继续抛给了调用方，此时仅debug时记录日志
            log.debug("JSON反序列化出错. content={}.", reader);
            throw new IllegalArgumentException(String.format("对象[%s]反序列化失败.", reader.toString()), e);
        }
    }

    /**
     * 反序列化
     * @param reader 待转换json字符串（不含class）
     * @param clazz  待转换的对象类型
     */
    public static <T> T deserialize(Reader reader, Class<T> clazz) {
        if (clazz == null) {
            return deserialize(reader);
        }
        try {
            return (T)mapperNoType.readValue(reader, clazz);
        } catch (IOException e) {
            // 因为异常继续抛给了调用方，此时仅debug时记录日志
            log.debug("JSON反序列化出错. content={}.", reader);
            throw new IllegalArgumentException(String.format("对象[%s]反序列化失败.", reader.toString()), e);
        }
    }

    /**
     * 反序列化
     * @param reader 待转换json字符串（不含class）
     * @param type   待转换的对象类型
     */
    public static <T> T deserialize(Reader reader, Type type) {
        if (type == null) {
            return deserialize(reader);
        }
        try {
            JavaType javaType = mapperNoType.getTypeFactory().constructType(type);
            return (T)mapperNoType.readValue(reader, javaType);
        } catch (IOException e) {
            // 因为异常继续抛给了调用方，此时仅debug时记录日志
            log.debug("JSON反序列化出错. content={}.", reader);
            throw new IllegalArgumentException(String.format("对象[%s]反序列化失败.", reader.toString()), e);
        }
    }

    /**
     * 反序列化（转换为List）
     * @param content 待转换json字符串（不含class）
     * @param clazz   待转换的对象类型（List中的元素类型）
     */
    public static <T> List<T> deserializeAsList(String content, Class<T> clazz) {
        JavaType javaType = mapperNoType.getTypeFactory().constructCollectionLikeType(List.class, clazz);
        return deserialize(content, javaType);
    }

    /**
     * 反序列化（Map）
     * @param content 待转换json字符串（不含class）
     * @param keyClazz  键对象类型
     * @param valClazz  值对象类型
     */
    public static <K, V> Map<K,V> deserializeAsMap(String content, Class<K> keyClazz, Class<V> valClazz) {
        JavaType javaType = mapperNoType.getTypeFactory().constructMapType(HashMap.class, keyClazz, valClazz);
        return deserialize(content, javaType);
    }

    /**
     * 反序列化
     */
    public static <T> T deserialize(String content, JavaType type) {
        try {
            return (T)mapperNoType.readValue(content, type);
        } catch (IOException e) {
            // 因为异常继续抛给了调用方，此时仅debug时记录日志
            log.debug("JSON反序列化出错. content={}.", content);
            throw new IllegalArgumentException(String.format("对象[%s]反序列化失败.", content), e);
        }
    }


    /**
     * {@link org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer GenericJackson2JsonRedisSerializer}
     */
    private static class NullValueSerializer extends StdSerializer<NullValue> {

        @Serial
        private static final long serialVersionUID = 1999052150548658808L;
        private final String classIdentifier;

        NullValueSerializer() {
            super(NullValue.class);
            this.classIdentifier = "@class";
        }

        @Override
        public void serialize(NullValue value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            jgen.writeStartObject();
            jgen.writeStringField(classIdentifier, NullValue.class.getName());
            jgen.writeEndObject();
        }
    }
}
