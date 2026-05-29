/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.interceptor;

import com.uoquo.utils.StringUtil;
import com.uoquo.utils.CurrentUser;
import com.uoquo.web.BaseCacheKey;
import com.uoquo.utils.spring.RedisUtil;
import com.uoquo.web.utils.WebUtil;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.HttpMethod;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 描述：登录用户信息拦截. <br>
 * 背景：通过token到redis中获取用户信息. <br>
 * 日期：2018-01-25 11:13 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-25     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class CurrentUser4TokenInterceptor extends CurrentUserInterceptorAdapter {

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
            throws Exception {
        // 如果是跨域的OPTIONS请求，放行
        if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 1. 如果是内部出错的跳转（此时保持用户信息）
        Throwable error = (Throwable)request.getAttribute(DefaultErrorAttributes.class.getName() + ".ERROR");
        if (error != null) {
            return true;
        }
        // 2. 清除线程缓存数据
        CurrentUser.clear();
        // 3. 缓存用户信息
        // 3.1 先根据appinfo补充机构ID
        String appid = WebUtil.getHeader(CurrentUser.APPID, request);
        CurrentUser.AppInfo appInfo = RedisUtil.getLocalCache(BaseCacheKey.APPKEY_INFO_PREFIX + appid, CurrentUser.AppInfo.class);
        if (appInfo != null) {
            if (StringUtil.notNull(appInfo.getInstituteId())) {
                CurrentUser.getInfo().setInstituteId(appInfo.getInstituteId());
            }
            CurrentUser.setAppType(appInfo.getType());
        }
        // 3.2 根据token填充用户信息
        String token = WebUtil.getHeader(CurrentUser.TOKEN, request);
        if (StringUtil.notNull(token)) {
            // 2025-03-05：此处不能用RedisUtil.getLocalCache，因为后续有setNonce等赋值操作，若从缓存获取，容易影响前一个请求
            CurrentUser.UserInfo user = RedisUtil.get(BaseCacheKey.USER_INFO_PREFIX + token, CurrentUser.UserInfo.class);
            if (user != null) {
                CurrentUser.setInfo(user);
            }
        }
        // 补全用户其他信息
        this.completionCurrentUser(request);
        return true;
    }

}
