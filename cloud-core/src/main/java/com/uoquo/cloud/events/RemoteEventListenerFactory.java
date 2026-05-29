/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.events;

import com.uoquo.web.events.UoquoEventListenerFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;

import java.lang.reflect.Method;

/**
 * @author xuhz
 */
public class RemoteEventListenerFactory implements UoquoEventListenerFactory {

    @Override
    public boolean supportsMethod(Method method) {
        return method.isAnnotationPresent(EventListener.class);
    }

    @NotNull
    @Override
    public ApplicationListener<?> createApplicationListener(@NotNull String beanName, @NotNull Class<?> type, @NotNull Method method) {
        return new RemoteEventListenerAdapter(beanName, type, method);
    }

}
