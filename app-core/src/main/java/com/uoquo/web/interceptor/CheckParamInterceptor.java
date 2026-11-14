/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.interceptor;

import com.uoquo.utils.StringUtil;
import com.uoquo.utils.CurrentUser;
import com.uoquo.web.BaseCacheKey;
import com.uoquo.annotation.web.IgnoreAuth;
import com.uoquo.web.exception.*;
import com.uoquo.utils.spring.RedisUtil;
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
import java.time.Clock;
import java.time.Duration;

/**
 * 描述：参数检测. <br>
 * 背景：判断传入的参数是否被篡改，需要在CurrentUser拦截之后. <br>
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
public class CheckParamInterceptor implements HandlerInterceptor {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    // 时间戳有效时间（默认5分钟）
    protected static final Duration TIMESTAMP_MAX = Duration.ofSeconds(5 * 60);

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
            throws Exception {
        // 如果是跨域的OPTIONS请求，放行
        if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 1. 免签过滤
        // 1.1 内部出错时的跳转不处理（理论上不应该有这个判断，应该交由拦截器注入的地方配置）
        Throwable error = (Throwable)request.getAttribute(DefaultErrorAttributes.class.getName() + ".ERROR");
        if (error != null) {
            return true;
        }
        String path = request.getRequestURI();
        boolean isFeignRequest  = WebUtil.isFeignRequest(request);
        IgnoreAuth ignoreAuth = getIgnoreAuthAnnotation(handler);
        // 1.2 内部接口：只能由内部微服务FEIGN发起调用（必须先于 all 判断，否则 all 会让 inner 失效）
        if (ignoreAuth.inner()) {
            if (!isFeignRequest) {
                log.warn("内部接口[{}]被[{}]调用.", path, CurrentUser.getClientIp());
                throw new ForbiddenException("无权调用内部接口");
            }
            // 内部微服务 FEIGN 发起的调用，不需要验签。
            return true;
        }
        // 1.3 来源校验
        if (isFeignRequest) {
            // 内部微服务 FEIGN 发起的调用，不需要验签。
            log.debug("[{}]内部微服务调用[appkey={}]免签.", path, CurrentUser.getAppkey());
            return true;
        } else {
            // 微服务必须经过网关转发，包括免签、忽略所有校验的接口；单体不需要
            this.checkSource(request);
        }
        // 1.4 忽略所有校验
        if (ignoreAuth.all()) {
            log.debug("[{}]注解[ignore all]跳过参数签名校验.", path);
            return true;
        }
        // 2. 参数校验
        // SSE/WebSocket 长连接：客户端断线重连会携带原来的时间戳和 nonce，因此不做时间戳和防重校验，
        // 且 SSE response 处于异步模式，写入普通错误响应会触发 AsyncRequestNotUsableException。
        // 长连接的身份仍由签名（以及登录校验）保证。
        boolean longLived = WebUtil.isLongLivedRequest(request);
        // 2.1 时间戳校验
        if (longLived) {
            log.debug("[{}]长连接请求跳过时间戳校验.", path);
        } else if (!ignoreAuth.timestamp()) {
            this.checkTimestamp(request);
        } else if (log.isDebugEnabled()) {
            log.debug("[{}]注解[ignore timestamp]跳过时间戳校验.", path);
        }
        // 2.2 参数签名校验
        if (!ignoreAuth.params()) {
            this.checkParams(request);
            // 2.3 防重提交校验
            // 必须在验签通过之后：避免未通过验签的请求往 Redis 写入 nonce；不验签的请求，防重没有意义，因此也不做
            if (!longLived) {
                this.checkResubmit(request);
            }
        } else if (log.isDebugEnabled()) {
            log.debug("[{}]注解[ignore params]跳过参数签名及防重校验.", path);
        }
        return true;
    }

    /**
     * 请求来源校验.<br>
     * 单体应用不需要校验；微服务重写该方法，校验请求是否由网关转发.
     */
    protected void checkSource(HttpServletRequest request) throws Exception {
        // 单体应用：不校验
    }

    /**
     * 防重提交校验（需在签名校验通过后调用，长连接请求不调用）.
     */
    protected void checkResubmit(HttpServletRequest request) throws Exception {
        // 若无签名，则不做防重判断
        String reqAppSign = WebUtil.getHeader(CurrentUser.SIGN_APP, request);
        if (StringUtil.isNull(reqAppSign)) {
            return;
        }
        String nonce = CurrentUser.getNonce();
        if (StringUtil.isNull(nonce)) {
            throw new ParamSignEmptyException(CurrentUser.NONCE);
        }
        // 防重校验
        // 20250924：不仅仅校验nonce，防止多个平台的nonce规则一致导致生成的nonce重复，从而误判
        String nonceKey = BaseCacheKey.REQUEST_NONCE_PREFIX + CurrentUser.getAppkey() + nonce;
        if (RedisUtil.exist(nonceKey)) {
            throw new ParamErrorException(String.format("在 %d 秒内不可以重复提交", TIMESTAMP_MAX.toSeconds()));
        } else {
            RedisUtil.put(nonceKey, reqAppSign, (int)TIMESTAMP_MAX.toSeconds());
            log.debug("防重提交校验成功：nonce=[{}], app-sign=[{}], time=[{}]", nonce, reqAppSign, System.currentTimeMillis());
        }
    }

    /**
     * 时间戳校验
     */
    protected void checkTimestamp(HttpServletRequest request) throws Exception {
        // 若无签名，则不做时间戳校验（需要签名的接口，会在签名校验时抛出签名为空的异常）
        String reqAppSign = WebUtil.getHeader(CurrentUser.SIGN_APP, request);
        if (StringUtil.isNull(reqAppSign)) {
            return;
        }
        String timestr = WebUtil.getHeader(CurrentUser.TIME, request);
        if (StringUtil.isNull(timestr)) {
            throw new ParamSignEmptyException(CurrentUser.TIME);
        }
        long timestamp = 0;
        try {
            timestamp = Long.parseLong(timestr);
        } catch (Exception e) {
            log.warn("请求头必须包含正确的请求时间戳：{}", timestr, e);
            throw new ParamSignErrorException(CurrentUser.TIME);
        }
        // 防止请求端时间偏差，因此取绝对值
        long diffms = Math.abs(Clock.systemUTC().millis() - timestamp);
        if ( diffms > TIMESTAMP_MAX.toMillis() ) {
            log.warn("请求头必须包含正确的请求时间戳：server[{}], request[{}], diff = {} ms", Clock.systemUTC().millis(), timestamp, diffms);
            throw new ParamSignErrorException(CurrentUser.TIME);
        }
    }

    /**
     * 签名校验
     */
    protected void checkParams(HttpServletRequest request) throws Exception {
        // 3.1 appid
        if (StringUtil.isNull(CurrentUser.getAppkey())) {
            throw new AppkeyEmptyException();
        }
        // 3.2 传入的签名
        String reqAppSign = WebUtil.getHeader(CurrentUser.SIGN_APP, request);
        if (StringUtil.isNull(reqAppSign)) {
            throw new ParamSignEmptyException(CurrentUser.SIGN_APP);
        }
        // 3.3 语言校验
        if (StringUtil.isNull(CurrentUser.getLanguage())) {
            throw new ParamSignEmptyException(CurrentUser.USER_LANGUAGE);
        }
        // 3.4 随机值校验
        if (StringUtil.isNull(CurrentUser.getNonce())) {
            throw new ParamSignEmptyException(CurrentUser.NONCE);
        }
        // 3.5 机器码校验
        if (StringUtil.isNull(CurrentUser.getDeviceId())) {
            throw new ParamSignEmptyException(CurrentUser.DEVICE_ID);
        }
        // 3.6 签名校验
        String appSecret  = CurrentUser.getAppSecret();
        String clcAppSign = WebUtil.signParam(appSecret, request);
        if (!reqAppSign.equalsIgnoreCase(clcAppSign)) {
            throw new ParamSignErrorException(CurrentUser.SIGN_APP);
        }
    }

    /**
     * 获取注解.
     */
    protected IgnoreAuth getIgnoreAuthAnnotation(@NonNull Object handler) {
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
