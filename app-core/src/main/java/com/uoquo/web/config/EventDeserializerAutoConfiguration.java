/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.config;

import com.uoquo.web.events.deserializer.ApplicationEventScan;
import com.uoquo.web.events.deserializer.DataTypeResolver;
import com.uoquo.web.events.deserializer.EventPackageScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 自动配置类：扫描 ApplicationEventScan 注解的包。
 */
@Configuration
public class EventDeserializerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EventDeserializerAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(value = EventPackageScanner.class, search = SearchStrategy.CURRENT)
    public EventPackageScanner eventPackageScanner(ApplicationContext ctx) {
        log.info("Scanning annotations for ApplicationEventScan");
        Map<String, Object> beans = ctx.getBeansWithAnnotation(ApplicationEventScan.class);
        List<String> packages = new ArrayList<>();
        for (Object bean : beans.values()) {
            ApplicationEventScan annotation = AnnotationUtils.findAnnotation(bean.getClass(), ApplicationEventScan.class);
            if (annotation != null) {
                packages.addAll(Arrays.asList(annotation.value()));
            }
        }
        return new EventPackageScanner(packages.toArray(new String[0]));
    }

    @Bean
    public DataTypeResolver eventDataTypeResolver(EventPackageScanner scanner) {
        log.info("Load DataTypeResolver: {}", scanner.toString());
        return new DataTypeResolver(scanner);
    }

}
