/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.config;

import com.uoquo.web.ServiceConfig;
import com.uoquo.web.filter.ContentCachingWrapperFilter;
import com.uoquo.web.filter.GlobalExceptionFilter;
import com.uoquo.web.filter.LogbackFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * @author xuhz
 */
@Configuration
@AutoConfigureAfter(ServiceConfig.class)
@ConditionalOnWebApplication
public class WebFilterConfig {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** 服务器IP（日志与堆栈前缀使用，输出时已脱敏） */
    @Value("${spring.cloud.client.ip-address:${server.address:unknown}}")
    private String serverIp;

    /** 服务器端口 */
    @Value("${server.port}")
    private Integer serverPort;

    /** 统一出错处理地址（GlobalExceptionFilter 不对其进行兜底） */
    @Value("${server.error.path:${error.path:/error}}")
    private String errorPath;

    @PostConstruct
    public void initConfiguration(){
        log.debug("WebFilterConfig init ...");
    }

    /**
     * 全局异常兜底（Filter 层）.<br>
     * 说明：过滤器为手动实例化（非 Spring Bean），因此 serverIp、serverPort 采用 setter 注入，
     *      类上的 @Value 注解不会生效.
     */
    @Bean
    public FilterRegistrationBean<GlobalExceptionFilter> setGlobalExceptionFilter() {
        log.debug("Use GlobalExceptionFilter");
        GlobalExceptionFilter filter = new GlobalExceptionFilter();
        filter.setServerIp(serverIp);
        filter.setServerPort(serverPort);
        filter.setErrorPath(errorPath);
        FilterRegistrationBean<GlobalExceptionFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(filter);
        filterRegistrationBean.addUrlPatterns("/*");
        filterRegistrationBean.setOrder(filter.getOrder());
        return filterRegistrationBean;
    }

    @Bean
    public FilterRegistrationBean<LogbackFilter> setLogbackFilter() {
        log.debug("Use LogbackFilter");
        LogbackFilter filter = new LogbackFilter();
        FilterRegistrationBean<LogbackFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(filter);
        filterRegistrationBean.addUrlPatterns("/*");
        filterRegistrationBean.setOrder(filter.getOrder());
        return filterRegistrationBean;
    }

    @Bean
    public FilterRegistrationBean<ContentCachingWrapperFilter> setContentCachingWrapperFilter() {
        log.debug("Use ContentCachingWrapperFilter");
        ContentCachingWrapperFilter filter = new ContentCachingWrapperFilter();
        FilterRegistrationBean<ContentCachingWrapperFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter(filter);
        filterRegistrationBean.addUrlPatterns("/*");
        filterRegistrationBean.setOrder(filter.getOrder());
        return filterRegistrationBean;
    }
}
