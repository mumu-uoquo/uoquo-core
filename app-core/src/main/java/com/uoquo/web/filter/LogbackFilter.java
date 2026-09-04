/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.filter;

import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.IDGenerator;
import com.uoquo.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * 描述：添加日志MDC信息. <br>
 * 背景：便于在日志中打印请求ID，用于区分同一次请求. <br>
 * 说明：清理动作必须放在 finally 中——servlet 容器的线程是池化复用的，
 *      若请求处理抛出异常而未清理，MDC 中的 requestId 会被下一个复用该线程的请求继承（日志串号）.<br>
 * <pre>
 *   WebFilter 是 Java Servlet 规范的一部分，由 Servlet 容器（如 Tomcat）直接管理
 *   Servlet 容器不识别 Spring 的 Order 注解，因为它属于 Spring 框架
 *   建议在配置类中采用Bean注入 FilterRegistrationBean 的方式来实例化
 * </pre>
 */
public class LogbackFilter implements Filter, Ordered {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public int getOrder() {
        // 数值越小，优先级越高
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // do nothing
        log.debug("----------------------->LogbackFilter init");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        String rid = ((HttpServletRequest)request).getHeader(CurrentUser.TRACE_ID);
        if (StringUtil.isNull(rid)) {
            rid = IDGenerator.getNextULID();
        }
        MDC.put("requestId", rid);
        log.debug("----------------------->LogbackFilter do. rid={}", rid);
        try {
            chain.doFilter(request, response);
        } finally {
            // 必须清理：线程由容器池化复用，不清理会导致下一个请求沿用上一个请求的 requestId
            MDC.remove("requestId");
        }
    }
    
    @Override
    public void destroy() {
        // do nothing
    }
}
