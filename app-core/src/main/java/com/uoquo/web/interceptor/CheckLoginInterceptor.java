/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.interceptor;

import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.StringUtil;
import com.uoquo.web.BaseCacheKey;
import com.uoquo.annotation.web.IgnoreAuth;

import com.uoquo.web.exception.AccountKickOutException;
import com.uoquo.web.exception.ForbiddenException;
import com.uoquo.web.exception.TokenEmptyException;
import com.uoquo.web.exception.TokenInvalidException;
import com.uoquo.utils.spring.RedisUtil;
import com.uoquo.utils.SignParamUtil;
import com.uoquo.web.utils.WebUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpMethod;
import org.jspecify.annotations.NonNull;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 描述：登录检测（仅独立应用需要，微服务在网关层已经做校验了）. <br>
 * 背景：用于判断URL是否需要登录处理. <br>
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
public class CheckLoginInterceptor implements HandlerInterceptor {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
            throws Exception {
        // 如果是跨域的OPTIONS请求，放行
        if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 1. 免登过滤
        // 1.1 内部出错时的跳转不处理
        Throwable error = (Throwable)request.getAttribute(DefaultErrorAttributes.class.getName() + ".ERROR");
        if (error != null) {
            return true;
        }
        String path = request.getRequestURI();
        IgnoreAuth ignoreAuth = getIgnoreAuthAnnotation(handler);
        if (ignoreAuth.all()) {
            log.debug("[{}]注解[ignore all]跳过登录校验.", path);
            return true;
        }
        // 如果是内部微服务 FEIGN 发起的调用，则不需要登录认证。
        if (isFeignRequest(request)) {
            log.debug("[{}]内部微服务调用[appkey={}]免登.", path, CurrentUser.getAppkey());
            return true;
        }
        // 1.2 免登陆校验（如登录、下载等接口）
        if (ignoreAuth.login() || path.endsWith("/login")) {
            log.debug("[{}]注解[ignore login]跳过登录校验.", path);
            return true;
        }
        boolean accept = RedisUtil.existSetItem(BaseCacheKey.GLOBAL_PERMISSION, path);
        if (accept) {
            log.debug("[{}]全局免登.", path);
            return true;
        }
        // 1.3 部分业务（如：下载）可以不校验登陆
        boolean unsigned = RedisUtil.existSetItem(BaseCacheKey.GLOBAL_UNSIGNED, path);
        if (unsigned) {
            log.debug("[{}]全局免签.", path);
            return true;
        }
        // 2. 登录校验
        // 第三方平台（appkey）对接也改造为先获取token，再访问业务的逻辑
        String token = CurrentUser.getToken();
        CurrentUser.UserInfo user = CurrentUser.getInfo();
//        // 第三方应用权限判断（方法1：不登录获取token，只判断资源授权）
//        if (user == null || StringUtil.isNull(user.getUserId())) {
//            // 若是第三方发起的调用，此时跟用户无关，仅需判断应用的权限
//            accept = RedisUtil.existSetItem(BaseCacheKey.APPKEY_PERMISSION_PREFIX + CurrentUser.getAppkey(), path);
//            if (accept) {
//                log.debug("[{}]独立授权[{}].", path, CurrentUser.getAppkey());
//                return true;
//            }
//            if (StringUtil.isNull(token)) {
//                throw new ForbiddenException();
//            } else {
//                // 有token，却无用户信息，说明token已失效
//                throw new TokenInvalidException();
//            }
//        }
        // 3. 登录校验
        // token判断（必须）
        if (StringUtil.isNull(token)) {
            throw new TokenEmptyException();
        }
        // 3.1 应用授权判断（方法2：需登录获取token，并判断资源授权）
        String appToken = RedisUtil.get(BaseCacheKey.APPKEY_TOKEN_PREFIX + CurrentUser.getAppkey(), String.class);
        if (StringUtil.notNull(appToken) && token.equals(appToken)) {
            accept = RedisUtil.existSetItem(BaseCacheKey.APPKEY_PERMISSION_PREFIX + CurrentUser.getAppkey(), path);
            if (accept) {
                log.debug("[{}]独立授权[{}].", path, CurrentUser.getAppkey());
                return true;
            } else {
                throw new ForbiddenException();
            }
        }
        // 3.2 用户授权判断（是否被踢）
        if (user == null || StringUtil.isNull(user.getUserId())) {
            // 有token，却无用户信息，说明token已失效
            // TODO 产生超时事件，并通知
            throw new TokenInvalidException();
        }
        String loginTokenCacheKey = BaseCacheKey.USER_TOKEN_PREFIX + user.getUserId() +":"+ CurrentUser.getAppkey();
        String loginToken = RedisUtil.get(loginTokenCacheKey, String.class);
        if (StringUtil.isNull(loginToken)) {
            log.warn("用户[{}]缓存的登录token为空，用当前token[{}]补充.", user.getUserId(), token);
            RedisUtil.put(loginTokenCacheKey, token, user.getExpires());
        } else if (!token.equals(loginToken)) {
            log.debug("用户[{}]的请求token[{}]不是最新的登录token[{}]，所以当前的客户端已经被踢下线.", user.getUserId(), loginToken, token);
            // 传入的token与最新缓存的token不一致，说明被踢下线了（在被踢时已经发布过事件，此处不重复发送）
            RedisUtil.clearLocalCache(BaseCacheKey.USER_INFO_PREFIX + token);
            throw new AccountKickOutException();
        }
        // 3.3 用户授权判断（校验权限）
        if (path.endsWith("/permission") || path.endsWith("/logout")) {
            // 清理本地缓存的用户信息（授权接口会重新设置缓存的用户信息）
            RedisUtil.clearLocalCache(BaseCacheKey.USER_INFO_PREFIX + token);
            // 清理本地所有缓存（从而减少定时清理的逻辑）
            RedisUtil.clearLocalCache();
        } else if (RedisUtil.existSetItem(BaseCacheKey.GLOBAL_ALL_RESOURCE, path)) {
            // 权限校验（登记到系统中的resource必须授权后才能访问）
            accept = RedisUtil.existSetItem(BaseCacheKey.ROLE_PERMISSION_PREFIX + user.getCurrentRoleId(), path);
            if (!accept) {
                throw new ForbiddenException();
            }
        }
//        // 2025-11-08：不再自动刷新，改由请求方采用freshToken机制无感刷新，从而减少服务端对redis的操作，以及减少token泄露后的隐患
//        // 4 更新缓存时间（默认30 * 60秒）
//        if (user.getExpires() != null && ignoreAuth.refreshExpiresTime()) {
//            int timeout = user.getExpires() == 0 ? 1800 : user.getExpires();
//            RedisUtil.expire(BaseCacheKey.USER_INFO_PREFIX + token, timeout);
//            RedisUtil.expire(loginTokenCacheKey, timeout);
//        }
        return true;
    }

