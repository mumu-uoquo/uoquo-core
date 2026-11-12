/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEvent;

import java.lang.reflect.Method;

/**
 * 本地事件（{@link AppEvent}）的监听适配器，行为见 {@link UoquoEventListenerAdapter}。
 * @author xuhz
 */
public class AppEventListenerAdapter extends UoquoEventListenerAdapter {

    public AppEventListenerAdapter(String beanName, Class<?> targetClass, Method method) {
        super(beanName, targetClass, method, AppEvent.class);
    }

    @Override
    protected EventData dataOf(@NonNull ApplicationEvent event) {
        if (event instanceof AppEvent<?> appEvent) {
            return new EventData(appEvent.getDataType(), appEvent.getOldData(), appEvent.getNewData());
        }
        return null;
    }
}
