/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uoquo.utils.*;
import com.uoquo.utils.http.HttpClientBuilder;
import com.uoquo.utils.json.JsonUtil;

import okhttp3.OkHttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 描述：项目全局配置. <br>
 * 备注：主要添加了：favicon、字符集、全局拦截、mybatis拦截器、请求头信息处理等<br>
 * 说明：AutoConfigureAfter、AutoConfigureBefore、AutoConfigureOrder 需配合 Configuration 使用，且对应类不能在 ComponentScan 扫描路径中，需在META-INF中配置才能加载
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
@Configuration
@AutoConfigureOrder(AutoConfigureOrder.DEFAULT_ORDER + 100)
public class ServiceConfig {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @PostConstruct
    public void setProperties(){
        log.debug("ServiceConfig init ...");
        // 解决druid日志报错：discard long time none received connection:xxx
        // http://www.icodebang.com/article/220484
        System.setProperty("druid.mysql.usePingMethod", "false");
    }

    @Bean
    @ConditionalOnMissingBean(value = ObjectMapper.class, search = SearchStrategy.CURRENT)
    public ObjectMapper objectMapper() {
        log.debug("加载BEAN：ObjectMapper");
        return JsonUtil.initialJackson(null);
    }

    /**
     * okhttp客户端对象. <br>
     * 备注：可以在application.yml中添加相关参数
     */
    @Bean
    @ConditionalOnMissingBean(value = OkHttpClient.class, search = SearchStrategy.CURRENT)
    public OkHttpClient okHttpClient() {
        log.debug("加载BEAN：OkHttpClient");
        return HttpClientBuilder.buildPooled();
    }

}
