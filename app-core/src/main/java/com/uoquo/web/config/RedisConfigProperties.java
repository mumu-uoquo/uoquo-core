/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.connection.RedisNode;

import com.uoquo.utils.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class RedisConfigProperties extends RedisProperties {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    @Override
    public Sentinel getSentinel() {
        Sentinel sentinel = super.getSentinel();
        if (sentinel == null) {
            return null;
        }
        // 有配置时需要解析
        List<String> nodes = checkNodes(sentinel.getNodes());
        if (nodes.isEmpty()) {
            return null;
        } else {
            // 因为 PropertiesRedisConnectionDetails.getSentinel() 调用了 getStandalone().getDatabase() 导致要检测host的配置
            // 所以此处需要设置host，否则会报错，无实际用处，仅用于检测
            if (StringUtil.isNull(this.getHost())) {
                this.setHost("localhost");
            }
            log.debug("redis sentinel property '{}'", nodes);
            sentinel.setNodes(nodes);
            return sentinel;
        }
    }

    @Override
    public Cluster getCluster() {
        // 有哨兵的配置时，优先用哨兵配置
        if (this.getSentinel() != null) {
            return null;
        }
        // 解析集群配置
        Cluster cluster = super.getCluster();
        if (cluster == null) {
            return null;
        }
        // 有配置时需要解析
        List<String> nodes = checkNodes(cluster.getNodes());
        if (nodes.isEmpty()) {
            return null;
        } else {
            log.debug("redis cluster property '{}'", nodes);
            cluster.setNodes(nodes);
            return cluster;
        }
    }

    private List<String> checkNodes(List<String> nodes) {
        List<String> result = new ArrayList<>();
        for (String node : nodes) {
            if (StringUtil.isNull(node)) {
                continue;
            }
            try {
                RedisNode.fromString(node);
                result.add(node);
            }
            catch (RuntimeException ex) {
                log.debug("Invalid redis sentinel property '{}'", node, ex);
            }
        }
        return result;
    }
}
