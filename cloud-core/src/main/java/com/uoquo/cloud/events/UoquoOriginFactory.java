/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.events;

import com.uoquo.utils.Config;
import com.uoquo.utils.StringUtil;

/**
 * 获取原服务名
 * @author xuhz
 */
public class UoquoOriginFactory {
    /**
     * 默认事件源服务名<br>
     * 不能用 RemoteApplicationEvent.TRANSIENT_ORIGIN，否则会出现NPE异常导致事件发布不成功
     */
//    protected static final String TRANSIENT_ORIGIN = "____transient_origin_service___";
    private static final String TRANSIENT_ORIGIN = "DEFAULT_TRANSIENT_ORIGIN";

    public String getOrigin(String serviceName) {
        if (StringUtil.notNull(serviceName)) {
            return serviceName;
        }
        serviceName = Config.getString("spring.application.name");
        if (StringUtil.notNull(serviceName)) {
            return serviceName;
        }
        serviceName = Config.getString("app.name");
        if (StringUtil.notNull(serviceName)) {
            return serviceName;
        }
        return TRANSIENT_ORIGIN;
    }
}
