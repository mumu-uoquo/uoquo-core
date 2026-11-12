/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.config;

import com.uoquo.web.events.deserializer.ApplicationEventScan;
import com.uoquo.web.events.deserializer.DataTypeResolver;
import com.uoquo.web.events.deserializer.EventPackageScanner;
import com.uoquo.web.events.deserializer.EventScanPackagesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 自动配置类：收集事件类的扫描包路径，提供 {@link EventPackageScanner} 与 {@link DataTypeResolver}。
 * <p>扫描包路径来自：</p>
 * <ol>
 *   <li>{@link ApplicationEventScan} 注解；</li>
 *   <li>容器中所有 {@link EventScanPackagesProvider}（如 cloud-core 提供的 {@code @RemoteApplicationEventScan}）。</li>
 * </ol>
 */
@Configuration
public class EventDeserializerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EventDeserializerAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(value = EventPackageScanner.class, search = SearchStrategy.CURRENT)
    public EventPackageScanner eventPackageScanner(ApplicationContext ctx,
                                                   ObjectProvider<EventScanPackagesProvider> providers) {
        Set<String> packages = new LinkedHashSet<>();
        // 1. @ApplicationEventScan：只读取注解元数据，不实例化 Bean（避免在容器初始化早期触发 Bean 创建）
        for (String beanName : ctx.getBeanNamesForAnnotation(ApplicationEventScan.class)) {
            ApplicationEventScan annotation = ctx.findAnnotationOnBean(beanName, ApplicationEventScan.class);
            if (annotation != null) {
                packages.addAll(EventScanPackagesProvider.mergePackages(
                        annotation.value(), annotation.basePackages(), annotation.basePackageClasses()));
            }
        }
        // 2. 扩展点：其他模块提供的扫描包
        providers.orderedStream().forEach(provider -> {
            List<String> extra = provider.getPackages();
            if (extra != null) {
                packages.addAll(extra);
            }
        });
        log.info("Event scan packages: {}", packages);
        return new EventPackageScanner(new ArrayList<>(packages).toArray(new String[0]));
    }

    @Bean
    public DataTypeResolver eventDataTypeResolver(EventPackageScanner scanner) {
        log.info("Load DataTypeResolver: {}", scanner.toString());
        return new DataTypeResolver(scanner);
    }

}
