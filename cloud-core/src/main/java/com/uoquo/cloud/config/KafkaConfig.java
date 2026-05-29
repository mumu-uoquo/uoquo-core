/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.config;

import com.uoquo.cloud.events.RemoteEventListenerFactory;
import com.uoquo.utils.*;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.ServiceConfig;
import com.uoquo.condition.ConditionOnPropertyExists;
import com.uoquo.web.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.bus.BusBridge;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import jakarta.annotation.PostConstruct;
import java.util.Date;

/**
 * 自定义 kafka 配置
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

    /**
     * 消息发布
     */
    @Bean
    public UoquoEventPublisher busAwarePublisher(ApplicationContext delegate, BusBridge busBridge) {
        log.debug("Use UoquoEventPublisher");
        return event -> {
            // 当前请求ID
            String traceId = StringUtil.isNull(CurrentUser.getTraceId()) ? IDGenerator.getNextULID() : CurrentUser.getTraceId();
            String bisnzId = (event instanceof UoquoEvent) ? ((UoquoEvent) event).getBusinessId() : "";
            // 补充必要信息
            if (event instanceof UoquoEvent appEvent) {
                if (StringUtil.isNull(appEvent.getBusinessType()) || StringUtil.isNull(appEvent.getOperationType())) {
                    throw new RuntimeException("businessType or operationType is null");
                }
                appEvent.setId(IDGenerator.getNextULID());
                if (StringUtil.isNull(appEvent.getToken())) {
                    appEvent.setToken(CurrentUser.getToken());
                }
                if (StringUtil.isNull(appEvent.getTraceId())) {
                    appEvent.setTraceId(CurrentUser.getTraceId());
                }
                if (StringUtil.isNull(appEvent.getAppKey())) {
                    appEvent.setAppKey(CurrentUser.getAppkey());
                    appEvent.setAppDeviceId(CurrentUser.getDeviceId());
                    appEvent.setAppVersion(CurrentUser.getAppVersion());
                    appEvent.setAppIp(CurrentUser.getClientIp());
                }
                if (StringUtil.isNull(appEvent.getOperatorId())) {
                    appEvent.setOperatorId(CurrentUser.getInfo().getUserId());
                    appEvent.setOperatorName(CurrentUser.getInfo().getUserName());
                    appEvent.setOperationTime(new Date());
                }
                if (StringUtil.isNull(appEvent.getOperatorInstituteId())) {
                    appEvent.setOperatorInstituteId(CurrentUser.getInfo().getInstituteId());
                }
            }
            // 发布事件（默认为同步，因为需要往消息队列发送，为了避免出现异常而影响主业务，此处改为异步）
            // 20250918: 调整为异步延迟发布，防止业务代码中的事务还未提交，导致消费事件时出错的情况
            ThreadPoolUtil.executeOnce(() -> {
                try {
                    MDC.put("requestId", String.format("%s-%s", traceId, bisnzId));
                    if (event instanceof RemoteApplicationEvent) {
                        // 如果是微服务消息，则转发到 Bus
                        if (log.isDebugEnabled()) {
                            try {
                                log.debug("publish remote event: {}", JsonUtil.serialize(event));
                            } catch (Throwable e2) {
                                log.debug("publish remote event: {} serialize event error. {}", event, e2.getMessage());
                            }
                        }
                        busBridge.send((RemoteApplicationEvent) event);
                    } else {
                        // 发布到本地
                        if (log.isDebugEnabled()) {
                            try {
                                log.debug("publish local event: {}", JsonUtil.serialize(event));
                            } catch (Throwable e2) {
                                log.debug("publish local event: {} serialize event error. {}", event, e2.getMessage());
                            }
                        }
                        delegate.publishEvent(event);
                    }
                } catch (Throwable e)  {
                    // 防止处理时覆盖了MDC信息
                    MDC.put("requestId", String.format("%s-%s", traceId, bisnzId));
                    try {
                        log.error("publish event error. {}", JsonUtil.serialize(event), e);
                    } catch (Throwable e2) {
                        log.error("publish event error: {} serialize error. {} ", event, e2.getMessage(), e);
                    }
                } finally {
                    MDC.clear();
                }
            }, 200);
        };
    }

    /**
     * 消息监听
     */
    @Bean
    public UoquoEventListenerFactory uoquoEventListenerFactory() {
        log.debug("Use RemoteEventListenerFactory");
        return new RemoteEventListenerFactory();
    }

}
