/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.loadbalancer;

import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.StringUtil;
import com.uoquo.web.utils.WebUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * IP Hash负载均衡策略实现
 */
public class IphashLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final String serviceId;

    private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

    public IphashLoadBalancer(
            ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider,
            String serviceId) {
        this.serviceId = serviceId;
        this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        RequestData requestData = ((RequestDataContext)request.getContext()).getClientRequest();
        String serviceId = requestData.getUrl().getHost();
        ServiceInstanceListSupplier supplier = serviceInstanceListSupplierProvider
                .getIfAvailable(NoopServiceInstanceListSupplier::new);
        return supplier.get(request).next()
                .map(serviceInstances -> getInstanceResponse(serviceId, requestData, serviceInstances));
    }

    private Response<ServiceInstance> getInstanceResponse(String serviceId, RequestData requestData, List<ServiceInstance> instances) {
        if (instances.isEmpty()) {
            log.warn("No servers available for service: {}", serviceId);
            return new EmptyResponse();
        }
        String clientIp = CurrentUser.getClientIp();
        if (StringUtil.isNull(clientIp)) {
            clientIp = this.getClientIp(requestData);
        }
        int pos;
        if (StringUtil.isNull(clientIp)) {
            // 获取不到clientIp时，采用随机模式
            pos = ThreadLocalRandom.current().nextInt(instances.size());
        } else {
            // 获取到clientIp时，采用hash
            int hash = Math.abs(clientIp.hashCode());
            pos = hash % instances.size();
        }
        ServiceInstance instance = instances.get(pos);
        log.info("servers [{}], request ip [{}], instance [{}/{}] is [{}:{}]",
                serviceId, clientIp, pos, instances.size(), instance.getHost(), instance.getPort());
        return new DefaultResponse(instance);
    }

    private String getClientIp(RequestData requestData) {
        HttpHeaders headers = requestData.getHeaders();
        // 1. 优先从当前用户中获取
        String clientIP = CurrentUser.getClientIp();
        if (StringUtil.isNull(clientIP) || "unknown".equalsIgnoreCase(clientIP)) {
            clientIP = headers.getFirst(CurrentUser.CLIENT_IP);
        }
        // 2. 再获取请求头中的
        if (StringUtil.isNull(clientIP) || "unknown".equalsIgnoreCase(clientIP)) {
            clientIP = WebUtil.getClientIp(headers);
        }
        // 3. 最后获取getRemoteAddr
        if (StringUtil.isNull(clientIP) || "unknown".equalsIgnoreCase(clientIP)) {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                clientIP = attributes.getRequest().getRemoteAddr();
            }
        }
        // 返回内容
        if (StringUtil.isNull(clientIP) || "unknown".equalsIgnoreCase(clientIP)) {
            return null;
        } else {
            return clientIP;
        }
    }

}
