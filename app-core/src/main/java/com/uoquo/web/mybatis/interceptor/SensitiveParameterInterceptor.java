/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.mybatis.interceptor;

import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.mybatis.sensitive.MapperMethodResolver;
import com.uoquo.mybatis.sensitive.SensitiveData;
import com.uoquo.web.mybatis.sensitive.SensitiveUtil;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 参数加密插件<br/>
 * <pre>
 * 使用 mybatis 插件时需要定义签名
 * \@Intercepts({
 *      \@Signature(type = Executor.class, method = "query/update", args = {...})
 * })
 * 注：需要实现 Executor 的拦截，而不是 ParameterHandler，否则分页插件的统计数值参数将不会做加密处理
 * </pre>
 **/
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
})
public class SensitiveParameterInterceptor implements Interceptor {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Value("${app.mybatis.sensitive.key:RXD8rxIG43_9XMeP}")
    private String sensitiveKey;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 1. 获取参数
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];

        if (Objects.isNull(parameter)) {
            log.debug("[{}]无参数，跳过加密.", mappedStatement.getId());
            return invocation.proceed();
        } else if (log.isDebugEnabled()) {
            log.debug("[{}]的原始入参：{}", mappedStatement.getId(), JsonUtil.serialize(parameter));
        }

        // 2. 参数处理（单参时直接放入当前参数，多参时 ParamMap 将结果进行包装）
        // mapper传单参：batchInsert(List<UserInfo> users); MyBatis 会包装成 ParamMap（key=list），从而走到 2.4 分支
        // sqlsession直接调用：SqlSession.update(statementId, listParam)，会进入 2.1 的 List 判断逻辑
        // 2.1 单参，且参数类型有 @SensitiveData 注解时，直接加密整个对象
        Class<?> parameterObjectClass = parameter.getClass();
        SensitiveData sensitiveData = AnnotationUtils.findAnnotation(parameterObjectClass, SensitiveData.class);
        if (Objects.isNull(sensitiveData) && parameter instanceof List<?> paramList) {
            // List 本身没有 @SensitiveData，取第一个元素的类型判断
            if (!paramList.isEmpty()) {
                Object first = paramList.getFirst();
                if (Objects.nonNull(first)) {
                    sensitiveData = AnnotationUtils.findAnnotation(first.getClass(), SensitiveData.class);
                }
            }
        }
        if (Objects.nonNull(sensitiveData)) {
            log.debug("[{}]的参数[{}] 有 SensitiveData 注解，执行加密处理.", mappedStatement.getId(), parameterObjectClass);
            SensitiveUtil.encrypt(parameter, sensitiveKey);
            return invocation.proceed();
        }

        // 2.2 获取mapper方法
        Method mapperMethod = MapperMethodResolver.getMethod(mappedStatement.getId());
        if (Objects.isNull(mapperMethod)) {
            log.debug("[{}]无对应的方法，跳过加密.", mappedStatement.getId());
            return invocation.proceed();
        }

        // 2.3 若没有 @SensitiveField 注解的参数，则跳过加密逻辑
        Set<String> paramNames = MapperMethodResolver.getSensitiveParamNames(mapperMethod);
        if (CollectionUtils.isEmpty(paramNames)) {
            log.debug("[{}]无 @SensitiveField 注解的参数，跳过加密.", mappedStatement.getId());
            return invocation.proceed();
        }

        // 2.4 参数加密
        if (parameter instanceof MapperMethod.ParamMap<?>) {
            // 多参处理
            @SuppressWarnings("unchecked")
            Map<String, Object> parameterMap = (Map<String, Object>) parameter;
            for (String key : paramNames) {
                if (!parameterMap.containsKey(key)) {
                    continue;
                }
                Object object = parameterMap.get(key);
                try {
                    parameterMap.put(key, SensitiveUtil.encrypt(object, sensitiveKey));
                    log.debug("参数[{}]加密完成.", key);
                } catch (Exception e) {
                    log.error("参数[{}]加密失败.", key, e);
                }
            }
        } else if (parameter instanceof String) {
            // 单参处理：字符串，需要改写 invocation 参数
            parameter = SensitiveUtil.encrypt(parameter, sensitiveKey);
            invocation.getArgs()[1] = parameter;
        } else {
            // 单参处理：其他POJO类型
            SensitiveUtil.encrypt(parameter, sensitiveKey);
        }

        return invocation.proceed();
    }
}
