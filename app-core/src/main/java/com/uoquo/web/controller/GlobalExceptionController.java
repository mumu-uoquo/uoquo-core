/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.controller;

import com.uoquo.utils.StringUtil;
import com.uoquo.web.ReturnData;
import com.uoquo.web.exception.*;
import com.uoquo.web.utils.GlobalExceptionUtil;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.error.AbstractErrorController;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.jspecify.annotations.NonNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.ModelAndView;

/**
 * 描述：全局异常处理（Servlet/容器兜底层，基于 /error 错误页转发）.<br>
 * 说明：响应码为 200，方便前端统一处理
 * <pre>
 * 捕获范围：
 *     1. 404（无匹配的handler时，Spring Boot 默认不会抛出异常，只能由 ErrorController 处理）；
 *     2. 过滤器（Filter）、Servlet 容器等发生在 DispatcherServlet 之前的异常；
 *     3. 拦截器、控制器、参数绑定等异常未被 GlobalExceptionResolver 消费时的最后兜底.
 * 优点：能覆盖 MVC 异常处理（GlobalExceptionResolver）够不到的所有场景，是全局异常的最后一道防线.
 * 缺点：
 *     1. 兜底场景的请求上下文不完整：错误转发（FORWARD）不会再执行 REQUEST 型过滤器，
 *        且 /error 已被排除在拦截器之外（见 WebHttpConfig#excludePaths），
 *        因此 404 等场景下取不到 CurrentUser 等上下文，请求体的读取结果也不可靠（此处不读取请求体）.
 *     2. 除 404 外，其他异常都会触发 “ContainerBase.[Tomcat]” 的错误输出，导致日志记录双份.
 *     3. 响应已提交后再转发会失败，此类场景无法输出统一响应.
 * 结论：本类不应作为全局异常的唯一实现，只适合作为补偿（兜底）机制.
 * 用法：与 GlobalExceptionResolver 配合使用，二者是“主处理 + 兜底”的协作关系，不是二选一
 * 资料：https://blog.csdn.net/weixin_36380516/article/details/132506064
 * </pre>
 * 参考：{@link org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController}的处理<br>
 * 日期：2018-03-20 15:13 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-03-20     xuhz.           创建
 * 1.1          2026-09-03     uoquo team      修正类注释；公共逻辑抽取到 GlobalExceptionUtil
 * </pre>
 * @since   JDK 1.8
 * @version 1.1
 * @author  uoquo team
 */
