/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.bus.ServiceMatcher;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;

import java.util.function.Supplier;

/**
 * 服务级的“来自自己”判断，包装 Spring Cloud Bus 自带的 {@link ServiceMatcher}。
 *
 * <p>背景：{@link RemoteEvent} 的 {@code originService} 是<b>应用名</b>（如 {@code service-platform}），
 * 而 Bus 默认用实例ID（busId，如 {@code service-platform:000}）作为 origin 并与本实例ID匹配，
 * 两者不一致时，自己发出的事件会被当成“别人发的”，经 Kafka 回到自己再处理一次。</p>
 *
 * <p>规则：</p>
 * <ul>
 *   <li>{@link RemoteEvent}：{@code originService} 等于本服务名，就认为来自自己（服务级：同名服务的所有实例都视为“自己”）。
 *       发送方服务自己的监听器由发布时的本地分发处理，从总线收到的同名服务事件一律丢弃；</li>
 *   <li>其他事件（Bus 自带的配置刷新、Ack 等）：保持原有判断。</li>
 * </ul>
 * {@link #isForSelf(RemoteApplicationEvent)}、{@link #getBusId()} 直接委托。
 *
 * @author xuhz
 */
public class UoquoServiceMatcher implements ServiceMatcher {
    private static final Logger log = LoggerFactory.getLogger(UoquoServiceMatcher.class);

    private final ServiceMatcher delegate;

    private final Supplier<String> appNameSupplier;

    /** 本服务名（首次使用时获取，避免启动早期环境尚未就绪） */
    private volatile String appName;

    /**
     * @param delegate        Bus 自带的 ServiceMatcher
     * @param appNameSupplier 本服务名的获取方式（应与事件写入 originService 的取值一致）
     */
    public UoquoServiceMatcher(ServiceMatcher delegate, Supplier<String> appNameSupplier) {
        this.delegate = delegate;
        this.appNameSupplier = appNameSupplier;
    }

    @Override
    public boolean isFromSelf(RemoteApplicationEvent event) {
        if (event instanceof RemoteEvent<?>) {
            String name = appName();
            if (name != null && name.equals(event.getOriginService())) {
                return true;
            }
        }
        return delegate.isFromSelf(event);
    }

    @Override
    public boolean isForSelf(RemoteApplicationEvent event) {
        return delegate.isForSelf(event);
    }

    @Override
    public String getBusId() {
        return delegate.getBusId();
    }

    /**
     * 获取被包装的 ServiceMatcher。
     */
    public ServiceMatcher getDelegate() {
        return delegate;
    }

    private String appName() {
        String name = appName;
        if (name == null) {
            name = appNameSupplier.get();
            if (name == null || name.isBlank() || UoquoOriginFactory.TRANSIENT_ORIGIN.equals(name)) {
                // 没有配置应用名时无法按服务名判断，退回 Bus 的原有判断（此时事件的 origin 也是默认值，不能据此判断“来自自己”）
                log.warn("The application name is not configured (spring.application.name), "
                        + "remote events sent by this service can not be recognized as 'from self'.");
                name = "";
            }
            appName = name;
        }
        return name.isEmpty() ? null : name;
    }
}
