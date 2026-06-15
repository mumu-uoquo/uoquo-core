/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.interceptor;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpMethod;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.uoquo.annotation.web.IgnoreAuth;
import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.IDGenerator;
import com.uoquo.utils.SignParamUtil;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.utils.spring.RedisUtil;
import com.uoquo.web.BaseCacheKey;
import com.uoquo.web.exception.AppkeyInvalidException;
import com.uoquo.web.utils.WebUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 描述：登录用户信息拦截. <br>
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
public abstract class CurrentUserInterceptorAdapter  implements HandlerInterceptor {
    protected Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * Controller方法处理完之后，DispatcherServlet进行视图的渲染之前
     */
    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, @Nullable Exception ex)
            throws Exception {
        // 1. 如果是跨域的OPTIONS请求，不处理
        if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
            return;
        }
        // 2. 如果是内部错误，因后续还是在同一个线程处理，所以需保留当前用户信息
        if (ex != null) {
            return;
        }
        Throwable error = (Throwable)request.getAttribute(DefaultErrorAttributes.class.getName() + ".ERROR");
        if (error != null) {
            return;
        }
        // 3. 业务处理完成，需清除当前用户信息
        CurrentUser.clear();
    }

    /**
     * 补全当前用户信息<br/>
     * 如：nonce、requestId、language、clientIp
     */
    protected void completionCurrentUser(HttpServletRequest request) {
        // 1. app信息
        // appkey
        String appkey = WebUtil.getHeader(CurrentUser.APPID, request);
        if (StringUtil.notNull(appkey)) {
            CurrentUser.setAppkey(appkey);
        }
        // appType
        String appType = WebUtil.getHeader(CurrentUser.APP_TYPE, request);
        if (StringUtil.notNull(appType)) {
            CurrentUser.setAppType(appType);
        }
        // appVersion
        String appVersion = WebUtil.getHeader(CurrentUser.APP_VERSION, request);
        if (StringUtil.notNull(appVersion)) {
            CurrentUser.setAppVersion(appVersion);
        }
        // secret
        String appSecret  = getAppSecret(CurrentUser.getAppkey());
        CurrentUser.setAppSecret(appSecret);
        // globalSecret
        String globalSecret = getGlobalSecret();
        CurrentUser.setGlobalSecret(globalSecret);

        // 2. 用户信息
        // token
        String token = WebUtil.getHeader(CurrentUser.TOKEN, request);
        if (StringUtil.notNull(token)) {
            CurrentUser.setToken(token);
        }
        // nonce
        String nonce = WebUtil.getHeader(CurrentUser.NONCE, request);
        if (StringUtil.notNull(nonce)) {
            CurrentUser.setNonce(nonce);
        }
        // deviceId
        String deviceId = WebUtil.getHeader(CurrentUser.DEVICE_ID, request);
        if (StringUtil.notNull(deviceId)) {
            CurrentUser.setDeviceId(deviceId);
        }
        // 如果没有机构信息，则从补充appid对应的机构ID（大多出现在内部定时任务发起的remote请求，此时可能只有appid，没有机构ID）
        if (StringUtil.isNull(CurrentUser.getInfo().getInstituteId())) {
            CurrentUser.AppInfo appInfo = RedisUtil.getLocalCache(BaseCacheKey.APPKEY_INFO_PREFIX + appkey, CurrentUser.AppInfo.class);
            if (appInfo != null) {
                CurrentUser.getInfo().setInstituteId(appInfo.getInstituteId());
            }
        }
        // 请求ID
        String rid = null;
        // 如果是微服务，则从请求头中获取
        String gatewayTime = request.getHeader(CurrentUser.GATEWAY_TIME);
        String gatewaySign = request.getHeader(CurrentUser.GATEWAY_SIGN);
        if (StringUtil.notNull(gatewayTime) && StringUtil.notNull(gatewaySign)) {
            rid = request.getHeader(CurrentUser.TRACE_ID);
        }
        // 如果前端没有出传请求ID，则以日志的请求ID为准
        if (StringUtil.isNull(rid)) {
            rid = MDC.get("requestId");
        }
        if (StringUtil.isNull(rid)) {
            rid = IDGenerator.getNextULID();
        }
        CurrentUser.setTraceId(rid);
        MDC.put("requestId", rid);
        // 客户端语言
        String lan = WebUtil.getHeader(CurrentUser.USER_LANGUAGE, request);
        if (StringUtil.notNull(lan)) {
            CurrentUser.setLanguage(lan);
        }
        // 客户端IP
        String cip = request.getHeader(CurrentUser.CLIENT_IP);
        if (StringUtil.notNull(cip)) {
            CurrentUser.setClientIp(cip);
        } else {
            CurrentUser.setClientIp(WebUtil.getClientIp(request));
        }
        // 标记是否为内部微服务 Feign 调用（供序列化器判断是否跳过加解密）
        CurrentUser.setFeignRequest(isFeignRequest(request));

        // 调试日志
        if (log.isDebugEnabled()) {
            log.debug("解析到的用户信息：{}", JsonUtil.serialize(CurrentUser.getInfo()));
        }
    }

    /**
     * 判断是否是内部微服务 Feign 调用.
     */
    protected boolean isFeignRequest(HttpServletRequest request) {
        String feignSign  = request.getHeader(CurrentUser.FEIGN_SIGN);
        String signGlobal = request.getHeader(CurrentUser.GATEWAY_SIGN);
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
    protected IgnoreAuth getIgnoreAuthAnnotation(Object handler) {
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

    /**
     * 获取应用密钥.
     */
    protected String getAppSecret(String appkey) {
        if (StringUtil.isNull(appkey)) {
            return null;
        }
        String secret = RedisUtil.getLocalCache(BaseCacheKey.APPKEY_SECRET_PREFIX + appkey, String.class);
        if (StringUtil.isNull(secret)) {
            log.error("[appkey={}]没有缓存的secret", appkey);
            throw new AppkeyInvalidException();
        }
        return secret;
    }

    /**
     * 获取全局密钥.
     */
    protected String getGlobalSecret() {
        String secret = RedisUtil.getLocalCache(BaseCacheKey.GLOBAL_SECRET, String.class);
        if (StringUtil.isNull(secret)) {
            log.error("没有缓存的全局secret");
            throw new AppkeyInvalidException();
        }
        return secret;
    }
}