@Hidden
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
@ConditionalOnWebApplication
public class GlobalExceptionController extends AbstractErrorController {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionController.class);

    private final ErrorProperties errorProperties;

    private final ErrorAttributes errorAttributes;

    @Value("${spring.cloud.client.ip-address:${server.address:unknown}}")
    private String serverIp;

    @Value("${server.port}")
    private Integer serverPort;

    @Autowired
    public GlobalExceptionController(ErrorAttributes errorAttributes, ServerProperties serverProperties) {
        super(errorAttributes, Collections.emptyList());
        this.errorProperties = serverProperties.getError();
        this.errorAttributes = errorAttributes;
    }

    @PostConstruct
    public void init() {
        // 为了不暴露服务器IP，只留服务器IP的最后一位
        int idx = serverIp.lastIndexOf(".");
        if (StringUtil.notNull(serverIp) && (idx > -1)) {
            serverIp = serverIp.substring(idx + 1);
        }
    }

    /**
     * 返回JSON错误信息.<br>
     * @param request  请求对象
     * @param response 响应对象
     */
    @RequestMapping
    @ResponseStatus(HttpStatus.OK)
    public ReturnData<String> error(HttpServletRequest request, HttpServletResponse response) {
        // 0. 获取错误信息
        Map<String, Object> body = this.getErrorAttributes(request, getErrorAttributeOptions(request, MediaType.ALL));
        Integer status = (Integer) body.get("status");
        String from = (String) body.get("path");
        String mesg = (String) body.get("message");
        Date   time = (Date) body.get("timestamp");
        // 获取真正的错误对象
        Throwable error = this.getError(request, status, mesg);
        // 1. 采集上下文（请求参数、请求体、用户信息等）
        // 兜底场景的请求上下文不完整，请求体的读取结果不可靠（详见类注释），此处不读取请求体
        GlobalExceptionUtil.Context ctx = GlobalExceptionUtil.buildContext(request, from, time, null, mesg)
                .withServer(serverIp, serverPort);
        // 2. 格式化错误信息及日志记录
        AbstractBaseException ex = GlobalExceptionUtil.resolveAndLog(log, ctx, error, GlobalExceptionUtil.DetailRule.FALLBACK);
        // 3. 补齐链路信息（traceId、堆栈前缀）
        GlobalExceptionUtil.attachTrace(ctx, ex);
        // 增加错误码到响应头，主要用于响应内容为文件流的接口
        if (response != null) {
            response.setHeader("response-code", ex.getStatus());
        }
        // 4. 返回异常内容
        return new ReturnData<>(ex);
    }

    private Throwable getError(@NonNull HttpServletRequest request, Integer status, String mesg) {
        // 1. 从当前请求对象中获取堆栈信息
        Throwable error = this.errorAttributes.getError(new ServletWebRequest(request));
        if (error == null) {
            error = getAttribute(new ServletRequestAttributes(request), "jakarta.servlet.error.exception");
            // 如果是403、404一类，将获取不到错误信息，此时根据请求头调整
            if (error == null) {
                status = (status == null) ? 500 : status;
                if (status == 403) {
                    error = new ForbiddenException();
                } else if (status == 404) {
                    error = new ResourceNotFoundException();
                } else {
                    error = new SystemErrorException(StringUtil.isNull(mesg) ? "未知错误" : mesg);
                }
            } else if (!(error instanceof AbstractBaseException)) {
                error = (error.getCause() == null) ? error : error.getCause();
            }
        }
        // 2. 如果不是基础异常，则判断最终原始原因是否是内部异常
        Throwable temp = error.getCause();
        while (temp != null) {
            if (temp instanceof AbstractBaseException) {
                return temp;
            }
            temp = temp.getCause();
        }
        return error;
    }

    @SuppressWarnings("unchecked")
    private <T> T getAttribute(RequestAttributes requestAttributes, @NonNull String name) {
        return (T) requestAttributes.getAttribute(name, RequestAttributes.SCOPE_REQUEST);
    }

    protected ErrorAttributeOptions getErrorAttributeOptions(HttpServletRequest request, MediaType mediaType) {
        ErrorAttributeOptions options = ErrorAttributeOptions.defaults();
        if (this.errorProperties.isIncludeException()) {
            options = options.including(ErrorAttributeOptions.Include.EXCEPTION);
        }
        if (isIncludeStackTrace(request, mediaType)) {
            options = options.including(ErrorAttributeOptions.Include.STACK_TRACE);
        }
        if (isIncludeMessage(request, mediaType)) {
            options = options.including(ErrorAttributeOptions.Include.MESSAGE);
        }
        if (isIncludeBindingErrors(request, mediaType)) {
            options = options.including(ErrorAttributeOptions.Include.BINDING_ERRORS);
        }
        return options;
    }

    /**
     * Determine if the stacktrace attribute should be included.
     * @param request the source request
     * @param produces the media type produced (or {@code MediaType.ALL})
     * @return if the stacktrace attribute should be included
     */
    protected boolean isIncludeStackTrace(HttpServletRequest request, MediaType produces) {
        switch (getErrorProperties().getIncludeStacktrace()) {
            case ALWAYS:
                return true;
            case ON_PARAM:
                return getTraceParameter(request);
            default:
                return false;
        }
    }

    /**
     * Determine if the message attribute should be included.
     * @param request the source request
     * @param produces the media type produced (or {@code MediaType.ALL})
     * @return if the message attribute should be included
     */
    protected boolean isIncludeMessage(HttpServletRequest request, MediaType produces) {
        switch (getErrorProperties().getIncludeMessage()) {
            case ALWAYS:
                return true;
            case ON_PARAM:
                return getMessageParameter(request);
            default:
                return false;
        }
    }

    /**
     * Determine if the errors attribute should be included.
     * @param request the source request
     * @param produces the media type produced (or {@code MediaType.ALL})
     * @return if the errors attribute should be included
     */
    protected boolean isIncludeBindingErrors(HttpServletRequest request, MediaType produces) {
        switch (getErrorProperties().getIncludeBindingErrors()) {
            case ALWAYS:
                return true;
            case ON_PARAM:
                return getErrorsParameter(request);
            default:
                return false;
        }
    }

    /**
     * Provide access to the error properties.
     * @return the error properties
     */
    protected ErrorProperties getErrorProperties() {
        return this.errorProperties;
    }

    @Override
    protected ModelAndView resolveErrorView(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                                            Map<String, Object> model) {
        return new ModelAndView();
    }

}
