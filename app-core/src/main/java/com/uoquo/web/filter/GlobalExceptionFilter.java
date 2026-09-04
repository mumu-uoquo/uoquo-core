/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.filter;

import com.uoquo.utils.StringUtil;
import com.uoquo.web.exception.AbstractBaseException;
import com.uoquo.web.utils.GlobalExceptionUtil;
import com.uoquo.web.utils.WebUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import org.jspecify.annotations.NonNull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Date;

/**
 * 描述：全局异常兜底（Filter 层）.<br>
 * 背景：发生在 DispatcherServlet 之外的异常（Filter 链抛出的异常）无法被 GlobalExceptionResolver 处理，
 *      最终会冒泡到 Servlet 容器，由容器先输出一条错误日志（ContainerBase.[Tomcat]…），
 *      再转发 /error 由 GlobalExceptionController 记录一次，形成日志双份.<br>
 * 作用：在最外层（DispatcherServlet 之外）捕获这些异常并直接输出统一响应，
 *      异常不再冒泡出 Servlet 容器，双份日志随之消失.<br>
 * 顺序：本类位于 LogbackFilter 之后（保证 MDC 中已有 requestId）、
 *      ContentCachingWrapperFilter 与 DispatcherServlet 之前（保证覆盖所有下游组件）.<br>
 * 说明：
 * <pre>
 * 1. 响应已提交（文件流、SSE 输出中途失败）时无法再输出统一响应，此时只记录日志并原样抛出，
 *    由容器日志作为最后的排查线索（因此不建议关闭容器的 ContainerBase 日志）；
 * 2. 只捕获 Exception，Error（OOM、StackOverflow 等）交还容器处理；
 * 3. 长连接请求（SSE/WebSocket）与 /error 自身不做兜底，避免二次处理与递归；
 * 4. 由本类在最外层包装请求流，保证兜底时仍能读取到请求体.
 * </pre>
 * 日期：2026-09-04 09:30 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2026-09-04     uoquo team      创建
 * </pre>
 * @since   JDK 21
 * @version 1.0
 * @author  uoquo team
 */
public class GlobalExceptionFilter extends OncePerRequestFilter implements Ordered {
    private final Logger log = LoggerFactory.getLogger(getClass());

    /** 服务器IP（脱敏：仅保留最后一段） */
    private String serverIp = "unknown";

    /** 服务器端口 */
    private Integer serverPort = 0;

    /** 统一出错处理地址（不参与兜底，避免递归） */
    private String errorPath = "/error";

    @Override
    public int getOrder() {
        // 位于 LogbackFilter（HIGHEST_PRECEDENCE）之后，其余过滤器与 DispatcherServlet 之前
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    /**
     * 设置服务器IP（内部按统一规则脱敏，只保留最后一段）.
     * @param serverIp 服务器IP
     */
    public void setServerIp(String serverIp) {
        int idx = (serverIp == null) ? -1 : serverIp.lastIndexOf(".");
        this.serverIp = (StringUtil.notNull(serverIp) && (idx > -1)) ? serverIp.substring(idx + 1) : serverIp;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    public void setErrorPath(String errorPath) {
        this.errorPath = errorPath;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        // 长连接请求（SSE/WebSocket）与 /error 自身不做兜底
        if (WebUtil.isLongLivedRequest(request) || errorPath.equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        // 最外层缓存请求流，保证兜底时仍能读取到请求体（下游的 ContentCachingWrapperFilter 会直接复用）
        HttpServletRequest wrapped = (request instanceof RepeatedlyHttpServletRequestWrapper)
                ? request
                : new RepeatedlyHttpServletRequestWrapper(request);
        try {
            chain.doFilter(wrapped, response);
        } catch (Exception e) {
            if (response.isCommitted()) {
                // 响应已写出，无法再输出统一结构，只能记录（此时容器日志是唯一线索）
                log.error("响应已提交，无法输出统一异常响应: [{}] {}", request.getMethod(), request.getRequestURI(), e);
                rethrow(e);
                return;
            }
            // 清掉响应缓冲区中可能已写入的内容，再输出统一响应
            response.resetBuffer();
            GlobalExceptionUtil.Context ctx = GlobalExceptionUtil
                    .buildContext(wrapped, wrapped.getRequestURI(), new Date(), GlobalExceptionUtil.readRequestBody(wrapped))
                    .withServer(serverIp, serverPort);
            AbstractBaseException ex = GlobalExceptionUtil.resolveAndLog(log, ctx, e, GlobalExceptionUtil.DetailRule.PRIMARY);
            GlobalExceptionUtil.attachTrace(ctx, ex);
            GlobalExceptionUtil.writeResponse(log, ctx, response, ex);
        }
    }

    /**
     * 原样抛出异常（保持异常类型，便于容器与上游组件识别）.
     */
    private void rethrow(Exception e) throws IOException, ServletException {
        if (e instanceof IOException io) {
            throw io;
        }
        if (e instanceof ServletException se) {
            throw se;
        }
        if (e instanceof RuntimeException re) {
            throw re;
        }
        throw new ServletException(e);
    }
}
