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

    protected static final Logger log = LoggerFactory.getLogger(RedisConfigProperties.class);

    @Override
    public Sentinel getSentinel() {
        Sentinel sentinel = super.getSentinel();
        if (sentinel != null) {
            List<String> nodes = checkNodes(sentinel.getNodes());
            if (nodes.isEmpty()) {
                super.setSentinel(null);
            } else {
                sentinel.setNodes(nodes);
            }
        }
        return super.getSentinel();
    }

    @Override
    public Cluster getCluster() {
        Cluster cluster = super.getCluster();
        if (cluster != null) {
            List<String> nodes = checkNodes(cluster.getNodes());
            if (nodes.isEmpty()) {
                super.setCluster(null);
            } else {
                cluster.setNodes(nodes);
            }
        }
        return super.getCluster();
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
