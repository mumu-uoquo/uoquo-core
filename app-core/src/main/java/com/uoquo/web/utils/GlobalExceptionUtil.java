/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.utils;

import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.DateUtil;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.ReturnData;
import com.uoquo.web.exception.AbstractBaseException;
import com.uoquo.web.exception.ForbiddenException;
import com.uoquo.web.exception.ParamErrorException;
import com.uoquo.web.exception.RemoteServiceException;
import com.uoquo.web.exception.SystemErrorException;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.HandlerMapping;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.TreeMap;

/**
 * 描述：全局异常处理的公共逻辑（上下文采集、异常规整、日志记录、响应输出）.<br>
 * 背景：GlobalExceptionController（/error 兜底）、GlobalExceptionResolver（MVC 内主处理）、
 * GlobalExceptionHandler（备用实现）三处存在大量相同的处理逻辑，统一收敛到本类，避免多处维护.<br>
 * 说明：本类只提供静态方法，不参与 Spring 的 Bean 生命周期.<br>
 * 日期：2026-09-03 10:00 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2026-09-03     uoquo team      创建（从三个全局异常处理类中抽取）
 * </pre>
 * @since   JDK 21
 * @version 1.0
 * @author  uoquo team
 */
public final class GlobalExceptionUtil {

    /**
     * 异常日志模板（记录堆栈详情）：末尾的占位符用于输出异常的堆栈信息.
     */
    private static final String LOG_DETAIL = "request [{}] [ERROR] [{}] [{}] [0s]. server={}:{}, pattern={}, code=[{}], message={}, appkey={}, client_ip={}, device={}, token={}, user={}, params={}, body={} .\n{}";

    /**
     * 异常日志模板（不记录堆栈详情）.
     */
    private static final String LOG_SIMPLE = "request [{}] [ERROR] [{}] [{}] [0s]. server={}:{}, pattern={}, code=[{}], message={}, appkey={}, client_ip={}, device={}, token={}, user={}, params={}, body={} .";

    private GlobalExceptionUtil() {
    }

    /**
     * 异常堆栈详情的记录规则.<br>
     * 说明：远程服务异常（RemoteServiceException）始终记录详情；系统异常与无权操作异常是否记录详情，
     * 取决于处理场景（两个主处理类的历史策略不一致，此处按场景区分，保持原有行为）.
     */
    public enum DetailRule {
        /**
         * MVC 内主处理（GlobalExceptionResolver、GlobalExceptionHandler）：<br>
         * RemoteServiceException、SystemErrorException 记录堆栈详情.
         */
        MVC(true, false),
        /**
         * /error 兜底处理（GlobalExceptionController）：<br>
         * RemoteServiceException、ForbiddenException 记录堆栈详情.
         */
        FALLBACK(false, true);

        /** 系统异常是否记录堆栈详情 */
        private final boolean includeSystemError;
        /** 无权操作异常是否记录堆栈详情 */
        private final boolean includeForbidden;

        DetailRule(boolean includeSystemError, boolean includeForbidden) {
            this.includeSystemError = includeSystemError;
            this.includeForbidden   = includeForbidden;
        }
    }

    /**
     * 异常处理的上下文信息（请求、用户、参数、请求体等）.
     */
    public static final class Context {
        private final HttpServletRequest request;
        /** 请求来源（原路径） */
        private final String from;
        /** 异常发生时间（兜底场景取自 ErrorAttributes） */
        private final Date time;
        /** 请求体（可能为null：未读取或不支持读取时为null） */
        private final String reqBody;
        /** 兜底场景中 ErrorAttributes 提供的错误信息（可能为null） */
        private final String errorMessage;
        /** 客户端IP */
        private final String clientIp;
        /** 请求匹配的URL模板 */
        private final String pattern;
        /** 请求随机数（用于串联同一次请求的日志） */
        private final String requestNonce;
        /** 用户信息（第三方直接调用时可能为空） */
        private final String userInfo;
        /** 请求参数（URL参数、Form表单参数） */
        private final TreeMap<String, Object> params;
        /** 服务器IP（脱敏后） */
        private String serverIp;
        /** 服务器端口 */
        private Integer serverPort;

