/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Method;

/**
 * @author xuhz
 */
public class AppEventListenerFactory implements UoquoEventListenerFactory {

    @Override
    public boolean supportsMethod(@NonNull Method method) {
        return method.isAnnotationPresent(EventListener.class);
    }

    @NonNull
    @Override
    public ApplicationListener<?> createApplicationListener(@NonNull String beanName, @NonNull Class<?> type, @NonNull Method method) {
        return new AppEventListenerAdapter(beanName, type, method);
    }
}
