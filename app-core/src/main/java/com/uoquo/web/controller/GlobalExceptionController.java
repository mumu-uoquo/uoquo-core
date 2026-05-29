/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.controller;

import com.uoquo.utils.DateUtil;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.utils.CurrentUser;
import com.uoquo.web.ReturnData;
import com.uoquo.web.exception.*;
import com.uoquo.web.utils.WebUtil;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;

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
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * 描述：全局异常处理（请求转发）.<br>
 * 说明：响应码为 200，方便前端统一处理
 * <pre>
 * 优点：能捕获 404、过滤器、拦截器、控制器、参数绑定等错误.
 * 缺点：
 *     1. 因为是请求转发，所以无法拿到请求的入参信息
 *     2. 除 404 外，其他异常都会触发 “ContainerBase.[Tomcat]” 的错误输出，导致日志记录双份.
 * 用法：作为全局异常处理的补偿机制，配合 GlobalExceptionResolver 或 GlobalExceptionHandler
 * 资料：https://blog.csdn.net/weixin_36380516/article/details/132506064
 * </pre>
 * 参考：{@link org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController}的处理<br>
 * 日期：2018-03-20 15:13 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-03-20     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
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
     * @param request 请求对象
     */
    @RequestMapping
    @ResponseStatus(HttpStatus.OK)
    public ReturnData<String> error(HttpServletRequest request, HttpServletResponse response) {
        // 拼装堆栈内容前缀
        CurrentUser.UserInfo user = CurrentUser.getInfo();
        Map<String, Object> body = this.getErrorAttributes(request, getErrorAttributeOptions(request, MediaType.ALL));
        Integer status = (Integer) body.get("status");
        String from = (String) body.get("path");
        String mesg = (String) body.get("message");
        Date   time = (Date) body.get("timestamp");
        String clientIp = StringUtil.isNull(CurrentUser.getClientIp()) ? WebUtil.getClientIp(request) : CurrentUser.getClientIp();
        String pattern  = (String)request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String requestNonce = CurrentUser.getNonce();
        // 第三方直接调用时userId为空，此时也应该记录相关日志
        String userInfo = JsonUtil.serialize(user);
        // 请求参数及请求体
        TreeMap<String, Object> params = WebUtil.getRequestParams(request);
        // TODO 由于是转发请求，需想办法拿到请求体数据
        String reqBody = null;

        // 获取真正的错误信息
        Throwable error = this.getError(request, status, mesg);
        AbstractBaseException ex;
        if (error instanceof RemoteServiceException    // 其他服务抛出的异常
                || error instanceof ForbiddenException // 无权操作的异常
        ) {
            // 此时需要记录异常的堆栈详情
            ex = (AbstractBaseException) error;
            log.error("request [{}] [ERROR] [{}] [{}] [0s]. server={}:{}, pattern={}, code=[{}], message={}, appkey={}, client_ip={}, device={}, token={}, user={}, params={}, body={} .\n{}",
                    requestNonce, request.getMethod(), from, serverIp, serverPort, pattern, ex.getCode(), ex.getMesg(), CurrentUser.getAppkey(),
                    clientIp, CurrentUser.getDeviceId(), CurrentUser.getToken(), userInfo, params, reqBody, ex.getTrace());
        } else if (error instanceof AbstractBaseException) {
            // 如果是自定义其他异常，不记录详细的异常信息
            ex = (AbstractBaseException) error;
            if (log.isDebugEnabled()) {
                log.error("request [{}] [ERROR] [{}] [{}] [0s]. server={}:{}, pattern={}, code=[{}], message={}, appkey={}, client_ip={}, device={}, token={}, user={}, params={}, body={} .\n{}",
                        requestNonce, request.getMethod(), from, serverIp, serverPort, pattern, ex.getCode(), ex.getMesg(), CurrentUser.getAppkey(),
                        clientIp, CurrentUser.getDeviceId(), CurrentUser.getToken(), userInfo, params, reqBody, ex.getTrace());
            } else {
                log.error("request [{}] [ERROR] [{}] [{}] [0s]. server={}:{}, pattern={}, code=[{}], message={}, appkey={}, client_ip={}, device={}, token={}, user={}, params={}, body={} .",
                        requestNonce, request.getMethod(), from, serverIp, serverPort, pattern, ex.getCode(), ex.getMesg(), CurrentUser.getAppkey(),
                        clientIp, CurrentUser.getDeviceId(), CurrentUser.getToken(), userInfo, params, reqBody);
            }
        } else if (error instanceof MethodArgumentNotValidException argsError) {
            // JSON入参校验失败（暂时只拿第一个校验出错的属性）
            //List<FieldError> listError = argsError.getBindingResult().getFieldErrors();
            FieldError fieldError = argsError.getBindingResult().getFieldError();
            if (fieldError == null) {
                ex = new ParamErrorException(argsError);
            } else {
                ex = new ParamErrorException(String.format("[%s]%s", fieldError.getField(), fieldError.getDefaultMessage()));
            }
            log.error("request [{}] [ERROR] [{}] [{}] [0s]. server={}:{}, pattern={}, code=[{}], message={}, appkey={}, client_ip={}, device={}, token={}, user={}, params={}, body={} .",
                    requestNonce, request.getMethod(), from, serverIp, serverPort, pattern, ex.getCode(), ex.getMesg(), CurrentUser.getAppkey(),
                    clientIp, CurrentUser.getDeviceId(), CurrentUser.getToken(), userInfo, params, reqBody, error);
        } else if (error instanceof ConstraintViolationException) {
            // FORM入参校验失败
            // TODO 后续是否可以拿到具体的字段
            ex = new ParamErrorException(error);
            log.error("request [{}] [ERROR] [{}] [{}] [0s]. server={}:{}, pattern={}, code=[{}], message={}, appkey={}, client_ip={}, device={}, token={}, user={}, params={}, body={} .",
                    requestNonce, request.getMethod(), from, serverIp, serverPort, pattern, ex.getCode(), ex.getMesg(), CurrentUser.getAppkey(),
                    clientIp, CurrentUser.getDeviceId(), CurrentUser.getToken(), userInfo, params, reqBody, error);
        } else {
            // 如果是其他异常，转换为自定义异常，并记录详细堆栈信息
            if (StringUtil.isNull(mesg)) {
                mesg = error.getMessage();
            }
            ex = new SystemErrorException(mesg, error);
            log.error("request [{}] [ERROR] [{}] [{}] [0s]. server={}:{}, pattern={}, code=[{}], message={}, appkey={}, client_ip={}, device={}, token={}, user={}, params={}, body={} .\n{}",
                    requestNonce, request.getMethod(), from, serverIp, serverPort, pattern, ex.getCode(), ex.getMesg(), CurrentUser.getAppkey(),
                    clientIp, CurrentUser.getDeviceId(), CurrentUser.getToken(), userInfo, params, reqBody, ex.getTrace());
        }
        // 3. 响应输出
        if (log.isDebugEnabled()) {
            log.error("request[{}] error trace: ", requestNonce, ex);
        }
        // 拼装堆栈内容前缀
        ex.setTraceId(CurrentUser.getTraceId());
        String activeType = System.getProperty("spring.profiles.active");
        if (!"prod".equalsIgnoreCase(activeType)) {
            StringBuilder tracePrefix = new StringBuilder();
            tracePrefix.append("timestamp: ").append(DateUtil.toString(time, DateUtil.FORMAT_TIMESTAMP)).append("\n");
            tracePrefix.append("server: ").append(String.format("%s:%s", serverIp, serverPort)).append("\n");
            tracePrefix.append("client: ").append(clientIp).append("\n");
            tracePrefix.append("from: ").append(from).append("\n");
            tracePrefix.append("traceId: ").append(CurrentUser.getTraceId()).append("\n");
            ex.setTrace(tracePrefix.toString());
        }
        // 增加错误码到响应头，主要用于响应内容为文件流的接口
        response.setHeader("response-code", ex.getStatus());
        return  new ReturnData<>(ex);
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