        private Context(@NonNull HttpServletRequest request, String from, Date time, String reqBody, String errorMessage) {
            CurrentUser.UserInfo user = CurrentUser.getInfo();
            this.request      = request;
            this.from         = from;
            this.time         = time;
            this.reqBody      = reqBody;
            this.errorMessage = errorMessage;
            this.clientIp     = StringUtil.isNull(CurrentUser.getClientIp()) ? WebUtil.getClientIp(request) : CurrentUser.getClientIp();
            this.pattern      = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            this.requestNonce = CurrentUser.getNonce();
            // 第三方直接调用时userId为空，此时也应该记录相关日志
            this.userInfo     = JsonUtil.serialize(user);
            // 请求参数（URL参数、Form表单参数）
            this.params       = WebUtil.getRequestParams(request);
        }

        /**
         * 设置服务器地址（用于日志及堆栈前缀）.
         * @param serverIp   服务器IP（脱敏后）
         * @param serverPort 服务器端口
         */
        public Context withServer(String serverIp, Integer serverPort) {
            this.serverIp   = serverIp;
            this.serverPort = serverPort;
            return this;
        }

        public HttpServletRequest getRequest() {
            return request;
        }

    }

    /**
     * 采集异常处理的上下文信息.<br>
     * @param request 请求对象
     * @param from    请求来源（MVC 内为 requestURI，兜底场景为 ErrorAttributes 中的 path）
     * @param time    异常发生时间
     * @param reqBody 请求体（不读取时传null）
     */
    public static Context buildContext(@NonNull HttpServletRequest request, String from, Date time, @Nullable String reqBody) {
        return buildContext(request, from, time, reqBody, null);
    }

    /**
     * 采集异常处理的上下文信息.<br>
     * @param request      请求对象
     * @param from         请求来源
     * @param time         异常发生时间
     * @param reqBody      请求体（不读取时传null）
     * @param errorMessage 兜底场景中 ErrorAttributes 提供的错误信息（可为null）
     */
    public static Context buildContext(@NonNull HttpServletRequest request, String from, Date time, @Nullable String reqBody, @Nullable String errorMessage) {
        return new Context(request, from, time, reqBody, errorMessage);
    }

    /**
     * 读取请求体（去掉文件）.<br>
     * 说明：请求体可读的前提是请求已被 ContentCachingWrapperFilter 包装，否则读取后将无法再次消费.
     * @param request 请求对象
     * @return 请求体内容，文件上传等场景返回null
     */
    public static @Nullable String readRequestBody(@NonNull HttpServletRequest request) {
        String contentType = request.getHeader("Content-Type");
        contentType = (contentType == null) ? "" : contentType.toLowerCase();
        if (contentType.startsWith("multipart/form-data") || contentType.startsWith("application/octet-stream")) {
            return null;
        }
        return WebUtil.getRequestBody(request);
    }

