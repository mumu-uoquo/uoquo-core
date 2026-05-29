/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.events.deserializer;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 静态注册表，维护 Java 内置类型简单名到 {@link Class} 的映射。
 * <p>
 * 覆盖范围：java.lang.* 常用类 + java.math.BigDecimal/BigInteger
 * + java.util.List/Map/Set 等集合接口。
 * </p>
 */
public final class BuiltinTypeRegistry {

    private static final Map<String, Class<?>> REGISTRY;

    static {
        REGISTRY = Map.ofEntries(
            Map.entry("String",     String.class),
            Map.entry("Long",       Long.class),
            Map.entry("Integer",    Integer.class),
            Map.entry("Short",      Short.class),
            Map.entry("Byte",       Byte.class),
            Map.entry("Double",     Double.class),
            Map.entry("Float",      Float.class),
            Map.entry("Boolean",    Boolean.class),
            Map.entry("Character",  Character.class),
            Map.entry("Number",     Number.class),
            Map.entry("Void",       Void.class),
            Map.entry("BigDecimal", BigDecimal.class),
            Map.entry("BigInteger", BigInteger.class),
            Map.entry("List",       List.class),
            Map.entry("Map",        Map.class),
            Map.entry("Set",        Set.class)
        );
    }

    private BuiltinTypeRegistry() {
        // utility class
    }

    /**
     * 按简单类名查找内置类型。
     *
     * @param simpleName 简单类名（如 {@code "String"}、{@code "BigDecimal"}）
     * @return 对应的 {@link Class}，若未注册则返回 {@link Optional#empty()}
     */
    public static Optional<Class<?>> lookup(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(REGISTRY.get(simpleName));
    }
}
