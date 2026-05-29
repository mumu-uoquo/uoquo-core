/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.config;

import com.uoquo.web.ServiceConfig;
import com.uoquo.web.mybatis.interceptor.PageInterceptor;
import com.uoquo.web.mybatis.interceptor.SensitiveParameterInterceptor;
import com.uoquo.web.mybatis.interceptor.SensitiveResultSetInterceptor;
import com.uoquo.web.mybatis.interceptor.SqlCostInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * MyBatis 插件配置
 * 执行顺序：后注入的先执行.
 */
@Configuration
@AutoConfigureAfter(ServiceConfig.class)
@ConditionalOnClass({SqlSessionFactory.class, SqlSessionFactoryBean.class})
public class MyBatisPluginConfig {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    @PostConstruct
    public void initConfiguration(){
        log.debug("MyBatisPluginConfig init ...");
    }

    /**
     * 数据脱敏：出参解密
     */
    @Bean
    public Interceptor sensitiveResultSetInterceptor() {
        log.debug("Use SensitiveResultSetInterceptor");
        return new SensitiveResultSetInterceptor();
    }

    /**
     * 分页插件.
     */
    @Bean
    public Interceptor pageInterceptor() {
        log.debug("Use PageInterceptor");
        return new PageInterceptor();
    }

    /**
     * 数据脱敏：入参加密
     */
    @Bean
    public Interceptor sensitiveParameterInterceptor() {
        log.debug("Use SensitiveParameterInterceptor");
        return new SensitiveParameterInterceptor();
    }

    /**
     * SQL执行时长统计（debug模式有效）.<br>
     */
    @Bean
    public Interceptor sqlCostInterceptor() {
        log.debug("Use SqlCostInterceptor");
        return new SqlCostInterceptor();
    }

//    /**
//     * 数据权限拦截.<br>
//     * 注：一定是放在最后，这样对于分页，可以少进一次该拦截
//     */
//    @Bean
//    public Interceptor dataPolicyInterceptor() {
//        return new DataPolicyInterceptor();
//    }
}
