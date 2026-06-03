/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.uoquo.cloud.events.BusErrorChannelHandler;
import com.uoquo.cloud.events.RemoteEvent;
import com.uoquo.cloud.events.deserializer.RemoteEventDeserializer;
import com.uoquo.web.events.deserializer.DataTypeResolver;
import com.uoquo.web.events.deserializer.EventPackageScanner;
import com.uoquo.cloud.kafka.DeserializationFailureData;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.cloud.bus.jackson.BusJacksonAutoConfiguration;
import org.springframework.cloud.bus.jackson.RemoteApplicationEventScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.integration.channel.AbstractMessageChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 自动配置类，将 {@link RemoteEventDeserializer} 精确注册到
 * spring-cloud-bus 专用的 {@link ObjectMapper}，不影响业务 ObjectMapper。
 *
 * <p>通过 {@link BeanPostProcessor} 完成两件事：</p>
 * <ol>
 *   <li>{@code postProcessBeforeInitialization}：在 {@code busJsonConverter} 的
 *       {@code afterPropertiesSet()} 之前注册 {@link RemoteEventDeserializer} 模块，
 *       不破坏已有的 {@code @JsonSubTypes} 映射。</li>
 *   <li>{@code postProcessAfterInitialization}：在 {@code springCloudBusInput} channel 上
 *       注册 {@link ChannelInterceptor}，当 payload 是 {@link DeserializationFailureData}
 *       时直接丢弃，阻止消息进入 busJsonConverter 做 Jackson 反序列化。</li>
 * </ol>
 */
@Configuration
@ConditionalOnClass(BusJacksonAutoConfiguration.class)
@AutoConfigureAfter(BusJacksonAutoConfiguration.class)
@ConditionalOnProperty(name = "app.kafka.default.enabled", havingValue = "true", matchIfMissing = false)
public class RemoteEventDeserializerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RemoteEventDeserializerAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(value = EventPackageScanner.class, search = SearchStrategy.CURRENT)
    public EventPackageScanner eventPackageScanner(ApplicationContext ctx) {
        log.info("Scanning annotations for RemoteApplicationEventScan");
        Map<String, Object> beans = ctx.getBeansWithAnnotation(RemoteApplicationEventScan.class);
        List<String> packages = new ArrayList<>();
        for (Object bean : beans.values()) {
            RemoteApplicationEventScan annotation = AnnotationUtils.findAnnotation(bean.getClass(), RemoteApplicationEventScan.class);
            if (annotation != null) {
                packages.addAll(Arrays.asList(annotation.value()));
            }
        }
        return new EventPackageScanner(packages.toArray(new String[0]));
    }

    @Bean
    public RemoteEventDeserializer remoteEventDeserializer(DataTypeResolver resolver) {
        return new RemoteEventDeserializer(resolver);
    }

    @Bean
    public BeanPostProcessor remoteEventDeserializerBeanPostProcessor(RemoteEventDeserializer deserializer) {
        return new BeanPostProcessor() {

            /**
             * 在 busJsonConverter 的 afterPropertiesSet() 之前注册自定义反序列化模块。
             */
            @Override
            public Object postProcessBeforeInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
                if (!"busJsonConverter".equals(beanName)) {
                    return bean;
                }
                Field mapperField = ReflectionUtils.findField(bean.getClass(), "mapper", ObjectMapper.class);
                if (mapperField == null) {
                    log.warn("RemoteEventDeserializerAutoConfiguration: 'mapper' field not found in {}", bean.getClass().getName());
                    return bean;
                }
                ReflectionUtils.makeAccessible(mapperField);
                ObjectMapper busMapper = (ObjectMapper) ReflectionUtils.getField(mapperField, bean);
                if (busMapper == null) {
                    log.warn("RemoteEventDeserializerAutoConfiguration: Bus ObjectMapper is null, skipping");
                    return bean;
                }
                // 自定义RemoteEvent解析器
                SimpleModule module = new SimpleModule("RemoteEventDeserializerModule");
                module.addDeserializer(RemoteEvent.class, deserializer);
                busMapper.registerModule(module);
                log.debug("RemoteEventDeserializer registered to Bus ObjectMapper before afterPropertiesSet");
                return bean;
            }

            /**
             * 在 springCloudBusInput channel 上注册拦截器，
             * payload 为 {@link DeserializationFailureData} 时直接丢弃，
             * 不再进入 busJsonConverter 做二次 Jackson 反序列化。
             */
            @Override
            public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
                if (!"springCloudBusInput".equals(beanName) || !(bean instanceof AbstractMessageChannel)) {
                    return bean;
                }
                ((AbstractMessageChannel) bean).addInterceptor(new ChannelInterceptor() {
                    @Override
                    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                        if (message.getPayload() instanceof DeserializationFailureData) {
                            log.debug("springCloudBusInput: dropping DeserializationFailureData sentinel, already logged by DeserializationFailureHandler");
                            return null; // 返回 null 表示丢弃该消息
                        }
                        return message;
                    }
                });
                log.debug("DeserializationFailureData filter interceptor registered on springCloudBusInput");
                return bean;
            }
        };
    }

    /* ******************* Spring Integration errorChannel handler ******************* */

    @Bean
    public BusErrorChannelHandler busErrorChannelHandler() {
        return new BusErrorChannelHandler();
    }
}