    /**
     * 判断是否是内部微服务调用.
     */
    private boolean isFeignRequest(HttpServletRequest request) {
        // feign 和 gateway 的参数必须是请求头中
        String feignSign = request.getHeader(CurrentUser.FEIGN_SIGN);
        String signGlobal = request.getHeader(CurrentUser.GATEWAY_SIGN);
        // 前端的签名可能在url中
        String signParams = WebUtil.getHeader(CurrentUser.SIGN_APP, request);
        if (StringUtil.notNull(feignSign) && StringUtil.notNull(signGlobal) && StringUtil.notNull(signParams)) {
            String calcFeignSign = SignParamUtil.sign(signParams + signGlobal, CurrentUser.getGlobalSecret());
            return feignSign.equalsIgnoreCase(calcFeignSign);
        }
        return false;
    }

    /**
     * 获取注解.
     */
    private IgnoreAuth getIgnoreAuthAnnotation(@NonNull Object handler) {
        IgnoreAuth annotation = null;
        // 优先找方法的注解
        if (handler instanceof HandlerMethod hm) {
            annotation = hm.getMethodAnnotation(IgnoreAuth.class);
            if (annotation == null) {
                annotation = AnnotationUtils.findAnnotation(hm.getBeanType(), IgnoreAuth.class);
            }
        }
        // 再找类的注解
        if (annotation == null)  {
            annotation = AnnotationUtils.findAnnotation(ClassUtils.getUserClass(handler), IgnoreAuth.class);
        }
        // 当没有注解时，创建一个默认的
        if (annotation == null) {
            annotation = AnnotationUtils.synthesizeAnnotation(IgnoreAuth.class);
        }
        return annotation;
    }
}
