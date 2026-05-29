/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud;

import com.uoquo.cloud.interceptor.CheckParam4GatewayInterceptor;
import com.uoquo.cloud.interceptor.CurrentUser4GatewayInterceptor;
import com.uoquo.web.ServiceConfig;
import com.uoquo.web.interceptor.CheckParamInterceptor;
import com.uoquo.web.interceptor.CurrentUserInterceptorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 描述：微服务验签拦截器等. <br>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
@Configuration
@AutoConfigureBefore(ServiceConfig.class)
@AutoConfigureOrder(AutoConfigureOrder.DEFAULT_ORDER + 90)
public class CloudConfig {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @PostConstruct
    public void setProperties(){
        log.debug("CloudConfig init ...");
    }

    /* ******************* interceptor ******************* */
    @Bean
    @ConditionalOnMissingBean(value = CurrentUserInterceptorAdapter.class, search = SearchStrategy.CURRENT)
    public CurrentUserInterceptorAdapter currentUserInterceptor() {
        log.debug("加载BEAN：CurrentUser4GatewayInterceptor");
        return new CurrentUser4GatewayInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(value = CheckParamInterceptor.class, search = SearchStrategy.CURRENT)
    public CheckParamInterceptor checkParamInterceptor() {
        log.debug("加载BEAN：CheckParam4GatewayInterceptor");
        return new CheckParam4GatewayInterceptor();
    }

//    /* ******************* 默认数据源 ******************* */
//    /**
//     * 由于seata必须包装数据源才可以初始化，因此当没有数据源时，默认使用H2的内存数据库模式.
//     */
//    @Bean
//    @ConditionalOnMissingBean(value = DataSource.class, search = SearchStrategy.CURRENT)
//    public DataSource dataSource() {
//        // TODO seata 在初始化时，偶尔会卡住，因此此处先调用一次，手动将其初始化
//        // 原因：io.seata.tm.api.DefaultGlobalTransaction.COMMIT_RETRY_COUNT 与 ROLLBACK_RETRY_COUNT 在初始化化时感觉产生了死锁
//        io.seata.config.Configuration config = ConfigurationFactory.getInstance();
//        config.getConfig(ConfigurationKeys.SERIALIZE_FOR_RPC, CodecType.SEATA.name());
//        // 事务参数
//        // String url = String.format("jdbc:h2:mem:%s-%s%s", env.getProperty("spring.application.name"), env.getProperty("system.app.code"), env.getProperty("system.app.node"));
//        DruidDataSource dataSource = new DruidDataSource();
//        dataSource.setDriverClassName("org.h2.Driver");
//        dataSource.setUrl("jdbc:h2:mem:");
//        return dataSource;
//    }
//
//
//    /**
//     * 数据源.
//     */
//    @Bean
//    @ConditionalOnMissingBean(value = DataSource.class, search = SearchStrategy.CURRENT)
//    public DataSource dataSource() {
//        // TODO seata 在初始化时，偶尔会卡住，因此此处先调用一次，手动将其初始化
//        // 原因：io.seata.tm.api.DefaultGlobalTransaction.COMMIT_RETRY_COUNT 与 ROLLBACK_RETRY_COUNT 在初始化化时感觉产生了死锁
//        io.seata.config.Configuration config = ConfigurationFactory.getInstance();
//        config.getConfig(ConfigurationKeys.SERIALIZE_FOR_RPC, CodecType.SEATA.name());
//        // 事务参数
//        //return DruidDataSourceBuilder.create().build();
//        DruidDataSource dataSource = new DruidDataSource();
//        dataSource.setDriverClassName(env.getProperty("spring.datasource.driver-class-name"));
//        dataSource.setUrl(env.getProperty("spring.datasource.url"));
//        dataSource.setUsername(env.getProperty("spring.datasource.username"));//用户名
//        dataSource.setPassword(env.getProperty("spring.datasource.password"));//密码
//        dataSource.setInitialSize(env.getProperty("spring.datasource.druid.initial-size", Integer.class, 10));
//        dataSource.setMaxActive(env.getProperty("spring.datasource.druid.max-active", Integer.class, 50));
//        dataSource.setMinIdle(env.getProperty("spring.datasource.druid.min-idle", Integer.class, 0));
//        dataSource.setMaxWait(env.getProperty("spring.datasource.druid.max-wait", Integer.class, 60000));
//        dataSource.setValidationQuery(env.getProperty("spring.datasource.druid.validation-query", "SELECT 1 FROM DUAL"));
//        dataSource.setTestOnBorrow(env.getProperty("spring.datasource.druid.test-on-borrow", Boolean.class, false));
//        dataSource.setTestWhileIdle(env.getProperty("spring.datasource.druid.test-while-idle", Boolean.class, true));
//        dataSource.setPoolPreparedStatements(env.getProperty("spring.datasource.druid.pool-prepared-statements", Boolean.class, false));
//        return dataSource;
//    }
}