    /**
     * 将原始异常规整为统一的 {@link AbstractBaseException}，并按类型记录日志.<br>
     * @param log   日志记录器（使用调用方的日志，便于按类定位）
     * @param ctx   异常处理的上下文
     * @param error 原始异常
     * @param rule  堆栈详情的记录规则
     * @return 规整后的基础异常
     */
    public static AbstractBaseException resolveAndLog(@NonNull Logger log, @NonNull Context ctx, @NonNull Throwable error, @NonNull DetailRule rule) {
        AbstractBaseException ex;
        if (error instanceof RemoteServiceException                                        // 其他服务抛出的异常
                || (rule.includeSystemError && error instanceof SystemErrorException)      // 系统异常
                || (rule.includeForbidden   && error instanceof ForbiddenException)        // 无权操作的异常
        ) {
            // 此时需要记录异常的堆栈详情
            ex = (AbstractBaseException) error;
            logDetail(log, ctx, ex);
        } else if (error instanceof AbstractBaseException) {
            // 如果是自定义其他异常，不记录详细的异常信息
            ex = (AbstractBaseException) error;
            if (log.isDebugEnabled()) {
                logDetail(log, ctx, ex);
            } else {
                logSimple(log, ctx, ex);
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
            logSimple(log, ctx, ex, error);
        } else if (error instanceof ConstraintViolationException) {
            // FORM入参校验失败
            // TODO 后续是否可以拿到具体的字段
            ex = new ParamErrorException(error);
            logSimple(log, ctx, ex, error);
        } else {
            // 如果是其他异常，转换为自定义异常，并记录详细堆栈信息
            String mesg = ctx.errorMessage;
            if (StringUtil.isNull(mesg)) {
                mesg = error.getMessage();
            }
            // 注意：必须保留原始异常作为 cause，否则堆栈中会丢失真正的出错位置
            ex = new SystemErrorException(error, mesg);
            logDetail(log, ctx, ex);
        }
        if (log.isDebugEnabled()) {
            log.error("request[{}] error trace: ", ctx.requestNonce, ex);
        }
        return ex;
    }

    /**
     * 补齐异常的链路信息（traceId）及堆栈前缀.<br>
     * 说明：404 时走不到拦截器，但会经过过滤器，此时 CurrentUser.getTraceId() 会为空，
     * 所以取 LogbackFilter 放入 MDC 的 requestId.
     * @param ctx 异常处理的上下文
     * @param ex  规整后的异常
     */
    public static void attachTrace(@NonNull Context ctx, @NonNull AbstractBaseException ex) {
        String traceId = StringUtil.isNull(CurrentUser.getTraceId()) ? MDC.get("requestId") : CurrentUser.getTraceId();
        ex.setTraceId(traceId);
        // 生产环境不返回堆栈信息，防止泄露信息
        String activeType = System.getProperty("spring.profiles.active");
        if (!"prod".equalsIgnoreCase(activeType)) {
            StringBuilder tracePrefix = new StringBuilder();
            tracePrefix.append("timestamp: ").append(DateUtil.toString(ctx.time, DateUtil.FORMAT_TIMESTAMP)).append("\n");
            tracePrefix.append("server: ").append(String.format("%s:%s", ctx.serverIp, ctx.serverPort)).append("\n");
            tracePrefix.append("client: ").append(ctx.clientIp).append("\n");
            tracePrefix.append("from: ").append(ctx.from).append("\n");
            tracePrefix.append("traceId: ").append(traceId).append("\n");
            ex.setTrace(tracePrefix.toString());
        }
    }

    /**
     * 输出统一的异常响应（响应码固定为200，业务错误码放在响应头 response-code 中）.<br>
     * @param log      日志记录器
     * @param ctx      异常处理的上下文
     * @param response 响应对象
     * @param ex       规整后的异常
     */
    public static void writeResponse(@NonNull Logger log, @NonNull Context ctx, @NonNull HttpServletResponse response, @NonNull AbstractBaseException ex) {
        try {
            // 增加错误码到响应头，主要用于响应内容为文件流的接口
            response.setHeader("response-code", ex.getStatus());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setStatus(HttpStatus.OK.value());
            response.getWriter().write(JsonUtil.serialize(new ReturnData<Object>(ex)));
        } catch (Exception e) {
            logDetail(log, ctx, ex, e);
        }
    }

    /* ******************* 日志输出 ******************* */

    private static void logDetail(Logger log, Context ctx, AbstractBaseException ex) {
        log.error(LOG_DETAIL, ctx.requestNonce, ctx.request.getMethod(), ctx.from, ctx.serverIp, ctx.serverPort,
                ctx.pattern, ex.getCode(), ex.getMesg(), CurrentUser.getAppkey(), ctx.clientIp,
                CurrentUser.getDeviceId(), CurrentUser.getToken(), ctx.userInfo, ctx.params, ctx.reqBody, ex.getTrace());
    }

    private static void logDetail(Logger log, Context ctx, AbstractBaseException ex, Throwable cause) {
        log.error(LOG_DETAIL, ctx.requestNonce, ctx.request.getMethod(), ctx.from, ctx.serverIp, ctx.serverPort,
                ctx.pattern, ex.getCode(), ex.getMesg(), CurrentUser.getAppkey(), ctx.clientIp,
                CurrentUser.getDeviceId(), CurrentUser.getToken(), ctx.userInfo, ctx.params, ctx.reqBody, ex.getTrace(), cause);
    }

    private static void logSimple(Logger log, Context ctx, AbstractBaseException ex) {
        log.error(LOG_SIMPLE, ctx.requestNonce, ctx.request.getMethod(), ctx.from, ctx.serverIp, ctx.serverPort,
                ctx.pattern, ex.getCode(), ex.getMesg(), CurrentUser.getAppkey(), ctx.clientIp,
                CurrentUser.getDeviceId(), CurrentUser.getToken(), ctx.userInfo, ctx.params, ctx.reqBody);
    }

    private static void logSimple(Logger log, Context ctx, AbstractBaseException ex, Throwable cause) {
        // 模板末尾没有占位符，最后一个 Throwable 参数由 slf4j 作为异常输出（打印堆栈）
        log.error(LOG_SIMPLE, ctx.requestNonce, ctx.request.getMethod(), ctx.from, ctx.serverIp, ctx.serverPort,
                ctx.pattern, ex.getCode(), ex.getMesg(), CurrentUser.getAppkey(), ctx.clientIp,
                CurrentUser.getDeviceId(), CurrentUser.getToken(), ctx.userInfo, ctx.params, ctx.reqBody, cause);
    }
}
