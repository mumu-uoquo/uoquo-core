/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.events.AppEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 自定义 Jackson 反序列化器，处理 {@code type=RemoteEvent} 的 spring-cloud-bus 消息。
 *
 * <p>注册在 {@link AppEvent} 上，Jackson 的 {@code @JsonTypeInfo} 多态分发机制
 * 保证只有 {@code type=RemoteEvent} 的消息才会进入本类。</p>
 *
 * <p>用 {@link JsonUtil#deserialize(String, Class)} 完成基础字段反序列化，
 * 其内部 {@code mapperNoType} 不含我们的自定义模块，彻底避免递归。</p>
 */
public class AppEventDeserializer extends StdDeserializer<AppEvent<?>> {

    private static final Logger log = LoggerFactory.getLogger(AppEventDeserializer.class);

    private final DataTypeResolver resolver;

    public AppEventDeserializer(DataTypeResolver resolver) {
        super(AppEvent.class);
        this.resolver = resolver;
    }

    /**
     * {@code @JsonTypeInfo} 存在时 Jackson 优先调用此方法，直接委托给 {@link #deserialize}。
     */
    @Override
    public AppEvent<?> deserializeWithType(JsonParser p, DeserializationContext ctxt,
                                              TypeDeserializer typeDeserializer) throws IOException {
        return deserialize(p, ctxt);
    }

    /**
     * 核心反序列化逻辑：
     * <ol>
     *   <li>读取完整 JSON 树</li>
     *   <li>若缺少 {@code type} 字段（已被 Jackson 消费），补入 {@code "AppEvent"}</li>
     *   <li>用 {@link JsonUtil#deserialize} 完成基础字段反序列化（不含自定义模块，无递归风险）</li>
     *   <li>按 {@code dataType} 覆盖 {@code oldData}/{@code newData}</li>
     * </ol>
     */
    @Override
    @SuppressWarnings("unchecked")
    public AppEvent<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);

        // type 字段可能已被 @JsonTypeInfo 消费，补入以确保 JsonUtil 能正确反序列化
        if (!node.has("type")) {
            ((ObjectNode) node).put("type", "AppEvent");
        }
        // 获取泛型类
        String dataType = node.path("dataType").asText(null);
        Class<?> resolvedClass = resolver.resolve(dataType, "AppEvent");
        log.debug("the event message is AppEvent, and the dataType is '{}', resolved class is '{}'", dataType, resolvedClass);

        // 用 JsonUtil 的 mapperNoType 反序列化，不能直接用mapper否则会出现递归死循环
        return JsonUtil.deserialize(node.toString(), AppEvent.class, resolvedClass);
    }

}
