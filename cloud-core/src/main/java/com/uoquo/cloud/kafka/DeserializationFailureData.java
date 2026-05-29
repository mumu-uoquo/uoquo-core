/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.kafka;

/**
 * Kafka 消息反序列化失败的哨兵对象。<br>
 * 由 {@link DeserializationFailureHandler} 在反序列化失败时返回，
 * 不继承任何业务类型，适用于任意 topic 的消费者做统一判断。
 *
 * <pre>
 * // 消费者示例
 * {@literal @}KafkaListener(topics = "xxx")
 * public void onMessage(Object message) {
 *     if (message instanceof DeserializationFailureData sentinel) {
 *         log.warn("跳过反序列化失败的消息, topic={}", sentinel.getTopic());
 *         return;
 *     }
 *     MyEvent event = (MyEvent) message;
 *     // 正常处理
 * }
 * </pre>
 *
 * @author xuhz
 */
public class DeserializationFailureData {

    private final String topic;
    private final String rawData;
    private final Throwable cause;

    public DeserializationFailureData(String topic, String rawData, Throwable cause) {
        this.topic   = topic;
        this.rawData = rawData;
        this.cause   = cause;
    }

    public String getTopic() {
        return topic;
    }

    public String getRawData() {
        return rawData;
    }

    public Throwable getCause() {
        return cause;
    }
}
