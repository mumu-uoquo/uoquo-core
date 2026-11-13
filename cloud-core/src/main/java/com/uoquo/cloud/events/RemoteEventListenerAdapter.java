/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.events;

import com.uoquo.web.events.AppEvent;
import com.uoquo.web.events.UoquoEventListenerAdapter;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.bus.ServiceMatcher;
import org.springframework.context.ApplicationEvent;

import java.lang.reflect.Method;

/**
 * 微服务事件（{@link RemoteEvent}）与本地事件（{@link AppEvent}）的监听适配器，行为见 {@link UoquoEventListenerAdapter}。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>来自消息队列的事件由 Spring Cloud Bus 在消息消费线程中同步发布，
 *       因此监听器统一异步执行，避免慢监听器占用消费线程；</li>
 *   <li>监听器抛出的异常只记录日志（不重试、不进入死信队列），
 *       因为同一事件可能有多个监听器，重投会导致其他监听器重复执行；</li>
 *   <li>发送方本地分发 {@link RemoteEvent} 时，只有目标包含本服务的事件才会交给监听器
 *       （广播事件、发给本服务的定向事件）；发给其他服务的定向事件不在本服务处理。
 *       从总线收到的事件已经由 Bus 按目标服务过滤过，这里的判断结果一致。</li>
 * </ul>
 *
 * @author xuhz
 */
public class RemoteEventListenerAdapter extends UoquoEventListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(RemoteEventListenerAdapter.class);

    /** Bus 的服务匹配器（没有启用 Bus 时为空，此时不按目标服务过滤） */
    private final ObjectProvider<ServiceMatcher> serviceMatcher;

    public RemoteEventListenerAdapter(String beanName, Class<?> targetClass, Method method) {
        this(beanName, targetClass, method, null);
    }

    public RemoteEventListenerAdapter(String beanName, Class<?> targetClass, Method method, ObjectProvider<ServiceMatcher> serviceMatcher) {
        super(beanName, targetClass, method, RemoteEvent.class, AppEvent.class);
        this.serviceMatcher = serviceMatcher;
    }

    @Override
    protected boolean accepts(@NonNull ApplicationEvent event) {
        if (serviceMatcher != null && event instanceof RemoteEvent<?> remoteEvent) {
            ServiceMatcher matcher = serviceMatcher.getIfAvailable();
            if (matcher != null && !matcher.isForSelf(remoteEvent)) {
                // 每个监听方法都会判断一次，只在 TRACE 输出，避免一个事件重复多条
                log.trace("skip remote event which is not for this service. destination: {}, event: {}",
                        remoteEvent.getDestinationService(), remoteEvent.getEventId());
                return false;
            }
        }
        return true;
    }

    @Override
    protected EventData dataOf(@NonNull ApplicationEvent event) {
        if (event instanceof RemoteEvent<?> remoteEvent) {
            return new EventData(remoteEvent.getDataType(), remoteEvent.getOldData(), remoteEvent.getNewData());
        }
        if (event instanceof AppEvent<?> appEvent) {
            return new EventData(appEvent.getDataType(), appEvent.getOldData(), appEvent.getNewData());
        }
        return null;
    }
}
