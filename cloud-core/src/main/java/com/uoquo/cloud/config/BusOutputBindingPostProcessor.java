/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Map;

/**
 * 让 Bus 的输出通道（{@code springCloudBusOutput}）在<b>启动阶段</b>创建并绑定。
 *
 * <p>Spring Cloud Bus 通过 {@code StreamBridge} 发送消息，而 {@code StreamBridge} 的输出绑定默认在<b>第一次发送时</b>才创建
 * （创建 Kafka AdminClient、检查/创建主题、创建 Producer，约 200ms，发生在发布事件的线程上）。
 * Kafka 不可用等问题要到第一次发布事件时才暴露，第一次发布也会变慢。</p>
 *
 * <p>Spring Cloud Stream 的 {@code spring.cloud.stream.output-bindings} 配置项（多个用 {@code ;} 分隔）可以让指定的输出绑定在启动时创建。
 * 这里把 {@code springCloudBusOutput} 追加到该配置项中，已有的配置保留。
 * 本类只在启用了 Bus 时注册（见 {@link RemoteEventDeserializerAutoConfiguration}）。</p>
 *
 * @author xuhz
 */
public class BusOutputBindingPostProcessor implements BeanFactoryPostProcessor, EnvironmentAware {
    private static final Logger log = LoggerFactory.getLogger(BusOutputBindingPostProcessor.class);

    /** Spring Cloud Stream 的配置项：启动时创建的输出绑定 */
    static final String OUTPUT_BINDINGS = "spring.cloud.stream.output-bindings";

    /** Spring Cloud Bus 的输出绑定名（BusConstants.OUTPUT） */
    static final String BUS_OUTPUT = "springCloudBusOutput";

    static final String PROPERTY_SOURCE_NAME = "uoquoBusOutputBinding";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!(environment instanceof ConfigurableEnvironment configurable)) {
            return;
        }
        String current = configurable.getProperty(OUTPUT_BINDINGS);
        String merged = merge(current);
        if (merged == null) {
            return;
        }
        configurable.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(OUTPUT_BINDINGS, merged)));
        log.debug("Bus output binding will be created at startup: {}={}", OUTPUT_BINDINGS, merged);
    }

    /**
     * @param current 已有的配置值（可空）
     * @return 追加 Bus 输出绑定后的值；已经包含时返回 {@code null}（无需修改）
     */
    static String merge(String current) {
        if (!StringUtils.hasText(current)) {
            return BUS_OUTPUT;
        }
        boolean exists = Arrays.stream(current.split(";")).map(String::trim).anyMatch(BUS_OUTPUT::equals);
        return exists ? null : current.trim() + ";" + BUS_OUTPUT;
    }
}
