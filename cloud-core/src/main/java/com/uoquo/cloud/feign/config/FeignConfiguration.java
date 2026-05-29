/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.feign.config;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.AnnotatedParameterProcessor;
import org.springframework.cloud.openfeign.FeignFormatterRegistrar;
import org.springframework.cloud.openfeign.annotation.PathVariableParameterProcessor;
import org.springframework.cloud.openfeign.annotation.RequestHeaderParameterProcessor;
import org.springframework.cloud.openfeign.annotation.RequestParamParameterProcessor;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.format.FormatterRegistry;

import com.uoquo.cloud.feign.FeignDecoder;
import com.uoquo.cloud.feign.FeignEncoder;
import com.uoquo.cloud.feign.FeignErrorDecoder;
import com.uoquo.cloud.feign.FeignHeaderInterceptor;
import com.uoquo.cloud.feign.converter.MapFormHttpMessageConverter;
import com.uoquo.cloud.feign.processor.DateFormatter;

import feign.Contract;
import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.form.spring.SpringFormEncoder;
import jakarta.annotation.Nullable;

/**
 * 描述：自定义feign相关配置，方便spring加载. <br>
 * 日期：2018-01-24 15:49 <br>
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
public class FeignConfiguration implements FeignFormatterRegistrar {
    private final org.slf4j.Logger log = LoggerFactory.getLogger(getClass());

    /**
     * http请求时：将Map转为 www-form-urlencoded 发送数据.<br>
     * feign 传输map的处理
     */
    @Bean
    public MapFormHttpMessageConverter mapFormHttpMessageConverter() {
        log.debug("Feign 加载BEAN：MapFormHttpMessageConverter");
        return new MapFormHttpMessageConverter();
    }

    /**
     * feign日志级别.<br>
     * 由application.yml配置文件管理<br>
     * <pre>
     * NONE   ：无记录（DEFAULT）。
     * BASIC  ：只记录请求方法和URL以及响应状态代码和执行时间。
     * HEADERS：记录基本信息以及请求和响应标头。
     * FULL   ：记录请求和响应的头文件，正文和元数据。
     * </pre>
     */
    @Bean  
    Logger.Level feignLoggerLevel() {
        log.debug("Feign 加载BEAN：Logger.Level.FULL");
        return Logger.Level.FULL;
    }
    
    /**
     * 自定义feign编码器.<br>
     * @param messageConverters 消息内容
     * @return 参数编码器
     */
    @Bean
    public Encoder feignEncoder(ObjectFactory<HttpMessageConverters> messageConverters) {
        log.debug("Feign 加载BEAN：SpringFormEncoder");
        // 因为先经过SpringFormEncoder，所以当请求体为空时将由SpringFormEncode直接处理，不再进入FeignEncoder处理
        return new SpringFormEncoder(new FeignEncoder(messageConverters));
    }

    /**
     * 自定义feign解码器.<br>
     * @param messageConverters 消息内容
     * @return 参数解码器
     */
    @Bean
    public Decoder feignDecoder(ObjectFactory<HttpMessageConverters> messageConverters) {
        log.debug("Feign 加载BEAN：FeignDecoder");
        return new FeignDecoder(messageConverters);
    }
    
    /**
     * 自定义feign error解码器.<br>
     * @return error解码器
     */
    @Bean
    public ErrorDecoder feignErrorDecoder(Decoder decoder) {
        log.debug("Feign 加载BEAN：FeignErrorDecoder");
        return new FeignErrorDecoder(decoder);
    }
    
    @Bean
    @ConditionalOnMissingBean(value = FeignHeaderInterceptor.class, search = SearchStrategy.CURRENT)
    public RequestInterceptor headerInterceptor(@Nullable FeignHeaderInterceptor headerInterceptor) {
        // 因为 ConditionalOnMissingBean 没生效，所以采用参数的方式来依赖已有Bean
        if (headerInterceptor != null) {
            log.debug("Feign 沿用BEAN：{}", headerInterceptor);
            return headerInterceptor;
        }
        log.debug("Feign 加载BEAN：FeignHeaderInterceptor");
        return new FeignHeaderInterceptor();
    }
    
    /*
    @Bean
    public Retryer feignRetryer() {
        //TODO 重试间隔为100ms，最大重试时间为1s,重试次数为2次
        return new Retryer.Default(100, SECONDS.toMillis(1), 2);
    }
    */

    /**
     * 自定义Contract.<br>
     * 增加了自定义参数注解
     * @param feignConversionService feignConversionService
     */
    @Bean
    public Contract feignContract(ConversionService feignConversionService) {
        log.debug("Feign 加载BEAN：SpringMvcContract");
        List<AnnotatedParameterProcessor> annotatedArgumentResolvers = new ArrayList<>();
        // default of springMvc
        annotatedArgumentResolvers.add(new PathVariableParameterProcessor());
        annotatedArgumentResolvers.add(new RequestParamParameterProcessor());
        annotatedArgumentResolvers.add(new RequestHeaderParameterProcessor());
        // 自定义processor
        // feign自身的param注解，不重写，防止与原有的冲突
        // annotatedArgumentResolvers.add(new FeignParamProcessor());
        //annotatedArgumentResolvers.add(new RequestParamProcessor());
        
        if (feignConversionService == null) {
            feignConversionService = new DefaultConversionService();
        }
        return new SpringMvcContract(annotatedArgumentResolvers, feignConversionService);
    }
    
    /**
     * 参数为日期时的格式化.
     * @see org.springframework.format.FormatterRegistrar#registerFormatters(org.springframework.format.FormatterRegistry)
     */
    @Override
    public void registerFormatters(FormatterRegistry registry) {
        log.debug("Feign addFormatter：DateFormatter");
        registry.addFormatter(new DateFormatter());
    }
}
