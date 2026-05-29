/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import org.springframework.context.ApplicationEvent;

/**
 * 参考 {@link org.springframework.context.ApplicationEventPublisher}
 * @author xuhz
 */
public interface UoquoEventPublisher {
    default void publishEvent(ApplicationEvent event) {
        publishEvent((Object) event);
    }

    void publishEvent(Object event);
}
