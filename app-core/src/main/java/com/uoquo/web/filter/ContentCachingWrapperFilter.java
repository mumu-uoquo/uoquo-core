/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.filter;

import java.io.IOException;
import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.uoquo.web.utils.WebUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 缓存请求体和响应体过滤器
 *
 * <p>
 * 由于 requestBody 和 responseBody 分别对应的是 InputStream 和 OutputStream，由于流的特性，读取完之后就无法再被使用了。
 * 所以，需要额外缓存一次流信息。
 * </p>
 * @author xuhz
 */
public class ContentCachingWrapperFilter extends OncePerRequestFilter implements Ordered {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public void initFilterBean() throws ServletException {
        log.debug("----------------------->ContentCachingWrapperFilter init");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        log.debug("----------------------->ContentCachingWrapperFilter do.");
        // 若是SSE/WebSocket长连接请求，则不缓存
        if (WebUtil.isLongLivedRequest(request)) {
            log.debug("当前为长连接请求（SSE/WebSocket），不缓存请求流和响应流：{}", request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }
//        // 1. 包装可复用的请求流
//        // ContentCachingRequestWrapper 会有报错 Required request body is missing
//        // 详细分析见：https://www.cnblogs.com/sfnz/p/18466992
//        ContentCachingRequestWrapper requestWrapper = null;
//        if (request instanceof ContentCachingRequestWrapper) {
//            requestWrapper = (ContentCachingRequestWrapper) request;
//        } else {
//            requestWrapper = new ContentCachingRequestWrapper(request);
//        }
        // 2. 包装可复用的响应流
        ContentCachingResponseWrapper responseWrapper = null;
        if (response instanceof ContentCachingResponseWrapper) {
            responseWrapper = (ContentCachingResponseWrapper) response;
        } else {
            responseWrapper = new ContentCachingResponseWrapper(response);
        }
        // 3. 继续请求
        String type = request.getContentType();
        type = (type == null) ? "" : type.toLowerCase();
        if (type.contains("stream")) {
            // 二进制流请求的数据不复制流对象
            // 由于spring优先处理过，此处不需要过滤文件上传的请求（type.contains("multipart/form-data")
            chain.doFilter(request, responseWrapper);
        } else {
            RepeatedlyHttpServletRequestWrapper repeatedlyRequest = new RepeatedlyHttpServletRequestWrapper(request);
            chain.doFilter(repeatedlyRequest, responseWrapper);
        }
        // 4. 更新响应（不操作这一步，会导致接口响应空白）
        Objects.requireNonNull(responseWrapper).copyBodyToResponse();
    }

}
