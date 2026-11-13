/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.events;

import com.uoquo.utils.Config;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

/**
 * {@link RemoteEvent} 的目标服务（{@code destinationService}）与本服务的匹配。
 *
 * <p>用于<b>没有 Spring Cloud Bus 的 ServiceMatcher</b>（例如 Bus 被关闭）时，判断一个事件是否发给本服务，
 * 规则与 Bus 的 {@code ServiceMatcher.isForSelf} 一致：以 {@code :} 为分隔符的 Ant 风格匹配。
 * 本服务的标识依次取：</p>
 * <ul>
 *   <li>实例ID：{@code spring.cloud.bus.id}（如 {@code demo-service-user:011}，配置了才有）；</li>
 *   <li>服务名：{@code spring.application.name}（与事件的 {@code originService} 取值一致）。</li>
 * </ul>
 * 目标为空视为广播；本服务的标识都取不到时无法判断，按“发给本服务”处理。
 * 例：目标 {@code **}、{@code demo-service-user}、{@code demo-service-user:**} 都匹配服务 {@code demo-service-user}。
 *
 * @author xuhz
 */
public final class RemoteEventDestination {

    private static final AntPathMatcher MATCHER = new AntPathMatcher(":");

    private static final UoquoOriginFactory ORIGIN_FACTORY = new UoquoOriginFactory();

    private RemoteEventDestination() {
    }

    /**
     * 目标是否包含本服务（本服务的标识从配置中获取）。
     *
     * @param destination 事件的目标服务
     */
    public static boolean isForLocalService(String destination) {
        return isForService(destination, ORIGIN_FACTORY.getOrigin(null), Config.getString("spring.cloud.bus.id"));
    }

    /**
     * 目标是否包含指定的服务。
     *
     * @param destination 事件的目标服务（空表示广播）
     * @param appName     服务名（可空，{@link UoquoOriginFactory#TRANSIENT_ORIGIN} 视为未配置）
     * @param busId       实例ID（可空）
     */
    public static boolean isForService(String destination, String appName, String busId) {
        if (!StringUtils.hasText(destination)) {
            return true;
        }
        String dest = destination.trim();
        boolean known = false;
        if (StringUtils.hasText(busId)) {
            known = true;
            if (MATCHER.match(dest, busId.trim())) {
                return true;
            }
        }
        if (StringUtils.hasText(appName) && !UoquoOriginFactory.TRANSIENT_ORIGIN.equals(appName)) {
            known = true;
            if (MATCHER.match(dest, appName.trim())) {
                return true;
            }
        }
        // 本服务的标识都取不到：无法判断，不丢弃事件
        return !known;
    }
}
