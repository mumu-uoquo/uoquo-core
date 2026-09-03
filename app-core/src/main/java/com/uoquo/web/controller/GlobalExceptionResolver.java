/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.controller;

import com.uoquo.utils.StringUtil;
import com.uoquo.web.exception.AbstractBaseException;
import com.uoquo.web.utils.GlobalExceptionUtil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Date;

/**
 * 描述：全局异常处理（MVC 内主处理，基于 HandlerExceptionResolver）.<br>
 * 说明：响应码为 200，方便前端统一处理
 * <pre>
 * 优点：
 *     1. 能捕获 拦截器（preHandle）、控制器、参数绑定等发生在 DispatcherServlet 内的异常.
 *     2. 异常在原始调用栈中处理，可以取到请求参数、请求体以及 CurrentUser 等上下文
 *        （请求体可读的前提是请求已被 ContentCachingWrapperFilter 包装）.
 * 缺点：无法捕获 404、过滤器（发生在 DispatcherServlet 之外）的错误.
 * 用法：作为全局异常的主处理方法，在配置类中用 @Bean 注解进行初始化注入，由 GlobalExceptionController 作兜底.
 * 注意：与 GlobalExceptionHandler（@RestControllerAdvice）属于同一处理层次，二者二选一、不能同时启用：
 *      本类的 order 为最高优先级且会无条件处理异常，即便启用 GlobalExceptionHandler 也不会被执行.
 * 资料：https://blog.csdn.net/weixin_36380516/article/details/132506064
 * </pre>
 */
public class GlobalExceptionResolver extends ExceptionHandlerExceptionResolver {
    private  final Logger log = LoggerFactory.getLogger(getClass());

    @Value("${spring.cloud.client.ip-address:${server.address:unknown}}")
    private String serverIp;

    @Value("${server.port}")
    private Integer serverPort;

    public GlobalExceptionResolver() {
        super();
        this.setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @PostConstruct
    public void init() {
        // 为了不暴露服务器IP，只留服务器IP的最后一位
        int idx = serverIp.lastIndexOf(".");
        if (StringUtil.notNull(serverIp) && (idx > -1)) {
            serverIp = serverIp.substring(idx + 1);
        }
    }

    @Override
    protected ModelAndView doResolveHandlerMethodException(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @Nullable HandlerMethod handler, @NonNull Exception error) {
        // 1. 采集上下文（请求参数、请求体、用户信息等）
        GlobalExceptionUtil.Context ctx = GlobalExceptionUtil.buildContext(request, request.getRequestURI(), new Date(), GlobalExceptionUtil.readRequestBody(request))
                .withServer(serverIp, serverPort);
        // 2. 格式化错误信息及日志记录
        AbstractBaseException ex = GlobalExceptionUtil.resolveAndLog(log, ctx, error, GlobalExceptionUtil.DetailRule.MVC);
        // 3. 补齐链路信息（traceId、堆栈前缀）
        GlobalExceptionUtil.attachTrace(ctx, ex);
        // 输出统一响应
        GlobalExceptionUtil.writeResponse(log, ctx, response, ex);
        // 4. 不能返回null，否则会找后续的resolver处理
        return new ModelAndView();
    }
}
