/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.events;

import com.uoquo.cloud.kafka.DeserializationFailureData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;

import java.nio.charset.StandardCharsets;

/**
 * 全局捕获 Spring Integration errorChannel 上的异常。
 *
 * <p>当 Spring Cloud Bus 消息的 {@code type} 字段对应的类在当前服务 classpath 中不存在时，
 * Jackson 多态反序列化失败，Spring Integration 将其包装为 {@link MessageHandlingException}
 * 并路由到 {@code errorChannel}。此处理器从 {@code failedMessage} 中提取原始 payload，
 * 以 {@code WARN} 级别记录到日志，然后静默忽略，保证消息处理线程不中断。</p>
 */
public class BusErrorChannelHandler {

    private static final Logger log = LoggerFactory.getLogger(BusErrorChannelHandler.class);

    @ServiceActivator(inputChannel = "errorChannel")
    public void handleBusError(ErrorMessage errorMessage) {
        try {
            Throwable cause = errorMessage.getPayload();
            if (cause instanceof MessageHandlingException || cause instanceof MessagingException) {
                MessagingException mhe = (MessagingException) cause;
                Message<?> failedMessage = mhe.getFailedMessage();
                if (failedMessage == null) {
                    log.warn("Spring Cloud Bus errorChannel: failedMessage is null", cause);
                    return;
                }
                Object payload = failedMessage.getPayload();
                if (payload == null) {
                    log.warn("Spring Cloud Bus errorChannel: payload is null", cause);
                    return;
                }
                // Kafka 反序列化失败时 DeserializationFailureHandler 已记录过日志，此处静默跳过
                if (payload instanceof DeserializationFailureData) {
                    log.debug("Spring Cloud Bus errorChannel: skipping DeserializationFailureData sentinel, already logged by DeserializationFailureHandler");
                    return;
                }
                String payloadStr;
                if (payload instanceof byte[]) {
                    payloadStr = new String((byte[]) payload, StandardCharsets.UTF_8);
                } else if (payload instanceof String) {
                    payloadStr = (String) payload;
                } else {
                    payloadStr = String.valueOf(payload);
                }
                log.warn("Spring Cloud Bus received unresolvable message, ignored. payload: {}", payloadStr, cause);
            } else {
                log.warn("Spring Integration errorChannel received error: {}", cause.getMessage(), cause);
            }
        } catch (Throwable t) {
            log.warn("Spring Integration errorChannel handler encountered unexpected error", t);
        }
    }
}
