/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.controller;

import com.uoquo.utils.StringUtil;
import com.uoquo.web.ReturnData;
import com.uoquo.web.exception.AbstractBaseException;
import com.uoquo.web.exception.SystemErrorException;
import com.uoquo.web.utils.GlobalExceptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Date;

/**
 * 描述：全局异常处理（MVC 内主处理，基于 @RestControllerAdvice）.<br>
 * 说明：响应码为 200，方便前端统一处理
 * <pre>
 * 优点：
 *     1. 能捕获 控制器、参数绑定、以及已匹配 handler 后的拦截器异常.
 *     2. 异常在原始调用栈中处理，可以取到请求参数、请求体以及 CurrentUser 等上下文.
 * 缺点：无法捕获 404（默认不抛异常，而是由容器转发 /error）、
 *       过滤器（发生在 DispatcherServlet 之外）的错误.
 * 当前状态：本类为备用实现，@RestControllerAdvice 已被注释，不参与运行.
 * 注意：本类与 GlobalExceptionResolver 属于同一处理层次（都作用于 DispatcherServlet 的异常解析器），
 *      二者二选一、不能同时启用——GlobalExceptionResolver 的 order 为最高优先级且会无条件处理异常，
 *      即便启用本类也不会被执行；如需改用本类作为主处理，
 *      必须同时移除 WebHttpConfig#globalExceptionResolver 的定义.
 * 用法：与 GlobalExceptionResolver 二选一，由 GlobalExceptionController 作兜底
 * 资料：https://blog.csdn.net/weixin_36380516/article/details/132506064
 * </pre>
 */
//@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private  final Logger log = LoggerFactory.getLogger(getClass());

    @Value("${spring.cloud.client.ip-address:${server.address:unknown}}")
    private String serverIp;

    @Value("${server.port}")
    private Integer serverPort;

    @PostConstruct
    public void init() {
        // 为了不暴露服务器IP，只留服务器IP的最后一位
        int idx = serverIp.lastIndexOf(".");
        if (StringUtil.notNull(serverIp) && (idx > -1)) {
            serverIp = serverIp.substring(idx + 1);
        }
    }

    /**
     * 处理所有类型的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ReturnData<?> handleAllExceptions(Exception error, HttpServletRequest request, HttpServletResponse response) {
        AbstractBaseException ex = this.getErrorException(error, request, response);
        return new ReturnData<>(ex);
    }

    /**
     * 处理方法参数验证失败的异常
     */
    @Override
    protected @NonNull ResponseEntity<Object> handleExceptionInternal(@NonNull Exception error, @Nullable Object body, @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest webRequest) {
        // 1. 获取请求对象
        HttpServletRequest request = null;
        HttpServletResponse response = null;
        if (webRequest instanceof ServletWebRequest servletWebRequest) {
            request  = servletWebRequest.getRequest();
            response = servletWebRequest.getResponse();
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (request == null && attributes != null) {
            request  = attributes.getRequest();
            response = attributes.getResponse();
        }
        // 2. 格式化异常信息
        AbstractBaseException ex;
        if (request != null) {
            ex = this.getErrorException(error, request, response);
        } else {
            ex = new SystemErrorException(error);
        }
        // 增加错误码到响应头，主要用于响应内容为文件流的接口
        if (response != null) {
            response.setHeader("response-code", ex.getStatus());
        }
        // 3. 返回异常内容
        return new ResponseEntity<>(new ReturnData<>(ex), HttpStatus.OK);
    }

    /**
     * 格式化异常信息
     */
    private AbstractBaseException getErrorException(Exception error, HttpServletRequest request, HttpServletResponse response) {
        // 1. 采集上下文（请求参数、请求体、用户信息等）
        GlobalExceptionUtil.Context ctx = GlobalExceptionUtil.buildContext(request, request.getRequestURI(), new Date(), GlobalExceptionUtil.readRequestBody(request))
                .withServer(serverIp, serverPort);
        // 2. 格式化错误信息及日志记录
        AbstractBaseException ex = GlobalExceptionUtil.resolveAndLog(log, ctx, error, GlobalExceptionUtil.DetailRule.MVC);
        // 3. 补齐链路信息（traceId、堆栈前缀）
        GlobalExceptionUtil.attachTrace(ctx, ex);
        return ex;
    }

}
