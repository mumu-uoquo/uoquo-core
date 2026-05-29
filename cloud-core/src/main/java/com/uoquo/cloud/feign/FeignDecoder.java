/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.feign;

import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.utils.json.TypeToken;
import com.uoquo.web.ReturnData;
import com.uoquo.web.SystemReturnCode;
import com.uoquo.web.exception.AbstractBaseException;
import com.uoquo.web.exception.RemoteServiceException;

import feign.FeignException;
import feign.Response;
import feign.codec.DecodeException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.util.TypeUtils;
import org.springframework.web.client.HttpMessageConverterExtractor;

/**
 * 描述：feign接收数据后的解码器. <br>
 * 背景：需优先拦截服务提供者返回的是否是异常信息，不是异常信息才走spring的解码器. <br>
 * 日期：2018-01-24 17:18 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-24     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class FeignDecoder extends SpringDecoder {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    
    private final ObjectFactory<HttpMessageConverters> messageConverters;

    private static final ConcurrentHashMap<String, Type> classTypeCache = new ConcurrentHashMap<>(100, 0.75F, 1);

    public FeignDecoder(ObjectFactory<HttpMessageConverters> messageConverters) {
        super(messageConverters);
        this.messageConverters = messageConverters;
    }

    @Override
    public Object decode(final Response response, Type type) throws IOException, FeignException {
        // 1. 若为流式响应，则直接返回原始内容
        if (isStreamResponse(response)) {
            return handleStreamResponse(response, type);
        }
        // TODO 需要测试接口返回错误时，void 类型的方法是否正常
        // 2. 获取响应体并记录耗时
        long bgn = System.currentTimeMillis();
        String responseText = getResponse(response);
        if (log.isDebugEnabled()) {
            long end = System.currentTimeMillis();
            String mesg = String.format("get response time=%.3fs, body:%s", (end - bgn) / 1_000F, responseText);
            log.debug(mesg);
        }
        // 3. 空响应处理
        if (StringUtil.isNull(responseText)) {
            return null;
        }
        // 4. 尝试解析为自定义ReturnData格式
        try {
            bgn = System.currentTimeMillis();
            // 需将type缓存，否则有OOM风险!!
//            Type respType = TypeToken.getParameterized(ReturnData.class, type).getType();
            Type respType = classTypeCache.computeIfAbsent(type.getTypeName(), k ->
                    TypeToken.getParameterized(ReturnData.class, type).getType()
            );
            ReturnData<?> data = JsonUtil.deserialize(responseText, respType);
            if (log.isDebugEnabled()) {
                long end = System.currentTimeMillis();
                String mesg = String.format("parse json time=%.3fs", (end - bgn) / 1_000F);
                log.debug(mesg);
            }
            checkException(data);
            return data.getData();
        } catch (AbstractBaseException e) {
            throw e;
        } catch (Exception e) {
            // 一般是json解析错误，降级为Spring默认处理
            log.warn("feign decode parse json error. Request: {} {}, Response: {}", response.request().httpMethod(), response.request().url(), responseText, e);
        }
        // 5. 降级为Spring HttpMessageConverter处理
        if (TypeUtils.isAssignable(type, Object.class)) {
//        if (type instanceof Class || type instanceof ParameterizedType || type instanceof WildcardType) {
            HttpMessageConverterExtractor<?> extractor = new HttpMessageConverterExtractor<>(type, this.messageConverters.getObject().getConverters());
            // 将输入流替换为字符串
            return extractor.extractData(new FeignResponseAdapter<>(response, responseText));
        }
        // 6. 不支持的类型
        throw new DecodeException(500,
                "The response type[" + type + "] is not an instance of Class or ParameterizedType. Request: " + response.request(),
                response.request());
    }

    /**
     * 判断是否流响应
     */
    private boolean isStreamResponse(Response response) {
        // 获取Content-Type头（忽略大小写）
        Collection<String> contentTypes = response.headers().get("Content-Type");
        if (contentTypes == null || contentTypes.isEmpty()) {
            return false;
        }
        String contentType = contentTypes.iterator().next().toLowerCase();
        // 判断是否为流式类型：二进制流、文件类型（pdf/png等）或自定义标记
        return contentType.contains("application/octet-stream")
                || contentType.contains("binary")
                || contentType.matches("application/(pdf|zip|excel)|image/.*|video/.*") // 常见文件类型
                || response.headers().containsKey("X-Stream"); // 自定义头标记流式响应
    }

    /**
     * 实现流式响应处理逻辑
     */
    private Object handleStreamResponse(Response response, Type type) throws IOException {
        Response.Body body = response.body();
        if (body == null) {
            log.warn("Stream response body is null. Request: {}", response.request());
            return null;
        }

        // 根据目标类型返回不同结果（支持byte[]、InputStream、Resource）
        if (type instanceof Class<?> targetClass) {
            if (byte[].class.equals(targetClass)) {
                // 返回字节数组（一次性读取，适合小文件）
                return body.asInputStream().readAllBytes();
            } else if (InputStream.class.isAssignableFrom(targetClass)) {
                // 返回输入流（需调用方自行关闭，适合大文件）
                return body.asInputStream();
            } else if (Resource.class.isAssignableFrom(targetClass)) {
                // 返回Spring Resource（自动管理流关闭）
                return new InputStreamResource(body.asInputStream());
            }
        }

        // 不支持的流式目标类型
        throw new DecodeException(415,
                "Unsupported stream response type: " + type + ". Request: " + response.request(),
                response.request());
    }

    /**
     * 获取响应内容.
     * @param response 响应对象
     * @return 响应内容
     * @throws IOException 数据流异常
     */
    private String getResponse(Response response) throws IOException {
        if (response.body() == null) {
            return null;
        }
        try (InputStream is = response.body().asInputStream()) {
            String str = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            return StringUtil.isNull(str) ? null : str;
        }
    }
    
    /**
     * 异常检测.
     * @param data 响应信息
     * @throws AbstractBaseException 异常信息
     */
    private void checkException(ReturnData<?> data) throws AbstractBaseException {
        if (SystemReturnCode.SUCCESS.getCode().equals(data.getStatus())) {
            return;
        }
        RemoteServiceException error = new RemoteServiceException();
        error.setStatus(data.getStatus());
        error.setCode(data.getCode());
        error.setLevel(data.getLevel());
        error.setMesg(data.getMessage());
        if (data.getData() != null) {
            error.setTrace(data.getData().toString());
        }
        throw error;
    }

    /**
     * 仿照SpringDecoder.FeignResponseAdapter的处理逻辑.<br>
     * 只是将getBody()获取内容流的部分做了修改
     */
    private class FeignResponseAdapter<T> implements ClientHttpResponse {

        private final Response response;
        private final T responseText;

        private FeignResponseAdapter(Response response, T str) {
            this.response = response;
            this.responseText = str;
        }

        @NonNull
        @Override
        public HttpStatus getStatusCode() throws IOException {
            return HttpStatus.valueOf(this.response.status());
        }

        @NonNull
        @Override
        public String getStatusText() throws IOException {
            return this.response.reason();
        }

        @Override
        public void close() {
            // 不做任何处理，流已经关闭
        }
        
        /**
         * 将原来response.body().asInputStream()更换为从字符串字节流.
         */
        @NonNull
        @Override
        public InputStream getBody() throws IOException {
            // 将对象作为输入流
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            if (this.responseText instanceof String) {
                bout.writeBytes(((String) this.responseText).getBytes(this.response.charset()));
            } else {
                // ObjectOutputStream.writeStreamHeader 会添加4字节头信息
                ObjectOutputStream out = new ObjectOutputStream(bout);
                out.writeObject(this.responseText);
                out.close();
            }
            return new ByteArrayInputStream(bout.toByteArray());
        }

        @NonNull
        @Override
        public HttpHeaders getHeaders() {
            HttpHeaders httpHeaders = new HttpHeaders();
            for (Map.Entry<String, Collection<String>> entry : this.response.headers().entrySet()) {
                httpHeaders.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            return httpHeaders;
        }

    }
}
