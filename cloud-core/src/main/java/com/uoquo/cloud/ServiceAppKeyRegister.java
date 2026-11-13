/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud;

import com.uoquo.utils.Config;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.spring.RedisUtil;
import com.uoquo.web.BaseCacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

/**
 * 描述：微服务启动就绪后，将当前服务名作为 appkey（secret 同为服务名）登记到 Redis. <br>
 * 背景：异步任务、定时任务等场景发起 Feign 调用时，{@code CurrentUser} 中没有 appkey，
 *      {@link com.uoquo.cloud.feign.FeignHeaderInterceptor} 会以当前服务名作为 appkey 和 secret 签名，
 *      被调用方需要能从 Redis 中查到该 appkey 对应的 secret，否则会抛出 AppkeyInvalidException。<br>
 * @author  uoquo team
 */
public class ServiceAppKeyRegister implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(ServiceAppKeyRegister.class);

    /** 未配置服务名时的占位值. */
    private static final String NONE = "none";

    /**
     * 获取当前服务名（配置项 {@code app.name}）.
     * @return 服务名；未配置时返回 null
     */
    public static String getAppName() {
        String appName = Config.getString("app.name", NONE);
        return (StringUtil.isNull(appName) || NONE.equals(appName)) ? null : appName;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String appName = getAppName();
        if (appName == null) {
            log.warn("未配置[app.name]，不登记服务名appkey，无用户上下文的Feign调用将无法签名.");
            return;
        }
        try {
            // 永久存储，appkey 与 secret 均为服务名
            RedisUtil.put(BaseCacheKey.APPKEY_SECRET_PREFIX + appName, appName, null);
            log.info("已登记服务名appkey：{}", appName);
        } catch (Exception e) {
            // Redis 异常不应影响服务启动
            log.error("登记服务名appkey[{}]到Redis失败.", appName, e);
        }
    }
}
