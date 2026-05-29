/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.config;

import com.uoquo.web.ServiceConfig;
import com.uoquo.web.filter.ContentCachingWrapperFilter;
import com.uoquo.web.filter.LogbackFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @PostConstruct
    public void initConfiguration(){
        log.debug("WebFilterConfig init ...");
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
