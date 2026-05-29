/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import org.springframework.context.event.EventListenerFactory;
import org.springframework.core.Ordered;

/**
 * @author xuhz
 */
public interface UoquoEventListenerFactory extends EventListenerFactory, Ordered {

    @Override
    default int getOrder() {
        return 0;
    }
}
