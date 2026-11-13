/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.events;

import com.uoquo.web.events.UoquoEventListenerFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.bus.ServiceMatcher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;

import java.lang.reflect.Method;

/**
 * @author xuhz
 */
public class RemoteEventListenerFactory implements UoquoEventListenerFactory {

    /** Bus 的服务匹配器（可空：为空时不按目标服务过滤；没有启用 Bus 时按本服务名匹配，见 {@link RemoteEventDestination}） */
    private final ObjectProvider<ServiceMatcher> serviceMatcher;

    public RemoteEventListenerFactory() {
        this(null);
    }

    /**
     * @param serviceMatcher Bus 的服务匹配器（延迟获取，不会提前初始化 Bus 的相关 Bean）
     */
    public RemoteEventListenerFactory(ObjectProvider<ServiceMatcher> serviceMatcher) {
        this.serviceMatcher = serviceMatcher;
    }

    @Override
    public boolean supportsMethod(Method method) {
        return method.isAnnotationPresent(EventListener.class);
    }

    @NotNull
    @Override
    public ApplicationListener<?> createApplicationListener(@NotNull String beanName, @NotNull Class<?> type, @NotNull Method method) {
        return new RemoteEventListenerAdapter(beanName, type, method, serviceMatcher);
    }

}
