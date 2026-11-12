/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events.deserializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 事件类扫描包的提供者（扩展点）。<br>
 * {@link EventPackageScanner} 会合并容器中所有提供者返回的包路径，再加上 {@link ApplicationEventScan} 声明的包路径。
 * 例如：微服务模块（cloud-core）通过它把 {@code @RemoteApplicationEventScan} 声明的包路径交给扫描器，
 * 基础模块（app-core）无需依赖 spring-cloud。
 *
 * @author xuhz
 */
@FunctionalInterface
public interface EventScanPackagesProvider {

    /**
     * 获取要扫描的包路径（支持 Ant 风格通配符）。
     * @return 包路径列表，不能为 null
     */
    List<String> getPackages();

    /**
     * 合并注解中声明的包路径：{@code value}、{@code basePackages}，以及 {@code basePackageClasses} 所在的包，去重并保持顺序。
     * @param value               注解的 value
     * @param basePackages        注解的 basePackages
     * @param basePackageClasses  注解的 basePackageClasses
     * @return 去重后的包路径
     */
    static List<String> mergePackages(String[] value, String[] basePackages, Class<?>[] basePackageClasses) {
        Set<String> packages = new LinkedHashSet<>();
        addAll(packages, value);
        addAll(packages, basePackages);
        if (basePackageClasses != null) {
            for (Class<?> clazz : basePackageClasses) {
                packages.add(clazz.getPackageName());
            }
        }
        return new ArrayList<>(packages);
    }

    private static void addAll(Set<String> target, String[] source) {
        if (source == null) {
            return;
        }
        Arrays.stream(source)
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .forEach(target::add);
    }
}
