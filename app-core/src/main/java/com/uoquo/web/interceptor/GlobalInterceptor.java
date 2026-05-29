/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.interceptor;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.uoquo.utils.CompressUtil;
import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.IDGenerator;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.filter.RepeatedlyHttpServletRequestWrapper;
import com.uoquo.web.mybatis.page.PageHelper;
import com.uoquo.web.utils.WebUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 描述：全局拦截器. <br>
 * 背景：内置的全局拦截器，仅用于记录慢请求，清理数据等. <br>
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
public class GlobalInterceptor implements HandlerInterceptor {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Logger logRequest = LoggerFactory.getLogger("ACCESS_REQUEST_ALL");
    private final Logger logResponse = LoggerFactory.getLogger("ACCESS_RESPONSE_ALL");

    @Value("${spring.cloud.client.ip-address:${server.address:unknown}}")
    protected String serverIp;

    @Value("${server.port}")
    protected Integer serverPort;

    /**
     * 请求时间
     */
    public final static String REQUEST_EXECUTE_TIME = "REQUEST_EXECUTE_TIME";

    /**
     * Controller方法处理之前
     */
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
            throws Exception {
        // 如果是跨域的OPTIONS请求，放行
        if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 记录请求开始时间
        request.setAttribute(REQUEST_EXECUTE_TIME, System.currentTimeMillis());
        // 清除线程缓存数据
        PageHelper.clearPage();
        // 记录请求日志
        if (StringUtil.isNull(CurrentUser.getTraceId())) {
            String rid = request.getHeader(CurrentUser.TRACE_ID);
            if (StringUtil.isNull(rid)) {
                // 如果前端没有出传请求ID，则以日志的请求ID为准
                rid = MDC.get("requestId");
            }
            if (StringUtil.isNull(rid)) {
                rid = IDGenerator.getNextULID();
            }
            CurrentUser.setTraceId(rid);
            MDC.put("requestId", rid);
        }
        // 请求日志
        if (logRequest.isDebugEnabled()) {
            saveRequestLog(request);
        }
        // 只有返回true才会继续向下执行，返回false取消当前请求
        return true;
    }

