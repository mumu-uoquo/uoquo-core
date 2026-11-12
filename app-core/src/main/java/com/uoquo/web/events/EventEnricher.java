/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.IDGenerator;
import com.uoquo.utils.StringUtil;

import java.util.Date;

/**
 * 事件发布前的信息补充：把当前发起人的会话、请求、操作端、操作人等信息放入事件。<br>
 * 必须在<b>业务线程</b>中调用（{@link CurrentUser} 基于 ThreadLocal），业务代码已经设置的值不会被覆盖。
 *
 * @author xuhz
 */
public final class EventEnricher {

    private EventEnricher() {
    }

    /**
     * 补充事件的发起人信息，并返回本次事件使用的请求ID（用于日志链路标识）。
     * <ul>
     *   <li>事件ID：每次发布都重新生成（用于应用自身标识消息，回放时也会重新生成）；</li>
     *   <li>请求ID：事件已设置则沿用；否则取当前请求ID；当前请求没有时生成一个，并回写到事件，保证日志与事件一致；</li>
     *   <li>操作时间：与操作人相互独立，未设置时取当前时间。</li>
     * </ul>
     * @param event 待发布的事件（非 {@link UoquoEvent} 时不处理，仅返回请求ID）
     * @return 本次使用的请求ID
     * @throws IllegalArgumentException 业务类型或操作类型为空
     */
    public static String enrich(Object event) {
        String traceId = CurrentUser.getTraceId();
        if (!(event instanceof UoquoEvent uoquoEvent)) {
            return StringUtil.isNull(traceId) ? IDGenerator.getNextULID() : traceId;
        }
        if (StringUtil.isNull(uoquoEvent.getBusinessType()) || StringUtil.isNull(uoquoEvent.getOperationType())) {
            throw new IllegalArgumentException("businessType or operationType is null");
        }
        uoquoEvent.setEventId(IDGenerator.getNextULID());
        // 会话与请求
        if (StringUtil.isNull(uoquoEvent.getToken())) {
            uoquoEvent.setToken(CurrentUser.getToken());
        }
        if (StringUtil.isNull(uoquoEvent.getTraceId())) {
            uoquoEvent.setTraceId(StringUtil.isNull(traceId) ? IDGenerator.getNextULID() : traceId);
        }
        // 操作端
        if (StringUtil.isNull(uoquoEvent.getAppKey())) {
            uoquoEvent.setAppKey(CurrentUser.getAppkey());
            uoquoEvent.setAppDeviceId(CurrentUser.getDeviceId());
            uoquoEvent.setAppVersion(CurrentUser.getAppVersion());
            uoquoEvent.setAppIp(CurrentUser.getClientIp());
        }
        // 操作人
        if (StringUtil.isNull(uoquoEvent.getOperatorId())) {
            uoquoEvent.setOperatorId(CurrentUser.getInfo().getUserId());
            uoquoEvent.setOperatorName(CurrentUser.getInfo().getUserName());
        }
        if (StringUtil.isNull(uoquoEvent.getOperatorInstituteId())) {
            uoquoEvent.setOperatorInstituteId(CurrentUser.getInfo().getInstituteId());
        }
        // 操作时间与操作人相互独立
        if (uoquoEvent.getOperationTime() == null) {
            uoquoEvent.setOperationTime(new Date());
        }
        return uoquoEvent.getTraceId();
    }
}
