/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.config;

import com.uoquo.cloud.events.UoquoOriginFactory;
import com.uoquo.cloud.events.UoquoServiceMatcher;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.bus.ServiceMatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用 {@link UoquoServiceMatcher} 包装 Spring Cloud Bus 的 {@link ServiceMatcher}，避免发送方服务处理自己发出的事件。<br>
 * 使用 {@link BeanPostProcessor} 包装已创建的 Bean，不依赖自动配置的加载顺序，也不影响 Bus 的其他配置。
 *
 * @author xuhz
 */
@Configuration
@ConditionalOnClass(ServiceMatcher.class)
@ConditionalOnProperty(name = "spring.cloud.bus.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceMatcherAutoConfiguration {

    /**
     * 注意：BeanPostProcessor 必须是 static 方法，这是 Spring 对 BeanPostProcessor 的要求，避免所在配置类被提前初始化。
     */
    @Bean
    public static BeanPostProcessor uoquoServiceMatcherPostProcessor() {
        UoquoOriginFactory originFactory = new UoquoOriginFactory();
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
                if (bean instanceof ServiceMatcher && !(bean instanceof UoquoServiceMatcher)) {
                    return new UoquoServiceMatcher((ServiceMatcher) bean, () -> originFactory.getOrigin(null));
                }
                return bean;
            }
        };
    }
}
