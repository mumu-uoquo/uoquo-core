/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.mybatis.sensitive;

import com.uoquo.mybatis.sensitive.SensitiveData;
import com.uoquo.mybatis.sensitive.SensitiveField;
import org.apache.ibatis.annotations.Param;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mapper 方法解析工具<br/>
 * 提供以下能力：
 * <ul>
 *   <li>根据 MappedStatement.getId() 解析 Mapper 接口方法（带缓存）</li>
 *   <li>获取方法上的 @SensitiveData 注解（带缓存）</li>
 *   <li>解析方法中需要加密的敏感参数名称（带缓存）</li>
 * </ul>
 *
 * @author xuhz
 */
public class MapperMethodResolver {
    private static final Logger log = LoggerFactory.getLogger(MapperMethodResolver.class);

    /**
     * 缓存: statementId -> Method（Optional.empty 表示找不到）
     */
    private static final ConcurrentHashMap<String, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();

    /**
     * 缓存: Method -> 方法上的 @SensitiveData 注解（Optional.empty 表示没有）
     */
    private static final ConcurrentHashMap<Method, Optional<SensitiveData>> METHOD_ANNOTATION_CACHE = new ConcurrentHashMap<>();

    /**
     * 缓存: Method -> 需要加密的参数名称集合
     */
    private static final ConcurrentHashMap<Method, Set<String>> SENSITIVE_NAMES_CACHE = new ConcurrentHashMap<>();

    // ========================== 方法解析 ==========================

    /**
     * 根据 statementId 获取 Mapper 接口方法
     *
     * @param statementId 格式为 "namespace.methodName"，如 "com.uoquo.user.mapper.UserInfoMapper.selectByPhone"
     * @return 对应的 Method，找不到返回 null
     */
    public static Method getMethod(String statementId) {
        return METHOD_CACHE.computeIfAbsent(statementId, MapperMethodResolver::resolveMethod)
                .orElse(null);
    }

    /**
     * 获取 Mapper 方法上的 @SensitiveData 注解
     *
     * @param statementId MappedStatement.getId()
     * @return @SensitiveData 注解实例，没有则返回 null
     */
    public static SensitiveData getMethodSensitiveData(String statementId) {
        Method method = getMethod(statementId);
        if (Objects.isNull(method)) {
            return null;
        }
        return METHOD_ANNOTATION_CACHE.computeIfAbsent(method, m -> {
            @SuppressWarnings("null")
            SensitiveData annotation = AnnotationUtils.findAnnotation(m, SensitiveData.class);
            return Optional.ofNullable(annotation);
        }).orElse(null);
    }

    /**
     * 获取方法中需要加密的参数名称集合<br/>
     * 判断逻辑：参数上有 @SensitiveField 注解，或参数类型上有 @SensitiveData 注解
     *
     * @param method Mapper 接口方法
     * @return 需要加密的参数名称集合（不可变），无则返回空集合
     */
    public static Set<String> getSensitiveParamNames(Method method) {
        return SENSITIVE_NAMES_CACHE.computeIfAbsent(method, m -> {
            Set<String> paramNames = new HashSet<>();
            Annotation[][] pa = m.getParameterAnnotations();
            Parameter[] parameters = m.getParameters();

            for (int i = 0; i < pa.length; i++) {
                // 1. 判断参数是否需要加密
                // 1.1 优先判断参数上是否有 @SensitiveField 注解
                boolean hasSensitiveField = Arrays.stream(pa[i])
                        .anyMatch(annotation -> annotation instanceof SensitiveField);
                if (!hasSensitiveField) {
                    // 1.2 再判断参数类型上是否有 @SensitiveData 注解
                    @SuppressWarnings("null")
                    SensitiveData typeSensitiveData = AnnotationUtils.findAnnotation(parameters[i].getType(), SensitiveData.class);
                    if (Objects.isNull(typeSensitiveData)) {
                        continue;
                    }
                }

                // 2. 获取参数名称（@Param 注解值 > 形参名称）
                String name = Arrays.stream(pa[i])
                        .filter(annotation -> annotation instanceof Param)
                        .map(annotation -> ((Param) annotation).value())
                        .findFirst()
                        .orElse(parameters[i].getName());
                paramNames.add(name);

                // 2.1 字符串类型时，添加 MyBatis 默认参数名 arg{i}, param{i+1}
                if (parameters[i].getType().equals(String.class)) {
                    paramNames.add("arg" + i);
                    paramNames.add("param" + (i + 1));
                }
            }
            return Collections.unmodifiableSet(paramNames);
        });
    }

    // ========================== 内部方法 ==========================

    private static Optional<Method> resolveMethod(String statementId) {
        try {
            int lastDot = statementId.lastIndexOf(".");
            if (lastDot <= 0) {
                log.warn("statementId格式异常: {}", statementId);
                return Optional.empty();
            }
            String namespace = statementId.substring(0, lastDot);
            String methodName = statementId.substring(lastDot + 1);
            log.debug("解析Mapper方法: namespace={}, methodName={}", namespace, methodName);

            Class<?> mapperClass = Class.forName(namespace);
            Method matched = null;
            for (Method method : mapperClass.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    if (matched == null) {
                        matched = method;
                    } else {
                        // MyBatis 不支持同名重载方法映射到不同 SQL，取第一个即可
                        log.warn("方法[{}]存在重载，使用第一个匹配.", statementId);
                        break;
                    }
                }
            }
            if (matched == null) {
                log.debug("Mapper[{}]中未找到方法[{}].", namespace, methodName);
            }
            return Optional.ofNullable(matched);
        } catch (ClassNotFoundException e) {
            log.warn("Mapper类[{}]不存在.", statementId);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("解析方法[{}]失败.", statementId, e);
            return Optional.empty();
        }
    }
}
