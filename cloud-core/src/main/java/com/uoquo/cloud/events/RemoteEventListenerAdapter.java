/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.events;

import com.uoquo.utils.IDGenerator;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.events.AppEvent;
import com.uoquo.web.events.UoquoEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ApplicationListenerMethodAdapter;
import org.springframework.core.ResolvableType;

import java.lang.reflect.Method;

/**
 * @author xuhz
 */
public class RemoteEventListenerAdapter extends ApplicationListenerMethodAdapter {
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 监听方法声明的 RemoteEvent 泛型类型 SimpleName（跨服务包名可能不同，用 SimpleName 兼容）。
     * 如 method(RemoteEvent<SseMessage> event) → "SseMessage"
     * 如 method(RemoteEvent<?> event) → null（匹配所有 RemoteEvent）
     * 如 method(CustomEvent event) → null（子类由 Spring 类型检查处理）
     */
    private final String declaredDataType;

    public RemoteEventListenerAdapter(String beanName, Class<?> targetClass, Method method) {
        super(beanName, targetClass, method);
        this.declaredDataType = resolveDataType(method);
    }

    /**
     * 解析监听方法第一个参数中 RemoteEvent 的泛型类型。
     * 仅当参数类型恰好是 RemoteEvent（非子类）且泛型为具体类型（非 ?）时返回 SimpleName。
     */
    private String resolveDataType(Method method) {
        if (method.getParameterCount() == 0) {
            return null;
        }
        ResolvableType parameterType = ResolvableType.forMethodParameter(method, 0);
        if ((parameterType.getRawClass() == RemoteEvent.class) || (parameterType.getRawClass() == AppEvent.class)) {
            ResolvableType genericType = parameterType.getGeneric(0);
            Class<?> resolved = genericType.resolve();
            if (resolved != null && resolved != Object.class) {
                log.debug("the EventListener [{}], and the dataType is '{}'", method, resolved.getSimpleName());
                return resolved.getSimpleName();
            }
        }
        return null;
    }

    private static String simpleName(String typeName) {
        int dot = typeName.lastIndexOf('.');
        return dot >= 0 ? typeName.substring(dot + 1) : typeName;
    }

    /**
     * 当监听到事件时，该事件的所有监听器都会进入 onApplicationEvent 方法，
     * 只有满足注解条件的才会由 doInvoke 真正执行监听器的业务。
     * 此处额外增加 dataType 匹配过滤，确保 RemoteEvent<T> 的泛型类型匹配。
     */
    @Override
    public void onApplicationEvent(@NotNull ApplicationEvent event) {
        if (log.isDebugEnabled()) {
            try {
                log.debug("received event: {}", JsonUtil.serialize(event));
            } catch (Throwable e2) {
                log.debug("received event: {} serialize event error. {}", event, e2.getMessage());
            }
        }
        if (declaredDataType != null ) {
            String eventDataType = null;
            if ((event instanceof RemoteEvent<?> remoteEvent)) {
                eventDataType = remoteEvent.getDataType();
            }
            if ((event instanceof AppEvent<?> appEvent)) {
                eventDataType = appEvent.getDataType();
            }
            Method method = getTargetMethod();
            if (eventDataType != null && !declaredDataType.equals(simpleName(eventDataType))) {
                log.debug("skip remote event. method: {}; method declared: {}, event actual: {}", method, declaredDataType, eventDataType);
                return;
            } else {
                log.debug("process remote event. method: {}; method declared: {}, event actual: {}", method, declaredDataType, eventDataType);
            }
        }
        super.onApplicationEvent(event);
    }

    @Override
    protected Object doInvoke(Object... args) {
        Method method = getTargetMethod();
        Object event = (args != null) && args.length > 0 ? args[0] : null;
        if (event == null) {
            log.error("process event error. method: {}; data: {} .", method, "event is null");
            return null;
        }
        String oldTraceId = MDC.get("requestId");
        String traceId = (event instanceof UoquoEvent) ? ((UoquoEvent) event).getTraceId() : IDGenerator.getNextULID();
        String bisnzId = (event instanceof UoquoEvent) ? ((UoquoEvent) event).getBusinessId() : "";
        // 此处不需要再启线程
        // 1. 因为 RemoteApplicationEvent 已经在 RemoteApplicationEventMulticaster 中采用独立线程处理了
        // 2. 非 RemoteApplicationEvent 事件与发布事件是同步的，在一个线程中，发布时已经是异步线程了，所以此处也不需要再启线程
        try {
            MDC.put("requestId", String.format("%s-%s", traceId, bisnzId));
            if (log.isDebugEnabled()) {
                String data;
                try {
                    data = JsonUtil.serialize(event);
                } catch (Throwable e2) {
                    data = String.valueOf(event);
                }
                if (event instanceof RemoteApplicationEvent) {
                    log.debug("process remote event begin. method: {}; data: {}", method, data);
                } else {
                    log.debug("process local event begin. method: {}; data: {}", method, data);
                }
            }
            return super.doInvoke(args);
        } catch (Throwable e)  {
            String data;
            try {
                data = JsonUtil.serialize(event);
            } catch (Throwable e2) {
                data = String.valueOf(event);
            }
            // 防止处理时覆盖了MDC信息
            MDC.put("requestId", String.format("%s-%s", traceId, bisnzId));
            if (event instanceof RemoteApplicationEvent) {
                // 若是 RemoteApplicationEvent 则抛出异常，让 RemoteApplicationEventMulticaster 重试（或放入死信队列）
                // 因为 RemoteApplicationEventMulticaster 会打印出异常堆栈信息，这里不需要重复打印
                log.error("process remote event error. method: {}; data: {} . reason: {}", method, data, e.getLocalizedMessage(), e);
                // 20251030 不再抛出异常，因为同一个事件可能有多个监听器，若交由 RemoteApplicationEventMulticaster 重试，将导致其他监听器的重复执行
                // throw e;
            } else {
                // 其他事件的异常，需要打印详细的堆栈信息
                log.error("process local event error. method: {}; data: {} .", method, data, e);
            }
            return null;
        } finally {
            MDC.remove("requestId");
            if (StringUtil.notNull(oldTraceId)) {
                MDC.put("requestId", oldTraceId);
            }
        }
    }
}
