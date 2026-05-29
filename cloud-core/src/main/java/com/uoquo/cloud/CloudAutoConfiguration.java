/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud;

import com.uoquo.cloud.feign.config.FeignConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.cloud.bus.jackson.RemoteApplicationEventScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 描述：微服务Feign注解. <br>
 * 参考：https://blog.csdn.net/yusimiao/article/details/97622666<br>
 * 备注：
 * <pre>
 * 1. 项目的具体配置类，需要添加注解
 *     {@code @Configuration}
 * 2. 添加注解
 *    {@code @EnableFeignClients(basePackages = {"com.uoquo.**.remote"}, defaultConfiguration = FeignConfiguration.class)}
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
@Configuration
@AutoConfigureAfter(CloudConfig.class)

@EnableFeignClients(basePackages = {"com.uoquo.**.remote"}, defaultConfiguration = FeignConfiguration.class)
@RemoteApplicationEventScan({"com.uoquo.**.event", "com.uoquo.**.events", "com.uoquo.**.model"})
public class CloudAutoConfiguration {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @PostConstruct
    public void setProperties(){
        log.debug("CloudAutoConfiguration init ...");
    }

}
