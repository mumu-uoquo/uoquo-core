/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.http;

import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okio.BufferedSink;

/**
 * HTTP 请求执行器，处理请求构建、执行和响应处理
 *
 * <p>将 HttpUtil 中的请求执行逻辑提取出来，提供统一的请求处理，包含完整的日志功能</p>
 *
 * @author uoquo team
 */
public class HttpRequestExecutor {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestExecutor.class);
    
    private static final MediaType MEDIA_JSON = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType MEDIA_STREAM = MediaType.parse("application/octet-stream");
    
    // ====================================================================
    // 执行方法（带日志）
    // ====================================================================
    
    /**
     * 执行 HTTP 请求
     */
    public static Response execute(Request.Builder requestBuilder, HttpHeaders headers, HttpCookies cookies) 
            throws IOException {
        return execute(requestBuilder, headers, cookies, null);
    }
    
    /**
     * 执行 HTTP 请求（支持回调）
     */
    public static Response execute(Request.Builder requestBuilder, HttpHeaders headers, 
                                 HttpCookies cookies, Callback callback) throws IOException {
        // 添加请求头
        addHeaders(requestBuilder, headers);
        
        // 构建请求
        Request request = requestBuilder.build();
        
        // 记录请求日志
        logRequest(request);
        
        // 获取客户端
        OkHttpClient client = getClient(cookies);
        
        try {
            Call call = client.newCall(request);
            if (callback != null) {
                call.enqueue(callback);
                return null;
            } else {
                Response response = call.execute();
                
                // 记录响应日志
                logResponse(response, false);
                
                return response;
            }
        } catch (Exception e) {
            // 记录请求错误日志
            logRequestError(request, e);
            throw e;
        }
    }
    
    /**
     * 执行 HTTP 请求并返回字符串响应（带完整日志）
     */
    public static String executeForString(Request.Builder requestBuilder, HttpHeaders headers, 
                                         HttpCookies cookies) throws IOException {
        Response response = execute(requestBuilder, headers, cookies);
        return parseResponseToString(response, false);
    }
    
    /**
     * 执行下载请求（不记录响应体）
     */
    public static Response executeForDownload(Request.Builder requestBuilder, HttpHeaders headers, 
                                            HttpCookies cookies) throws IOException {
        // 添加请求头
        addHeaders(requestBuilder, headers);
        
        // 构建请求
        Request request = requestBuilder.build();
        
        // 记录请求日志
        logRequest(request);
        
        // 获取客户端
        OkHttpClient client = getClient(cookies);
        
        try {
            Response response = client.newCall(request).execute();
            
            // 记录下载响应日志（只记录响应头）
            logResponse(response, true);
            
            return response;
        } catch (Exception e) {
            // 记录请求错误日志
            logRequestError(request, e);
            throw e;
        }
    }
    
    // ====================================================================
    // 请求构建方法
    // ====================================================================
    
    /**
     * 构建 GET 请求
     */
    public static Request.Builder buildGetRequest(String url, HttpParams params) {
        StringBuilder urlBuilder = new StringBuilder(url);
        
        if ((params != null) && !params.emptyFormParam()) {
            urlBuilder.append(url.contains("?") ? "&" : "?");
            urlBuilder.append(params.getURLEncodedParams());
        }
        
        return new Request.Builder()
                .get()
                .url(urlBuilder.toString());
    }
    
    /**
     * 构建 POST 表单请求
     */
    public static Request.Builder buildPostRequest(String url, HttpParams params) {
        RequestBody body = FormBody.create(new byte[0], null);

        if ((params != null) && !params.emptyFormParam()) {
            FormBody.Builder formBody = new FormBody.Builder();
            Map<String, String> formParams = params.getFormParams();
            for (Map.Entry<String, String> entry : formParams.entrySet()) {
                formBody.add(entry.getKey(), entry.getValue());
            }
            body = formBody.build();
        }
        
        return new Request.Builder()
                .post(body)
                .url(url);
    }
    
    /**
     * 构建 JSON 请求
     */
    public static Request.Builder buildJsonRequest(String url, Object data) {
        String jsonStr = buildJsonString(data);
        RequestBody body = StringUtil.isNull(jsonStr) ? 
            createJsonBody(new byte[0]) : 
            createJsonBody(jsonStr.getBytes(StandardCharsets.UTF_8));
        
        return new Request.Builder()
                .post(body)
                .url(url);
    }
    
    /**
     * 构建文件上传请求
     */
    public static Request.Builder buildUploadRequest(String url, HttpParams params) {
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);
        
        if (params != null) {
            // 添加文件参数
            Map<String, java.util.List<java.io.File>> fileParams = params.getFileParams();
            for (Map.Entry<String, java.util.List<java.io.File>> entry : fileParams.entrySet()) {
                for (java.io.File file : entry.getValue()) {
                    bodyBuilder.addFormDataPart(
                        entry.getKey(), 
                        file.getName(), 
                        RequestBody.create(
                            MediaType.parse(guessMimeType(file.getName())), 
                            file
                        )
                    );
                }
            }
            
            // 添加表单参数
            Map<String, String> formParams = params.getFormParams();
            for (Map.Entry<String, String> entry : formParams.entrySet()) {
                bodyBuilder.addFormDataPart(entry.getKey(), entry.getValue());
            }
        }
        
        return new Request.Builder()
                .post(bodyBuilder.build())
                .url(url);
    }
    
    /**
     * 构建通用 POST 请求（支持自定义请求体和媒体类型）
     * 适用于 SOAP、XML、自定义格式等
     * 
     * @param url 请求地址
     * @param bodyContent 请求体内容（字符串或字节数组）
     * @param mediaType 媒体类型，如 "text/xml; charset=utf-8"、"application/soap+xml" 等
     * @return Request.Builder 请求构建器
     */
    public static Request.Builder buildGenericPostRequest(String url, Object bodyContent, MediaType mediaType) {
        RequestBody body;
        
        if (bodyContent == null) {
            body = RequestBody.create(new byte[0], mediaType);
        } else if (bodyContent instanceof String) {
            String content = (String) bodyContent;
            body = RequestBody.create(content, mediaType);
        } else if (bodyContent instanceof byte[]) {
            byte[] content = (byte[]) bodyContent;
            body = RequestBody.create(content, mediaType);
        } else {
            // 其他类型转换为字符串
            body = RequestBody.create(bodyContent.toString(), mediaType);
        }
        
        return new Request.Builder()
                .post(body)
                .url(url);
    }
    
    /**
     * 构建通用 POST 请求（使用字符串内容和自定义媒体类型）
     * 
     * @param url 请求地址
     * @param bodyContent 请求体内容字符串
     * @param mediaType 媒体类型字符串，如 "text/xml; charset=utf-8"
     * @return Request.Builder 请求构建器
     */
    public static Request.Builder buildGenericPostRequest(String url, String bodyContent, String mediaType) {
        return buildGenericPostRequest(url, bodyContent, MediaType.parse(mediaType));
    }
    
    // ====================================================================
    // 响应处理方法
    // ====================================================================
    
    /**
     * 解析响应为字符串（带日志）
     */
    public static String parseResponseToString(Response response, boolean isDownload) throws IOException {
        if (response == null) {
            return null;
        }
        
        try (response) {
            ResponseBody body = response.body();
            
            if (!isDownload) {
                // 记录响应体日志（非下载请求）
                if (log.isInfoEnabled() && body != null) {
                    String content = body.string();
                    if (StringUtil.notNull(content)) {
                        String truncatedBody = content;
                        if (truncatedBody.length() > 2000) {
                            truncatedBody = truncatedBody.substring(0, 2000) + "...";
                        }
                        log.info("HTTP Response Body: {}", truncatedBody);
                        return content;
                    }
                } else if (body != null) {
                    return body.string();
                }
            } else {
                // 下载请求，直接读取内容
                if (body != null) {
                    return body.string();
                }
            }
            
            if (response.code() == 200) {
                return null;
            }
            throw new IOException(String.valueOf(response.code()));
        }
    }
    
    // ====================================================================
    // 日志方法（从HttpUtil迁移过来）
    // ====================================================================
    
    /**
     * 记录请求日志
     */
    private static void logRequest(Request request) {
        if (!log.isInfoEnabled()) {
            return;
        }
        
        try {
            Map<String, List<String>> headerMap = request.headers().toMultimap();
            String uri = truncateString(request.url().toString(), 500);
            RequestBody body = request.body();
            
            if (body == null) {
                log.info("HTTP Request: uri={}, method={}, headers={}", 
                        uri, request.method(), headerMap);
            } else if (isFileUpload(body)) {
                log.info("HTTP Request: uri={}, method={}, headers={}, body=[multipart/form-data file upload]", 
                        uri, request.method(), headerMap);
            } else if (isStreamUpload(body)) {
                log.info("HTTP Request: uri={}, method={}, headers={}, body=[stream upload]", 
                        uri, request.method(), headerMap);
            } else if (body instanceof FormBody formBody) {
                List<Map<String, String>> params = extractFormParams(formBody);
                log.info("HTTP Request: uri={}, method={}, headers={}, params={}", 
                        uri, request.method(), headerMap, params);
            } else if (Objects.equals(body.contentType(), MEDIA_JSON)) {
                String bodyStr = truncateString(body.toString(), 1000);
                log.info("HTTP Request: uri={}, method={}, headers={}, body={}", 
                        uri, request.method(), headerMap, bodyStr);
            } else if (body.contentLength() == 0) {
                log.info("HTTP Request: uri={}, method={}, headers={}, body=[empty]", 
                        uri, request.method(), headerMap);
            } else {
                log.info("HTTP Request: uri={}, method={}, headers={}, body=[type: {}]", 
                        uri, request.method(), headerMap, body.contentType());
            }
        } catch (Exception e) {
            // 记录日志时发生异常不影响主流程
            log.debug("Failed to log request: {}", e.getMessage());
        }
    }
    
    /**
     * 记录响应日志
     */
    private static void logResponse(Response response, boolean isDownload) {
        if (!log.isInfoEnabled()) {
            return;
        }
        
        try {
            Map<String, List<String>> headerMap = response.headers().toMultimap();
            int statusCode = response.code();
            String message = response.message();
            
            if (isDownload) {
                long contentLength = response.body() != null ? response.body().contentLength() : 0;
                log.info("HTTP Download Response: status={}, message={}, content-length={}, headers={}, body=[download stream]", 
                        statusCode, message, contentLength, headerMap);
            } else {
                log.info("HTTP Response: status={}, message={}, headers={}", 
                        statusCode, message, headerMap);
            }
        } catch (Exception e) {
            // 记录日志时发生异常不影响主流程
            log.debug("Failed to log response: {}", e.getMessage());
        }
    }
    
    /**
     * 记录请求错误日志
     */
    private static void logRequestError(Request request, Exception e) {
        if (log.isWarnEnabled()) {
            Map<String, List<String>> headerMap = request.headers().toMultimap();
            String uri = truncateString(request.url().toString(), 500);
            RequestBody body = request.body();
            
            if (body == null) {
                log.warn("HTTP Request Error: uri={}, method={}, headers={}", 
                        uri, request.method(), headerMap, e);
            } else if (body instanceof FormBody formBody) {
                List<Map<String, String>> params = extractFormParams(formBody);
                log.warn("HTTP Request Error: uri={}, method={}, headers={}, params={}", 
                        uri, request.method(), headerMap, params, e);
            } else if (Objects.equals(body.contentType(), MEDIA_JSON)) {
                log.warn("HTTP Request Error: uri={}, method={}, headers={}, body={}", 
                        uri, request.method(), headerMap, body.toString(), e);
            } else {
                log.warn("HTTP Request Error: uri={}, method={}, headers={}", 
                        uri, request.method(), headerMap, e);
            }
        }
    }
    
    // ====================================================================
    // 辅助方法
    // ====================================================================
    
    /**
     * 添加请求头
     */
    private static void addHeaders(Request.Builder builder, HttpHeaders headers) {
        if (headers != null && !headers.empty()) {
            try {
                for (Map.Entry<String, String> entry : headers.get().entrySet()) {
                    builder.addHeader(entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                // 忽略添加请求头异常
            }
        }
    }
    
    /**
     * 获取 HTTP 客户端
     */
    private static OkHttpClient getClient(HttpCookies cookies) {
        if (cookies == null) {
            return HttpClientBuilder.buildPooled();
        } else {
            return HttpClientBuilder.buildWithCookies(cookies);
        }
    }
    
    /**
     * 构建 JSON 字符串
     */
    private static String buildJsonString(Object json) {
        if (json == null) {
            return "";
        }
        
        if (json instanceof HttpParams params) {
            return params.emptyFormParam() ? "" : JsonUtil.serialize(params.getJsonParams()) ;
        } else if (json instanceof String) {
            return (String) json;
        } else {
            return JsonUtil.serialize(json);
        }
    }
    
    /**
     * 创建 JSON 请求体
     */
    private static RequestBody createJsonBody(final byte[] content) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return MEDIA_JSON;
            }

            @Override
            public long contentLength() {
                return content.length;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.write(content, 0, content.length);
            }
            
            @Override
            public String toString() {
                return new String(content, StandardCharsets.UTF_8);
            }
        };
    }
    
    /**
     * 猜测 MIME 类型
     */
    private static String guessMimeType(String fileName) {
        java.net.FileNameMap fileNameMap = java.net.URLConnection.getFileNameMap();
        String contentType = fileNameMap.getContentTypeFor(fileName);
        return contentType != null ? contentType : "application/octet-stream";
    }
    
    /**
     * 判断是否为文件上传
     */
    private static boolean isFileUpload(RequestBody body) {
        if (body == null || body.contentType() == null) {
            return false;
        }
        String contentType = body.contentType().toString();
        return contentType.startsWith("multipart/form-data");
    }
    
    /**
     * 判断是否为流式上传
     */
    private static boolean isStreamUpload(RequestBody body) {
        if (body == null || body.contentType() == null) {
            return false;
        }
        return MEDIA_STREAM.toString().equals(body.contentType().toString());
    }
    
    /**
     * 提取表单参数
     */
    private static List<Map<String, String>> extractFormParams(FormBody formBody) {
        List<Map<String, String>> params = new ArrayList<>();
        for (int i = 0; i < formBody.size(); i++) {
            Map<String, String> map = new HashMap<>();
            String val = truncateString(formBody.value(i), 300);
            map.put(formBody.name(i), val);
            params.add(map);
        }
        return params;
    }
    
    /**
     * 截断字符串
     */
    private static String truncateString(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }
}