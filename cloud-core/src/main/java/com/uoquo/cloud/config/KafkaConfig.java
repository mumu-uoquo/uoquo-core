/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.config;

import com.uoquo.web.ServiceConfig;
import com.uoquo.condition.ConditionOnPropertyExists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import jakarta.annotation.PostConstruct;

/**
 * 自定义 kafka 配置.<br>
 * 事件的发布统一由 app-core 的 {@code UoquoEventPublisher} 完成：
 * 事件经 {@code ApplicationEventPublisher.publishEvent} 在本地分发，同时由 Spring Cloud Bus 的
 * {@code RemoteApplicationEventListener} 发往消息队列（{@link ServiceMatcherAutoConfiguration} 保证发送方不会再次处理自己发的事件）；
 * 事件监听工厂见 {@link com.uoquo.cloud.CloudConfig}。
 * @author  uoquo team
 */
@Configuration
@AutoConfigureBefore(ServiceConfig.class)
@ConditionalOnProperty(name = "app.kafka.default.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaConfig {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @PostConstruct
    public void setProperties(){
        log.debug("KafkaConfig init ...");
    }

    @Bean
    @Primary
    @ConditionOnPropertyExists(name = "spring.kafka.default.bootstrap-servers")
    @ConfigurationProperties(prefix = "spring.kafka.default")
    public KafkaProperties  kafkaProperties() {
        log.debug("Use KafkaProperties");
        return new KafkaProperties();
    }

}
