/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.loadbalancer.blocking.client.BlockingLoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 根据注册中心返回的服务元数据 LoadBalancerRule 是否指定 iphash 策略来负载。
 * 当服务没有配置为 iphash 模式时，将采用默认模式（一般是轮询）.
 * 一般作为全局注解时生效
 */
@Deprecated
public class IphashLoadBalancerClient extends BlockingLoadBalancerClient {
    private static final Logger log = LoggerFactory.getLogger(IphashLoadBalancerClient.class);

    private final LoadBalancerClientFactory loadBalancerClientFactory;

    private Map<String, ObjectProvider<ServiceInstanceListSupplier>> serviceInstanceMap = new ConcurrentHashMap<>();

	public IphashLoadBalancerClient(LoadBalancerClientFactory loadBalancerClientFactory) {
        super(loadBalancerClientFactory);
        this.loadBalancerClientFactory = loadBalancerClientFactory;
    }

    @Override
    public <T> ServiceInstance choose(String serviceId, Request<T> request) {
        // 1. 获取负载策略
        // 1.1 获取服务元数据中配置的策略类型
        ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider =
                serviceInstanceMap.computeIfAbsent(serviceId,
                        k-> loadBalancerClientFactory.getProvider(serviceId, ServiceInstanceListSupplier.class));
//        ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider =
//                loadBalancerClientFactory.getProvider(serviceId, ServiceInstanceListSupplier.class);
        ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider.getIfAvailable(NoopServiceInstanceListSupplier::new);
        Mono<String> lbRule = supplier.get(request).next().map(serviceInstances -> {
            String rule = null;
            if (serviceInstances.size() > 0) {
                rule = serviceInstances.get(0).getMetadata().get("LoadBalancerRule");
            }
            return rule == null ? "" : rule;
        });
        // 1.2 根据元数据的负载策略类型加载对应的负载策略
        ReactiveLoadBalancer<ServiceInstance> loadBalancer = null;
        if ("iphash".equalsIgnoreCase(lbRule.block())) {
            log.debug("service [{}] use IphashLoadBalancer.", serviceId);
            loadBalancer = loadBalancerClientFactory.getInstance(serviceId, IphashLoadBalancer.class);
        }
        // 1.3 若无指定策略，则采用默认负载策略
        if (loadBalancer == null) {
            loadBalancer = loadBalancerClientFactory.getInstance(serviceId);
        }
        if (loadBalancer == null) {
            return null;
        }
        // 2. 执行负载策略
        Response<ServiceInstance> loadBalancerResponse = Mono.from(loadBalancer.choose(request)).block();
        if (loadBalancerResponse == null) {
            return null;
        }
        return loadBalancerResponse.getServer();
    }
}
