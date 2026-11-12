/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.config;

import com.uoquo.web.ServiceConfig;
import com.uoquo.web.events.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自定义 Event 配置
 * @author  uoquo team
 */
@Configuration
@AutoConfigureAfter(ServiceConfig.class)
public class EventConfig {
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 事件发布.<br>
     * 微服务（cloud-core）下，事件经 {@code ApplicationEventPublisher} 发布后，除本地监听器外，
     * 还会由 Spring Cloud Bus 发往消息队列，无需另行实现发布器。
     */
    @Bean
    @ConditionalOnMissingBean(value = UoquoEventPublisher.class)
    public UoquoEventPublisher busAwarePublisher(ApplicationEventPublisher delegate) {
        log.debug("Use UoquoEventPublisher");
        return new DefaultUoquoEventPublisher(delegate);
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

    /**
     * 容器关闭时，等待事件线程池中已提交的事件处理完毕.
     */
    @Bean
    public DisposableBean eventExecutorShutdown() {
        return EventExecutor::shutdown;
    }
}
