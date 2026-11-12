/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.config;

import com.uoquo.web.events.deserializer.EventScanPackagesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.bus.jackson.RemoteApplicationEventScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * 把 {@link RemoteApplicationEventScan} 声明的包路径提供给 app-core 的事件类扫描器。<br>
 * 与 Kafka 开关无关：只要引入了 cloud-core，事件重放等场景用到的 {@code DataTypeResolver} 就应当能扫描到这些包。
 *
 * @author xuhz
 */
@Configuration
public class RemoteEventScanAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(RemoteEventScanAutoConfiguration.class);

    @Bean
    public EventScanPackagesProvider remoteEventScanPackagesProvider(ApplicationContext ctx) {
        // 在扫描器创建时才读取，且只读取注解元数据，不实例化 Bean
        return () -> readPackages(ctx);
    }

    static List<String> readPackages(ApplicationContext ctx) {
        Set<String> packages = new LinkedHashSet<>();
        for (String beanName : ctx.getBeanNamesForAnnotation(RemoteApplicationEventScan.class)) {
            RemoteApplicationEventScan annotation = ctx.findAnnotationOnBean(beanName, RemoteApplicationEventScan.class);
            if (annotation != null) {
                packages.addAll(EventScanPackagesProvider.mergePackages(
                        annotation.value(), annotation.basePackages(), annotation.basePackageClasses()));
            }
        }
        log.debug("RemoteApplicationEventScan packages: {}", packages);
        return new ArrayList<>(packages);
    }
}
