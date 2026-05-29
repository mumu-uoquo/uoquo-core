/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import com.uoquo.utils.IDGenerator;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.ThreadPoolUtil;
import com.uoquo.utils.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ApplicationListenerMethodAdapter;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Method;

/**
 * @author xuhz
 */
public class AppEventListenerAdapter extends ApplicationListenerMethodAdapter {
    private final Logger log = LoggerFactory.getLogger(getClass());

    public AppEventListenerAdapter(String beanName, Class<?> targetClass, Method method) {
        super(beanName, targetClass, method);
    }

    /**
     * 当监听到事件时，该事件的所有监听器都会进入 onApplicationEvent 方法，
     * 只有满足注解条件的才会由 doInvoke 真正执行监听器的业务
     */
    @Override
    public void onApplicationEvent(@NonNull ApplicationEvent event) {
        if (log.isDebugEnabled()) {
            try {
                log.debug("received event: {}", JsonUtil.serialize(event));
            } catch (Throwable e2) {
                log.debug("received event: {} serialize event error. {}", event, e2.getMessage());
            }
        }
        super.onApplicationEvent(event);
    }

    @Override
    protected Object doInvoke(@NonNull Object... args) {
        Method method = getTargetMethod();
        Object event = (args != null) && args.length > 0 ? args[0] : null;
        if (event == null) {
            log.error("process event error. method: {}; data: {} .", method, "event is null");
            return null;
        }
        String traceId = (event instanceof UoquoEvent) ? ((UoquoEvent) event).getTraceId() : IDGenerator.getNextULID();
        String bisnzId = (event instanceof UoquoEvent) ? ((UoquoEvent) event).getBusinessId() : "";
        // 异步处理
        // 20251029 可能同一个事件有多个监听器的情况，如果不异步执行，这些监听器将串行，且当前面处理抛异常会导致后面监听器无法处理
        ThreadPoolUtil.executeOnce(() -> {
            String oldTraceId = MDC.get("requestId");
            try {
                MDC.put("requestId", String.format("%s-%s", traceId, bisnzId));
                if (log.isDebugEnabled()) {
                    String data;
                    try {
                        data = JsonUtil.serialize(event);
                    } catch (Throwable e2) {
                        data = String.valueOf(event);
                    }
                    log.debug("process event begin. method: {}; data: {}", method, data);
                }
                Object result = super.doInvoke(event);
                // 因为当前在异步线程执行，原线程已经返回 null 给 processEvent 方法，所以此处需要处理 doInvoke 的返回值
                if (result != null) {
                    handleResult(result);
                }
            } catch (Throwable e) {
                String data;
                try {
                    data = JsonUtil.serialize(event);
                } catch (Throwable e2) {
                    data = String.valueOf(event);
                }
                // 防止处理时覆盖了MDC信息
                MDC.put("requestId", String.format("%s-%s", traceId, bisnzId));
                // 在事件处理过程中发生异常时，仅记录日志，不再重试
                log.error("process event error. method: {}; data: {} .", method, data, e);
            } finally {
                MDC.clear();
                if (StringUtil.notNull(oldTraceId)) {
                    MDC.put("requestId", oldTraceId);
                }
            }
        });
        return null;
    }
}
