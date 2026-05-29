/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.serializer.FailedDeserializationInfo;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * Kafka 消息反序列化失败的全局处理器。<br>
 * 捕获所有 topic 的反序列化异常，记录日志后返回 {@link DeserializationFailureData}，
 * 消费者通过 {@code instanceof} 判断即可跳过或做针对性处理，无需抛出异常或写入死信队列。<br>
 * 对应配置项：{@code spring.deserializer.value.function}
 *
 * @author xuhz
 */
public class DeserializationFailureHandler implements Function<FailedDeserializationInfo, Object> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public Object apply(FailedDeserializationInfo info) {
        String data = new String(info.getData(), StandardCharsets.UTF_8);
        log.warn("Kafka消息反序列化失败 - Topic: {}, Data: {}, Reason: ", info.getTopic(), data, info.getException());
        return new DeserializationFailureData(info.getTopic(), data, info.getException());
    }
}
