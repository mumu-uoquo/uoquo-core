/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.events.deserializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 将 {@code dataType} 字符串解析为 {@link Class} 的独立组件，封装两级降级逻辑。
 *
 * <p>解析状态机：
 * <ol>
 *   <li>null / 空白 → {@code Map.class}（无日志）</li>
 *   <li>含 "." → 先尝试 ClassLoader 全限定名加载；失败后取简单类名走第二级</li>
 *   <li>不含 "." → 直接走第二级</li>
 *   <li>第二级：先查 {@link BuiltinTypeRegistry}；未命中则调用 {@link RemoteEventPackageScanner}</li>
 *   <li>唯一匹配 → WARN 日志 + 返回该 Class</li>
 *   <li>多个匹配 → WARN 日志（含歧义列表）+ {@code Map.class}</li>
 *   <li>无匹配 → WARN 日志（含 dataType 和 eventType 值）+ {@code Map.class}</li>
 * </ol>
 */
public class DataTypeResolver {

    private static final Logger log = LoggerFactory.getLogger(DataTypeResolver.class);

    private final RemoteEventPackageScanner scanner;

    public DataTypeResolver(RemoteEventPackageScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * 解析 {@code dataType}，委托给 {@link #resolve(String, String)}，eventType 默认为 "unknown"。
     *
     * @param dataType JSON 中的 dataType 字段值（可为 null/空）
     * @return 解析到的 Class，无法解析时返回 {@code Map.class}
     */
    public Class<?> resolve(String dataType) {
        return resolve(dataType, "unknown");
    }

    /**
     * 解析 {@code dataType}，按状态机处理。
     *
     * @param dataType  JSON 中的 dataType 字段值（可为 null/空）
     * @param eventType 消息的 type 字段值，仅用于无匹配时的 WARN 日志
     * @return 解析到的 Class，无法解析时返回 {@code Map.class}
     */
    public Class<?> resolve(String dataType, String eventType) {
        // null / 空白 → Map.class（无日志）
        if (dataType == null || dataType.isBlank()) {
            return Map.class;
        }

        if (dataType.contains(".")) {
            // 含 "." → 先尝试全限定名加载
            Optional<Class<?>> byFqn = tryLoadByFqn(dataType);
            if (byFqn.isPresent()) {
                return byFqn.get();
            }
            // 加载失败 → 取最后一个 "." 后的简单类名走第二级
            String simpleName = dataType.substring(dataType.lastIndexOf('.') + 1);
            return resolveBySimpleName(simpleName, dataType, eventType);
        } else {
            // 不含 "." → 直接走第二级
            return resolveBySimpleName(dataType, dataType, eventType);
        }
    }

    /**
     * 第二级解析：先查内置类型，再包扫描。
     */
    private Class<?> resolveBySimpleName(String simpleName, String originalDataType, String eventType) {
        // 第二级 A：检查是否为 Java 内置类型
        Optional<Class<?>> builtin = tryBuiltinType(simpleName);
        if (builtin.isPresent()) {
            return builtin.get();
        }

        // 第二级 B：包扫描
        return tryPackageScan(simpleName, originalDataType, eventType);
    }

    /**
     * 第一级：尝试全限定名加载。
     */
    private Optional<Class<?>> tryLoadByFqn(String fqn) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = DataTypeResolver.class.getClassLoader();
            }
            return Optional.of(cl.loadClass(fqn));
        } catch (ClassNotFoundException e) {
            log.debug("DataTypeResolver: FQN '{}' not found by ClassLoader: {}", fqn, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 第二级 A：检查是否为 Java 内置类型简单名。
     */
    private Optional<Class<?>> tryBuiltinType(String simpleName) {
        return BuiltinTypeRegistry.lookup(simpleName);
    }

    /**
     * 第二级 B：在扫描包范围内按简单类名搜索，处理唯一/多个/无匹配三种情况。
     */
    private Class<?> tryPackageScan(String simpleName, String originalDataType, String eventType) {
        List<Class<?>> matches = scanner.findBySimpleName(simpleName);

        if (matches.size() == 1) {
            Class<?> resolved = matches.getFirst();
            log.info("DataTypeResolver - dataType fallback: '{}' not found by FQN, resolved to '{}' by simple name scan",
                    originalDataType, resolved.getName());
            return resolved;
        } else if (matches.size() > 1) {
            log.warn("DataTypeResolver - dataType ambiguous: simple name '{}' matches multiple classes: {}; falling back to Map",
                    simpleName, matches);
            return Map.class;
        } else {
            log.warn("DataTypeResolver - dataType unresolvable: '{}' (event type='{}'); falling back to Map",
                    originalDataType, eventType);
            return Map.class;
        }
    }
}
