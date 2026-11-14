/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.interceptor;

import com.uoquo.utils.CurrentUser;
import com.uoquo.web.interceptor.CheckLoginInterceptor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpMethod;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 描述：微服务的登录检测. <br>
 * 背景：微服务的登录校验已经由网关完成（未通过网关，直接发起的请求，会在签名校验时因网关签名不正确被拒绝），
 *      服务内不再重复校验登录，从而减少 Redis 调用.<br>
 * 备注：授权、退出接口仍需清理本地缓存（与独立应用保持一致）.
 * @author  uoquo team
 */
public class CheckLogin4GatewayInterceptor extends CheckLoginInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
            throws Exception {
        // 如果是跨域的OPTIONS请求，放行
        if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (isPermissionPath(request.getRequestURI())) {
            clearLocalCache(CurrentUser.getToken());
        }
        return true;
    }
}
