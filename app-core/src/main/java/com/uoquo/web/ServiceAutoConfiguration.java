/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.annotation.PostConstruct;

/**
 * 描述：项目全局配置. <br>
 * 参考：https://blog.csdn.net/yusimiao/article/details/97622666<br>
 * 备注：
 * <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html#boot-features-external-config"> spring properties的加载顺序</a>
 * <pre>
 * 1. 项目的具体配置类，需要添加注解
 *     {@code @Configuration}
 * 2. spring扫描包为
 *     启动类所在包及其子包，
 *     com.uoquo.**.config,（已移除，不放入自动扫描，而是通过META-INF配置加载，可以通过AutoConfigureAfter、AutoConfigureBefore、AutoConfigureOrder来调整顺序）
 *     com.uoquo.**.controller,
 *     com.uoquo.**.service,
 *     com.uoquo.**.interceptor,
 *     com.uoquo.**.filter,
 *     com.uoquo.**.servlet,
 *     com.uoquo.**.listener,
 *     com.uoquo.**.task,
 *     com.uoquo.**.spring
 * 4. mybatis扫描包为
 *     com.uoquo.**.mapper
 * </pre>
 * 日期：2018-01-25 11:26 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-25     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
@Configuration
@AutoConfigureAfter(ServiceConfig.class)

//@PropertySource(value = {
//        "classpath:system.properties",      "classpath:system.yml",
//        "classpath:bootstrap.properties",   "classpath:bootstrap.yml",
//        "classpath:application.properties", "classpath:application.yml"
//        }, ignoreResourceNotFound = true)
@ComponentScan({
//        "com.uoquo.**.config",     // 配置相关（@Configuration）
        "com.uoquo.**.controller",  // controller相关（@Controller、@RestController）
        "com.uoquo.**.service",     // service相关（@Service、@Repository）
        "com.uoquo.**.interceptor", // 拦截器
        "com.uoquo.**.listener",    // 监听器（@Component）
        "com.uoquo.**.task",        // 任务相关（@Component）
        "com.uoquo.**.plugin",      // 插件
        "com.uoquo.**.spring",      // 其他需扫描的（如异常处理、工具类等）（@Component）
})
// Servlet相关的类，建议采用Bean注入的方式又Spring接管实例化，而不是直接扫描
@ServletComponentScan({
        "com.uoquo.**.filter",   // 过滤器（@WebFilter）
        "com.uoquo.**.servlet",  // servlet（@WebServlet）
})
@EnableTransactionManagement    // 开启事物
public class ServiceAutoConfiguration {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @PostConstruct
    public void setProperties(){
        log.debug("ServiceAutoConfiguration init ...");
    }
}
