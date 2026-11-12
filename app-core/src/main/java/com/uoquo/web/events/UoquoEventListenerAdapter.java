/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import java.lang.reflect.Method;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ApplicationListenerMethodAdapter;

import com.uoquo.utils.IDGenerator;
import com.uoquo.utils.StringUtil;

/**
 * 事件监听方法的适配器基类（本地事件 {@code AppEvent} 与微服务事件 {@code RemoteEvent} 共用）。
 *
 * <p>在 Spring 标准的 {@code @EventListener} 处理之上增加：</p>
 * <ol>
 *   <li><b>泛型过滤</b>：监听方法声明了具体泛型（如 {@code RemoteEvent<SseMessage>}）时，只处理数据类型匹配的事件；</li>
 *   <li><b>异步执行</b>：监听器在事件专用线程池（{@link EventExecutor}）中执行。同一事件的多个监听器互不阻塞、互不影响；
 *       本地发布线程与消息消费线程不会被慢监听器占用；</li>
 *   <li><b>用户上下文</b>：用事件携带的发起人信息填充 {@code CurrentUser}，执行完毕后恢复；</li>
 *   <li><b>日志链路</b>：把 {@code traceId-businessId} 放入 MDC 的 {@code requestId}；</li>
 *   <li><b>异常隔离</b>：监听器抛出的异常只记录日志，不影响其他监听器，也不会触发消息重投
 *       （同一事件有多个监听器，重投会造成其他监听器的重复执行）。</li>
 * </ol>
 *
 * @author xuhz
 */
public abstract class UoquoEventListenerAdapter extends ApplicationListenerMethodAdapter {
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 监听方法声明的事件泛型类型（跨服务包名可能不同，匹配时按 SimpleName 比较，并校验数据实例）。
     * 如 method(RemoteEvent<SseMessage> event) → SseMessage.class；
     * 如 method(RemoteEvent<?> event) → null（匹配所有）；
     * 如 method(CustomEvent event) → null（子类由 Spring 类型检查处理）。
     */
    private final Class<?> declaredDataClass;

    /**
     * 事件携带的数据信息。
     * @param dataType 数据类型名称
     * @param oldData  旧数据
     * @param newData  新数据
     */
    public record EventData(String dataType, Object oldData, Object newData) {
    }

    /**
     * @param beanName     监听器所在 Bean 的名称
     * @param targetClass  监听器所在的类
     * @param method       监听方法
     * @param eventClasses 需要按泛型过滤的事件基类（如 AppEvent、RemoteEvent）
     */
    protected UoquoEventListenerAdapter(String beanName, Class<?> targetClass, Method method, Class<?>... eventClasses) {
        super(beanName, targetClass, method);
        this.declaredDataClass = EventSupport.declaredDataClass(method, eventClasses);
        if (this.declaredDataClass != null) {
            log.debug("the EventListener [{}], and the dataType is '{}'", method, this.declaredDataClass.getSimpleName());
        }
    }

    /**
     * 提取事件携带的数据信息，用于泛型过滤。
     * @param event 事件
     * @return 数据信息；事件不是携带数据的事件类型时返回 null（不过滤）
     */
    protected abstract EventData dataOf(ApplicationEvent event);

    /**
     * 事件是否应当由本服务的监听器处理（在泛型过滤之前判断）。<br>
     * 默认全部接受；子类可以按事件的目标服务等信息过滤，例如微服务事件只处理发给本服务的事件。
     * @param event 事件
     * @return true 表示接受
     */
    protected boolean accepts(ApplicationEvent event) {
        return true;
    }

