/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.loadbalancer;

import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注入后，默认全局使用IPHASH负载策略
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} that sets up LoadBalancer for Iphash.
 */
@Deprecated
@Configuration(proxyBeanMethods = false)
@ConditionalOnDiscoveryEnabled
@LoadBalancerClients(defaultConfiguration = IphashLoadBalancerConfiguration.class)
public class IphashLoadBalancerClientAutoConfiguration {

	@Bean
	public IphashLoadBalancerClient iphashLoadBalancerClient(LoadBalancerClientFactory loadBalancerClientFactory) {
		return new IphashLoadBalancerClient(loadBalancerClientFactory);
	}
}
