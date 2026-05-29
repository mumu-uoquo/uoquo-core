/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.events.deserializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 在 {@code @RemoteApplicationEventScan} 声明的包路径下扫描所有类，
 * 结果按简单类名缓存，支持 Ant 风格通配符包路径。
 */
public class RemoteEventPackageScanner {

    private static final Logger log = LoggerFactory.getLogger(RemoteEventPackageScanner.class);

    private final String[] basePackages;
    private final AtomicReference<Map<String, List<Class<?>>>> indexRef = new AtomicReference<>();

    /**
     * @param basePackages 来自 {@code @RemoteApplicationEventScan} 的包路径数组（支持 Ant 风格通配符）
     */
    public RemoteEventPackageScanner(String[] basePackages) {
        this.basePackages = basePackages == null ? new String[0] : basePackages;
    }

    /**
     * 按简单类名查找，返回所有匹配的类（可能为空或多个）。
     * 首次调用时懒加载并缓存扫描结果。
     *
     * @param simpleName 简单类名（如 {@code "UserInfo"}）
     * @return 匹配的类列表，未找到时返回空列表
     */
    public List<Class<?>> findBySimpleName(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) {
            return Collections.emptyList();
        }
        Map<String, List<Class<?>>> index = indexRef.get();
        if (index == null) {
            index = buildIndex();
            if (!indexRef.compareAndSet(null, index)) {
                // another thread beat us; use the winner's result
                index = indexRef.get();
            }
        }
        return index.getOrDefault(simpleName, Collections.emptyList());
    }

    /**
     * 将 Ant 风格包路径转换为 classpath 资源路径，使用
     * {@link PathMatchingResourcePatternResolver} 扫描 {@code .class} 文件，
     * 构建简单类名 → 类列表的索引。
     */
    private Map<String, List<Class<?>>> buildIndex() {
        Map<String, List<Class<?>>> index = new HashMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        SimpleMetadataReaderFactory metadataReaderFactory = new SimpleMetadataReaderFactory();

        for (String pkg : basePackages) {
            String pattern = toResourcePattern(pkg);
            try {
                Resource[] resources = resolver.getResources(pattern);
                for (Resource resource : resources) {
                    if (!resource.isReadable()) {
                        continue;
                    }
                    try {
                        MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                        String className = metadataReader.getClassMetadata().getClassName();
                        Class<?> clazz = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
                        String simpleName = clazz.getSimpleName();
                        if (!simpleName.isBlank()) {
                            index.computeIfAbsent(simpleName, k -> new ArrayList<>()).add(clazz);
                        }
                    } catch (ClassNotFoundException | LinkageError e) {
                        log.debug("RemoteEventPackageScanner: skipping class that cannot be loaded from resource {}: {}",
                                resource, e.getMessage());
                    } catch (IOException e) {
                        log.debug("RemoteEventPackageScanner: skipping unreadable resource {}: {}",
                                resource, e.getMessage());
                    }
                }
            } catch (IOException e) {
                log.warn("RemoteEventPackageScanner: failed to scan package pattern '{}': {}", pattern, e.getMessage());
            }
        }
        return index;
    }

    /**
     * 将包路径（支持 Ant 风格通配符）转换为 classpath 资源路径。
     * <p>
     * 例如：{@code com.uoquo.user} → {@code classpath*:com/uoquo/user/**}{@code /*.class}
     * </p>
     */
    static String toResourcePattern(String packagePath) {
        // replace dots with slashes (but not wildcard segments like **)
        String slashed = packagePath.replace('.', '/');
        return "classpath*:" + slashed + "/**/*.class";
    }
}
