/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.feign;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;

import feign.RequestTemplate;
import feign.codec.EncodeException;

/**
 * 描述：自定义信息编码器. <br>
 * 背景：用于添加公用的请求头信息<br>
 * 备注：只有含body的请求才会走该编码器，不是所有请求（详见源码{@link feign.ReflectiveFeign.ParseHandlersByName#apply}）！！因此用拦截器FeignHeaderInterceptor替代<br>
 * 日期：2018-01-24 15:54 <br>
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
public class FeignEncoder extends SpringEncoder {

    private static final Logger log = LoggerFactory.getLogger(FeignEncoder.class);

    public FeignEncoder(ObjectFactory<HttpMessageConverters> messageConverters) {
        super(messageConverters);
    }

    @Override
    public void encode(Object requestBody, Type bodyType, RequestTemplate request) throws EncodeException {
        log.debug("in FeignEncoder encode");
        // 自定义序列化逻辑
        if ((requestBody != null) && isPojo(bodyType)) {
            // Feign 发出请求时跳过加解密，微服务间传递原始值
            boolean prevFeignRequest = CurrentUser.isFeignRequest();
            CurrentUser.setFeignRequest(true);
            String jsonBody;
            try {
                jsonBody = JsonUtil.serialize(requestBody);
            } finally {
                CurrentUser.setFeignRequest(prevFeignRequest);
            }
            try {
                request.body(jsonBody);
                // 设置 Content-Type 为 JSON
                request.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                return;
            } catch (Exception e) {
                log.info("远程调用[{}]的方法[{}]入参[{}]序列化失败，尝试SpringEncode原生处理：{}",
                        request.feignTarget().url(), request.feignTarget().name(), bodyType, e.getMessage() );
            }
        }
        // 序列化失败，或空值、特殊类型（如 Map、Collection、String等简单类型）的处理
        super.encode(requestBody, bodyType, request);
    }

    /**
     * 判断是否为自定义 POJO（非基本类型、非集合、非 Map）
     */
    private boolean isPojo(Type type) {
        if (type instanceof Class<?> clazz) {
            return !clazz.isPrimitive()
                    && !StringUtil.isNull(clazz.getPackage())
                    && !Collection.class.isAssignableFrom(clazz)
                    && !Map.class.isAssignableFrom(clazz);
        }
        return false;
    }
}
