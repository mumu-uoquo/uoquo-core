/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.config;

import com.uoquo.utils.*;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.ServiceConfig;
import com.uoquo.web.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Date;

/**
 * 自定义 Event 配置
 * @author  uoquo team
 */
@Configuration
@AutoConfigureAfter(ServiceConfig.class)
public class EventConfig {
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 事件发布
     */
    @Bean
    @ConditionalOnMissingBean(value = UoquoEventPublisher.class)
    public UoquoEventPublisher busAwarePublisher(ApplicationEventPublisher delegate) {
        log.debug("Use UoquoEventPublisher");
        return event -> {
            if (event == null) {
                log.warn("publish event data is null !!");
                return;
            }
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
            // 因为是JVM内部转发，几乎无异常和性能损耗，因此事件发布时不采用异步线程，在事件监听消费的地方再采用异步处理
            // 20250918: 调整为异步延迟发布，防止业务代码中的事务还未提交，导致消费事件时出错的情况
            ThreadPoolUtil.executeOnce(() -> {
                String oldTraceId = MDC.get("requestId");
                try {
                    MDC.put("requestId", String.format("%s-%s", traceId, bisnzId));
                    if (log.isDebugEnabled()) {
                        try {
                            log.debug("publish event: {}", JsonUtil.serialize(event));
                        } catch (Throwable e2) {
                            log.debug("publish event: {} serialize event error. {}", event, e2.getMessage());
                        }
                    }
                    delegate.publishEvent(event);
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
                    if (StringUtil.notNull(oldTraceId)) {
                        MDC.put("requestId", oldTraceId);
                    }
                }
            }, 200);
        };
    }

    /**
     * 事件监听工厂
     */
    @Bean
    @ConditionalOnMissingBean(value = UoquoEventListenerFactory.class)
    public UoquoEventListenerFactory uoquoEventListenerFactory() {
        log.debug("Use AppEventListenerFactory");
        return new AppEventListenerFactory();
    }
}
