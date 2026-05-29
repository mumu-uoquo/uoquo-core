/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.loadbalancer;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 不进行自动扫描注入，由具体 remote 的 service 类添加 LoadBalancerClient 注解来指定。
 * <pre>使用示例
 *    ```java
 *    @ FeignClient(name = "demo-cloud-book", contextId = "BookRemoteService", path = "/api/book")
 *    @ LoadBalancerClient(name = "demo-cloud-book", configuration = IphashLoadBalancerConfiguration.class)
 *    public interface BookRemoteService {
 *        @ RequestMapping(value = "/my/book/name")
 *        String getBookName();
 *    }
 *    ```
 * </pre>
 *
 * 注意：应用实例列表 {@link ServiceInstanceListSupplier} 不要使用缓存.<br>
 * <a href="https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#switching-between-the-load-balancing-algorithms">官网文档</a>
 */
public class IphashLoadBalancerConfiguration {

	@Bean
	public ReactorLoadBalancer<ServiceInstance> iphashLoadBalancer(Environment environment, LoadBalancerClientFactory loadBalancerClientFactory) {
		String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
		return new IphashLoadBalancer(loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class), name);
	}

}