    /**
     * Controller方法处理完之后，DispatcherServlet进行视图的渲染之前
     */
    @Override
    public void postHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, @Nullable ModelAndView modelAndView) 
            throws Exception {
        // spring-session在执行postHandle之后会保存到redis，所以在此处清空所有非必须的数据
    }

    /**
     * DispatcherServlet进行视图的渲染之后
     */
    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, @Nullable Exception ex)
            throws Exception {
        // 记录日志
        if (ex != null) {
            saveAccessLog(request, "ERROR", ex);
        } else {
            // 如果是参数解析错误（或者spring抛出的其他错误）将由 DefaultHandlerExceptionResolver 解析，此时ex为空
            Throwable error = (Throwable) request.getAttribute(DefaultErrorAttributes.class.getName() + ".ERROR");
            if ((error != null) || (HttpServletResponse.SC_OK != response.getStatus())) {
                saveAccessLog(request, "ERROR", error);
            } else {
                saveAccessLog(request, "SUCCESS", null);
            }
        }
        // 记录响应日志（放在 postHandle 中将无法获取出错时的响应内容）
        if (logResponse.isDebugEnabled()) {
            saveResponseLog(request, response);
        }
        // 清理当前线程中的缓存信息（有可能在错误处理的地方需要用到CurrentUser，所以此处不清理）
        request.removeAttribute(REQUEST_EXECUTE_TIME);
    }

    /**
     * 记录日志（含执行耗时、用户信息、简单入参等）
     */
    private void saveAccessLog(HttpServletRequest request, String status, Throwable ex) {
        // 1. 计算执行耗时
        double sec = 0;
        Long bgn = (Long) request.getAttribute(REQUEST_EXECUTE_TIME);
        if (bgn != null) {
            sec = (System.currentTimeMillis() - bgn) / 1000d;
        }
        // 2. 请求参数处理
        //String path  = request.getRequestURI().substring(request.getContextPath().length());
        String path    = request.getRequestURI();
        String pattern = (String)request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String clientIp  = StringUtil.isNull(CurrentUser.getClientIp()) ? WebUtil.getClientIp(request) : CurrentUser.getClientIp();
        // 第三方直接调用时userId为空，此时也应该记录相关日志
        CurrentUser.UserInfo user = CurrentUser.getInfo();
        String userInfo  = JsonUtil.serialize(user);
        // 2.1 请求头
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String key = headerNames.nextElement();
            if ("user-agent".equalsIgnoreCase(key)) {
                continue;
            }
            String value = request.getHeader(key);
            headers.put(key, value);
        }
        // 2.2 请求参数
        Map<String, Object> params = WebUtil.getRequestParams(request);
        // 2.3 请求体（非上传文件的请求体）
        String body = null;
        String contentType = request.getHeader("Content-Type");
        contentType = (contentType == null) ? "" : contentType.toLowerCase();
        if (request instanceof RepeatedlyHttpServletRequestWrapper) {
            body = WebUtil.getRequestBody(request);
        } else {
            body = "Cannot read body for [" + contentType + "]";
        }
        // 3. 记录日志
        // INFO只记录1000字符以内的数据（减少日志量）
        if ((body != null) && (body.length() > 1000)) {
            body = body.substring(0, 1000) + "...";
        }
        // 3.1 错误日志始终记录（此处仅简单记录，详细信息由错误统一处理的地方记录）
        if ("ERROR".equals(status)) {
            log.error("request [{}] [{}] [{}] [{}] [{}] [{}s]. server={}:{}, pattern={}, appkey={}, client_ip={}, device={}, token={}, user={}, params={}, body={}, error={} .",
                    CurrentUser.getNonce(), status, request.getMethod(), path, contentType, String.format("%.3f", sec),
                    serverIp, serverPort, pattern, CurrentUser.getAppkey(), clientIp, CurrentUser.getDeviceId(), CurrentUser.getToken(), userInfo, params, body,
                    (ex == null) ? null : ex.getMessage());
            return;
        }
        // 3.2 正常信息按级别记录
        if (sec >= 1) {
            log.warn("request [{}] [{}] [{}] [{}] [{}] [{}s]. server={}:{}, pattern={}, appkey={}, client_ip={}, device={}, token={}, user={}, params={}, body={} .",
                    CurrentUser.getNonce(), status, request.getMethod(), path, contentType, String.format("%.3f", sec),
                    serverIp, serverPort, pattern, CurrentUser.getAppkey(), clientIp, CurrentUser.getDeviceId(), CurrentUser.getToken(), userInfo, params, body);
        } else if (log.isDebugEnabled()) {
            log.debug("request [{}] [{}] [{}] [{}] [{}s]. server={}:{}, pattern={}, appkey={}, client_ip={}, device={}, token={}, user={}, header={}, params={}, body={} .",
                    CurrentUser.getNonce(), status, request.getMethod(), path, String.format("%.3f", sec),
                    serverIp, serverPort, pattern, CurrentUser.getAppkey(), clientIp, CurrentUser.getDeviceId(), CurrentUser.getToken(), userInfo, JsonUtil.serialize(headers), params, body);
        } else if (log.isInfoEnabled()) {
            log.info("request [{}] [{}] [{}] [{}] [{}] [{}s]. server={}:{}, pattern={}, appkey={}, client_ip={}, device={}, token={}, user={}, params={}, body={} .",
                    CurrentUser.getNonce(), status, request.getMethod(), path, contentType, String.format("%.3f", sec),
                    serverIp, serverPort, pattern, CurrentUser.getAppkey(), clientIp, CurrentUser.getDeviceId(), CurrentUser.getToken(), userInfo, params, body);
        }
    }

    /**
     * 记录完整的请求日志（调试时）
     */
    private void saveRequestLog(HttpServletRequest request) {
        // 1. 基本信息
        //String path  = request.getRequestURI().substring(request.getContextPath().length());
        String path    = request.getRequestURI();
        String pattern = (String)request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String clientIp = StringUtil.isNull(CurrentUser.getClientIp()) ? WebUtil.getClientIp(request) : CurrentUser.getClientIp();
        // 2 请求头
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String key = headerNames.nextElement();
            if ("user-agent".equalsIgnoreCase(key)) {
                continue;
            }
            String value = request.getHeader(key);
            headers.put(key, value);
        }
        // 3. 请求参数
        Map<String, Object> params = WebUtil.getRequestParams(request);
        // 4. 请求体（当为可复制流时记录）
        String body = null;
        String contentType = request.getContentType();
        contentType = (contentType == null) ? "" : contentType.toLowerCase();
        if (request instanceof RepeatedlyHttpServletRequestWrapper) {
            body = WebUtil.getRequestBody(request);
        } else {
            body = "Cannot read body for [" + contentType + "]";
        }
        CurrentUser.UserInfo user = CurrentUser.getInfo();
        String userInfo  = JsonUtil.serialize(user);
        logRequest.debug("request [{}] [{}] [{}] [{}]. server={}:{}, pattern={}, appkey={}, client_ip={}, device={}, token={}, user={}, header={}, params={}, body={} .",
                CurrentUser.getNonce(), request.getMethod(), path, contentType,
                serverIp, serverPort, pattern, CurrentUser.getAppkey(), clientIp, CurrentUser.getDeviceId(), CurrentUser.getToken(), userInfo, JsonUtil.serialize(headers), params, body);
    }

    /**
     * 记录完整的响应日志（调试时）
     */
    private void saveResponseLog(HttpServletRequest request, HttpServletResponse response) {
        String path    = request.getRequestURI();
        String contentType = response.getContentType();
        // 获取响应体（文件流不记录）
        String body = null;
        if (response instanceof ContentCachingResponseWrapper responseWrapper) {
//            ContentCachingResponseWrapper responseWrapper = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
            byte[] content = responseWrapper.getContentAsByteArray();
            // 此处写死UTF-8，避免乱码（response的默认是ISO-8859-1）
            Charset charset = StandardCharsets.UTF_8;
//            try {
//                charset = Charset.forName(response.getCharacterEncoding());
//            } catch (Exception e) {
//                charset = StandardCharsets.UTF_8;
//            }
            try {
                if (CompressUtil.isGzip(content)) {
                    body = new String(CompressUtil.unGzip(content), charset);
                } else {
                    body = new String(content, charset);
                }
                responseWrapper.copyBodyToResponse();
            } catch (IOException e) {
                log.warn("get response body error.", e);
                if (body == null) {
                    body = StringUtil.byte2hex(content);
                }
            }
        } else {
            body = "Cannot read body for [" + contentType + "]";
        }
        logResponse.debug("response [{}] [{}] [{}] [{}]. server={}:{}, body={} .",
                CurrentUser.getNonce(), request.getMethod(), path, contentType, serverIp, serverPort, body);
    }
}