    /**
     * 当监听到事件时，该事件的所有监听器都会进入 onApplicationEvent 方法，
     * 只有满足注解条件（condition、参数类型）的才会由 doInvoke 真正执行监听器的业务。
     * 此处额外增加泛型类型匹配过滤。
     */
    @Override
    public void onApplicationEvent(@NonNull ApplicationEvent event) {
        // 只记录业务事件：Spring 自身的事件（如 ApplicationReadyEvent）会引用整个应用上下文，序列化它们
        // 既没有意义，又会因为对象图过深而耗时很长（实测启动阶段每个监听器停顿两分钟以上）
        if (!isFrameworkEvent(event) && log.isDebugEnabled()) {
            log.debug("received event: {}", EventSupport.toLogString(event));
        }
        if (!accepts(event)) {
            return;
        }
        if (declaredDataClass != null) {
            EventData data = dataOf(event);
            if (data != null) {
                if (!EventSupport.matchesDataType(declaredDataClass, data.dataType(), data.oldData(), data.newData())) {
                    log.debug("skip event. method: {}; method declared: {}, event actual: {}",
                            getTargetMethod(), declaredDataClass.getSimpleName(), data.dataType());
                    return;
                }
                log.debug("process event. method: {}; method declared: {}, event actual: {}",
                        getTargetMethod(), declaredDataClass.getSimpleName(), data.dataType());
            }
        }
        super.onApplicationEvent(event);
    }

    @Override
    protected Object doInvoke(@NonNull Object... args) {
        Method method = getTargetMethod();
        Object event = (args != null) && args.length > 0 ? args[0] : null;
        if (event == null) {
            if (args == null || args.length == 0) {
                // 无参的监听方法（如 @EventListener(ApplicationReadyEvent.class) public void init()）
                // 没有事件参数可供判断，按 Spring 原生行为同步执行，否则该方法永远不会被调用
                return super.doInvoke(args == null ? new Object[0] : args);
            }
            log.warn("process event error. method: {}; data: {} .", method, "event is null");
            return null;
        }
        if (isFrameworkEvent(event)) {
            // Spring 自身的事件（生命周期事件等）：保持 Spring 原生的同步语义，
            // 这类监听器通常依赖执行顺序（如 ApplicationReadyEvent 之后的初始化），且没有用户上下文需要填充
            return super.doInvoke(args);
        }
        UoquoEvent uoquoEvent = (event instanceof UoquoEvent) ? (UoquoEvent) event : null;
        String eventTraceId = (uoquoEvent != null) ? uoquoEvent.getTraceId() : null;
        String traceId = StringUtil.isNull(eventTraceId) ? IDGenerator.getNextULID() : eventTraceId;
        String requestId = EventSupport.requestId(traceId, uoquoEvent != null ? uoquoEvent.getBusinessId() : "");
        // 异步执行：同一个事件可能有多个监听器，同步执行会让它们串行，且前面的处理慢或失败会影响后面的监听器
        EventExecutor.execute(() -> invokeListener(event, uoquoEvent, requestId));
        return null;
    }

    /**
     * 是否为 Spring 框架自身发布的事件（如 ContextRefreshedEvent、ApplicationReadyEvent）。
     * 业务自定义的事件（即使继承了 ApplicationEvent）不属于框架事件，仍按业务事件处理。
     */
    private static boolean isFrameworkEvent(Object event) {
        return event instanceof ApplicationEvent
                && !(event instanceof UoquoEvent)
                && event.getClass().getName().startsWith("org.springframework.");
    }

    private void invokeListener(Object event, UoquoEvent uoquoEvent, String requestId) {
        Method method = getTargetMethod();
        String oldRequestId = MDC.get("requestId");
        // 监听器可能在调用方线程内执行（线程池饱和时），需要保留并恢复调用方的用户上下文
        EventContext.Snapshot snapshot = EventContext.capture();
        try {
            MDC.put("requestId", requestId);
            EventContext.apply(uoquoEvent);
            if (log.isDebugEnabled()) {
                log.debug("process event begin. method: {}; data: {}", method, EventSupport.toLogString(event));
            }
            Object result = super.doInvoke(event);
            // 当前在异步线程执行，原线程已经返回 null 给 processEvent 方法，所以此处需要处理监听方法的返回值（如继续发布事件）
            if (result != null) {
                handleResult(result);
            }
        } catch (Throwable e) {
            // 防止处理时覆盖了MDC信息
            MDC.put("requestId", requestId);
            // 在事件处理过程中发生异常时，仅记录日志，不再重试
            log.error("process event error. method: {}; data: {} .", method, EventSupport.toLogString(event), e);
        } finally {
            snapshot.restore();
            if (StringUtil.notNull(oldRequestId)) {
                MDC.put("requestId", oldRequestId);
            } else {
                MDC.remove("requestId");
            }
        }
    }
}
