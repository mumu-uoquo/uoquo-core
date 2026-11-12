/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import com.uoquo.utils.CurrentUser;

/**
 * 监听器执行时的用户上下文处理：用事件中携带的发起人信息填充 {@link CurrentUser}，执行结束后恢复。
 *
 * <p>事件监听器在线程池（或消息消费线程）中执行，这些线程没有请求上下文。
 * 填充后，监听器中的代码（包括 Feign 调用的请求头）可以像在发起人的请求中一样获取 token、traceId、操作人等信息。</p>
 *
 * @author xuhz
 */
public final class EventContext {

    private EventContext() {
    }

    /**
     * 当前线程 {@link CurrentUser} 的快照（用于执行结束后恢复，避免监听器在调用方线程内执行时破坏调用方的上下文）。
     */
    public static final class Snapshot {
        private final String appkey;
        private final String token;
        private final String appType;
        private final String appSecret;
        private final String globalSecret;
        private final String appVersion;
        private final String clientIp;
        private final String nonce;
        private final String deviceId;
        private final String traceId;
        private final String language;
        private final CurrentUser.UserInfo info;
        private final boolean feignRequest;

        private Snapshot() {
            this.appkey = CurrentUser.getAppkey();
            this.token = CurrentUser.getToken();
            this.appType = CurrentUser.getAppType();
            this.appSecret = CurrentUser.getAppSecret();
            this.globalSecret = CurrentUser.getGlobalSecret();
            this.appVersion = CurrentUser.getAppVersion();
            this.clientIp = CurrentUser.getClientIp();
            this.nonce = CurrentUser.getNonce();
            this.deviceId = CurrentUser.getDeviceId();
            this.traceId = CurrentUser.getTraceId();
            this.language = CurrentUser.getLanguage();
            this.info = CurrentUser.getInfo();
            this.feignRequest = CurrentUser.isFeignRequest();
        }

        /**
         * 恢复到快照时的状态。
         */
        public void restore() {
            CurrentUser.clear();
            CurrentUser.setAppkey(appkey);
            CurrentUser.setToken(token);
            CurrentUser.setAppType(appType);
            CurrentUser.setAppSecret(appSecret);
            CurrentUser.setGlobalSecret(globalSecret);
            CurrentUser.setAppVersion(appVersion);
            CurrentUser.setClientIp(clientIp);
            CurrentUser.setNonce(nonce);
            CurrentUser.setDeviceId(deviceId);
            CurrentUser.setTraceId(traceId);
            CurrentUser.setLanguage(language);
            CurrentUser.setInfo(info);
            CurrentUser.setFeignRequest(feignRequest);
        }
    }

    /**
     * 记录当前线程的用户上下文。
     * @return 快照
     */
    public static Snapshot capture() {
        return new Snapshot();
    }

    /**
     * 用事件中的发起人信息替换当前线程的用户上下文。
     * @param event 事件
     */
    public static void apply(UoquoEvent event) {
        CurrentUser.clear();
        if (event == null) {
            return;
        }
        CurrentUser.setToken(event.getToken());
        CurrentUser.setTraceId(event.getTraceId());
        CurrentUser.setAppkey(event.getAppKey());
        CurrentUser.setDeviceId(event.getAppDeviceId());
        CurrentUser.setAppVersion(event.getAppVersion());
        CurrentUser.setClientIp(event.getAppIp());
        CurrentUser.UserInfo info = new CurrentUser.UserInfo();
        info.setUserId(event.getOperatorId());
        info.setUserName(event.getOperatorName());
        info.setInstituteId(event.getOperatorInstituteId());
        CurrentUser.setInfo(info);
    }
}
