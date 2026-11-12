/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events.deserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.uoquo.utils.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.util.ClassUtils;

/**
 * 在 {@code @RemoteApplicationEventScan} 或 {@code @ApplicationEventScan} 声明的包路径下扫描所有类，
 * 结果按简单类名缓存，支持 Ant 风格通配符包路径。
 */
public class EventPackageScanner implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(EventPackageScanner.class);

    /** 是否在应用启动完成后预热索引（默认开启，可通过 {@code app.event.scanner.warm-up=false} 关闭） */
    private static final String WARM_UP_KEY = "app.event.scanner.warm-up";

    private final String[] basePackages;
    private final AtomicReference<Map<String, List<Class<?>>>> indexRef = new AtomicReference<>();

    /**
     * @param basePackages 来自 {@code @RemoteApplicationEventScan} 或 {@code @ApplicationEventScan}  的包路径数组（支持 Ant 风格通配符）
     */
    public EventPackageScanner(String[] basePackages) {
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
        return index().getOrDefault(simpleName, Collections.emptyList());
    }

    /**
     * 获取扫描索引：首次调用时构建（同一时刻只有一个线程扫描，其他线程等待结果）。
     */
    private Map<String, List<Class<?>>> index() {
        Map<String, List<Class<?>>> index = indexRef.get();
        if (index == null) {
            synchronized (this) {
                index = indexRef.get();
                if (index == null) {
                    index = buildIndex();
                    indexRef.set(index);
                }
            }
        }
        return index;
    }

    /**
     * 预热：立即构建扫描索引（已构建则直接返回）。<br>
     * 索引需要扫描所有声明的包并加载其中的类，耗时与类的数量有关；
     * 如果等到第一条事件到达时才扫描，这个耗时会落在消息消费线程上，扫描出现的问题也要到那时才会暴露。
     * @return 索引中的类的数量
     */
    public int warmUp() {
        boolean first = indexRef.get() == null;
        long start = System.currentTimeMillis();
        int count = index().values().stream().mapToInt(List::size).sum();
        if (first) {
            log.info("EventPackageScanner warm up: {} classes indexed in {} ms. {}",
                    count, System.currentTimeMillis() - start, this);
        }
        return count;
    }

    /**
     * 索引是否已经构建。
     */
    public boolean isWarmedUp() {
        return indexRef.get() != null;
    }

    /**
     * 所有单例 Bean 创建完成后预热索引（此时扫描包的来源已经齐全）。预热失败只记录日志，仍然保留首次使用时再扫描的兜底。
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (!Config.getBoolean(WARM_UP_KEY, true)) {
            log.debug("EventPackageScanner warm up is disabled by '{}'.", WARM_UP_KEY);
            return;
        }
        try {
            warmUp();
        } catch (Throwable e) {
            log.warn("EventPackageScanner warm up failed, the index will be built on first use.", e);
        }
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
                            // 同一个类可能被多个扫描模式（如 **.event 与 **.model）同时命中，需按 Class 去重，
                            // 否则会被误判为“简单类名歧义”而退化为 Map
                            List<Class<?>> sameName = index.computeIfAbsent(simpleName, k -> new ArrayList<>());
                            if (!sameName.contains(clazz)) {
                                sameName.add(clazz);
                            }
                        }
                    } catch (ClassNotFoundException | LinkageError e) {
                        log.debug("EventPackageScanner: skipping class that cannot be loaded from resource {}: {}",
                                resource, e.getMessage());
                    } catch (IOException e) {
                        log.debug("EventPackageScanner: skipping unreadable resource {}: {}",
                                resource, e.getMessage());
                    }
                }
            } catch (IOException e) {
                log.warn("EventPackageScanner: failed to scan package pattern '{}': {}", pattern, e.getMessage());
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

    @Override
    public String toString() {
        return "EventPackageScanner{" +
                "basePackages=" + Arrays.toString(basePackages) +
                '}';
    }
}
