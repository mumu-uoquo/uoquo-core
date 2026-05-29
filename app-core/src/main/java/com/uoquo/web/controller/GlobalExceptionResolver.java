/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.controller;

import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.DateUtil;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.ReturnData;
import com.uoquo.web.exception.*;
import com.uoquo.web.utils.WebUtil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.TreeMap;

/**
 * 描述：全局异常处理（拦截器、业务异常、入参绑定）.<br>
 * 说明：响应码为 200，方便前端统一处理
 * <pre>
 * 优点：
 *     1. 能捕获 拦截器、控制器、参数绑定等错误.
 *     2. 可以获取请求时的参数
 * 缺点：无法捕获 404、过滤器的错误.
 * 用法：作为全局异常的主处理方法，在配置类中用 @Bean 注解进行初始化注入，由 GlobalExceptionController 作补偿
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
        // 1. 组装基础数据
        CurrentUser.UserInfo user = CurrentUser.getInfo();
        String from = request.getRequestURI();
        Date   time = new Date();
        String clientIp = StringUtil.isNull(CurrentUser.getClientIp()) ? WebUtil.getClientIp(request) : CurrentUser.getClientIp();
        String pattern  = (String)request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String requestNonce = CurrentUser.getNonce();
        // 第三方直接调用时userId为空，此时也应该记录相关日志
        String userInfo = JsonUtil.serialize(user);
        // 请求参数
        TreeMap<String, Object> params = WebUtil.getRequestParams(request);
        // 请求体（去掉文件）
        String reqBody = null;
        String contentType = request.getHeader("Content-Type");
        contentType = (contentType == null) ? "" : contentType.toLowerCase();
        if (!contentType.startsWith("multipart/form-data") && !contentType.startsWith("application/octet-stream")) {
            reqBody = WebUtil.getRequestBody(request);
            // INFO只记录1000字符以内的数据（便于以后查问题，此处放开）
//            if ((body.length() > 1000) && !log.isDebugEnabled()) {
//                body = body.substring(0, 1000) + "...";
//            }
        }
        // 2. 格式化错误信息及日志记录
        AbstractBaseException ex;
        if (error instanceof RemoteServiceException         // 其他服务抛出的异常
                || error instanceof SystemErrorException    // 无权操作的异常
        ) {
            // 此时需要记录异常的堆栈详情
            ex = (AbstractBaseException) error;
            log.error("request [{}] [ERROR] [{}] [{}] [0s]. server={}:{}, pattern={}, code=[{}], message={}, appkey={}, client_ip={}, device={}, token={}, user={}, params={}, body={} .\n{}",
                    requestNonce, request.getMethod(), from, serverIp, serverPort, pattern, ex.getCode(), ex.getMesg(), CurrentUser.getAppkey(),
                    clientIp, CurrentUser.getDeviceId(), CurrentUser.getToken(), userInfo, params, reqBody, ex.getTrace());
        } else if (error instanceof AbstractBaseException) {
            // 如果是自定义其他异常，不记录详细的异常堆栈信息
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
            ex = new SystemErrorException(error);
            log.error("request [{}] [ERROR] [{}] [{}] [0s]. server={}:{}, pattern={}, code=[{}], message={}, appkey={}, client_ip={}, device={}, token={}, user={}, params={}, body={} .\n{}",
                    requestNonce, request.getMethod(), from, serverIp, serverPort, pattern, ex.getCode(), ex.getMesg(), CurrentUser.getAppkey(),
                    clientIp, CurrentUser.getDeviceId(), CurrentUser.getToken(), userInfo, params, reqBody, ex.getTrace());
        }
        // 3. 响应输出
        if (log.isDebugEnabled()) {
            log.error("request[{}] error trace: ", requestNonce, ex);
        }
        try {
            // 拼装堆栈内容前缀
            ex.setTraceId(CurrentUser.getTraceId());
            String activeType = System.getProperty("spring.profiles.active");
            if (!"prod".equalsIgnoreCase(activeType)) {
                StringBuffer tracePrefix = new StringBuffer();
                tracePrefix.append("timestamp: ").append(DateUtil.toString(time, DateUtil.FORMAT_TIMESTAMP)).append("\n");
                tracePrefix.append("server: ").append(String.format("%s:%s", serverIp, serverPort)).append("\n");
                tracePrefix.append("client: ").append(clientIp).append("\n");
                tracePrefix.append("from: ").append(from).append("\n");
                tracePrefix.append("traceId: ").append(CurrentUser.getTraceId()).append("\n");
                ex.setTrace(tracePrefix.toString());
            }
            // 增加错误码到响应头，主要用于响应内容为文件流的接口
            response.setHeader("response-code", ex.getStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setStatus(HttpStatus.OK.value());
            response.getWriter().write(JsonUtil.serialize(new ReturnData<>(ex)));
        } catch (Exception e) {
            log.error("request [{}] [ERROR] [{}] [{}] [0s]. server={}:{}, pattern={}, code=[{}], message={}, appkey={}, client_ip={}, device={}, token={}, user={}, params={}, body={} .\n{}",
                    requestNonce, request.getMethod(), from, serverIp, serverPort, pattern, ex.getCode(), ex.getMesg(), CurrentUser.getAppkey(),
                    clientIp, CurrentUser.getDeviceId(), CurrentUser.getToken(), userInfo, params, reqBody, ex.getTrace(), e);
        }
        // 4. 不能返回null，否则会找后续的resolver处理
        return new ModelAndView();
    }
}
