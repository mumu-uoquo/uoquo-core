/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.interceptor;

import com.uoquo.utils.StringUtil;
import com.uoquo.utils.CurrentUser;
import com.uoquo.web.BaseCacheKey;
import com.uoquo.annotation.web.IgnoreAuth;
import com.uoquo.web.exception.ParamSignEmptyException;
import com.uoquo.web.exception.ParamSignErrorException;
import com.uoquo.utils.spring.RedisUtil;
import com.uoquo.web.interceptor.CheckParamInterceptor;
import com.uoquo.utils.SignParamUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.uoquo.web.utils.WebUtil;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.HttpMethod;

/**
 * 描述：微服务全局拦截�? <br>
 * 背景：用于获取从前端外围系统传入的用户信息，并进行签名校�? <br>
 * 日期�?018-01-25 11:13 <br>
 * 变更�?
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-25     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class CheckParam4GatewayInterceptor extends CheckParamInterceptor {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
            throws Exception {
        // 如果是跨域的OPTIONS请求，放�?
        if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 1. 免签过滤
        // 1.1 内部出错时的跳转不处理（理论上不应该有这个判断，应该交由拦截器注入的地方配置�?
        Throwable error = (Throwable)request.getAttribute(DefaultErrorAttributes.class.getName() + ".ERROR");
        if (error != null) {
            return true;
        }
        String path = request.getRequestURI();
        IgnoreAuth ignoreAuth = getIgnoreAuthAnnotation(handler);
        if (ignoreAuth.all()) {
            log.debug("[{}]注解[ignore all]跳过参数签名校验.", path);
            return true;
        }
        // 1.2 部分业务（如：下载）可以不校验签�?
        boolean unsigned = RedisUtil.existSetItem(BaseCacheKey.GLOBAL_UNSIGNED, path);
        if (unsigned) {
            log.debug("[{}]全局免签.", path);
            return true;
        }
        // 如果是内部微服务 FEIGN 发起的调用，则不需要登录认证�?
        if (isFeignRequest(request)) {
            log.debug("[{}]内部微服务调用[appkey={}]免签.", path, CurrentUser.getAppkey());
            return true;
        }
        // 2. 参数校验
        // 2.1 时间戳校�?
        if (!ignoreAuth.timestamp()) {
            this.checkTimestamp(request);
        } else if (log.isDebugEnabled()) {
            log.debug("[{}]注解[ignore timestamp]跳过时间戳校�?", path);
        }
        // 2.2 防重提交校验
        this.checkResubmit(request);
        // 2.3 参数签名校验
        if (!ignoreAuth.params()) {
            // 网关签名校验（防止直接访问应用）
            this.checkGatewayParams(request);
            // 参数签名校验
            this.checkParams(request);
        } else if (log.isDebugEnabled()) {
            log.debug("[{}]注解[ignore params]跳过参数签名校验.", path);
        }
        return true;
    }

    /**
     * 网关签名校验
     */
    protected void checkGatewayParams(HttpServletRequest request) throws Exception {
        // 网关签名信息
        String reqGatewaySign = request.getHeader(CurrentUser.GATEWAY_SIGN);
        if (StringUtil.isNull(reqGatewaySign)) {
            throw new ParamSignEmptyException(CurrentUser.GATEWAY_SIGN);
        }
        String timestamp = request.getHeader(CurrentUser.GATEWAY_TIME);
        if (StringUtil.isNull(timestamp)) {
            throw new ParamSignEmptyException(CurrentUser.GATEWAY_TIME);
        }
        // 客户端签名信�?
        String reqAppSign = WebUtil.getHeader(CurrentUser.SIGN_APP, request);
        if (StringUtil.isNull(reqAppSign)) {
            throw new ParamSignEmptyException(CurrentUser.SIGN_APP);
        }
        // 缓存全局通信秘钥
        String globalSecret = CurrentUser.getGlobalSecret();
        // 网关签名校验
        String clcGlobalSecret = SignParamUtil.sign(reqAppSign + timestamp, globalSecret);
        if (!reqGatewaySign.equalsIgnoreCase(clcGlobalSecret)) {
            log.debug("网关签名原文[{}]，计算的密文[{}]，传入的密文[{}]", reqAppSign + timestamp, clcGlobalSecret, reqGatewaySign);
            throw new ParamSignErrorException(CurrentUser.SIGN_APP);
        }
    }
}
