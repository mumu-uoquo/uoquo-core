/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.mybatis.interceptor;

import com.uoquo.web.mybatis.sensitive.MapperMethodResolver;
import com.uoquo.mybatis.sensitive.SensitiveData;
import com.uoquo.web.mybatis.sensitive.SensitiveUtil;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.CollectionUtils;

import java.sql.Statement;
import java.util.List;
import java.util.Objects;

/**
 * 返回值解密插件<br/>
 * 支持两种触发方式：
 * <ul>
 *   <li>返回的POJO类上有 @SensitiveData 注解</li>
 *   <li>Mapper方法上有 @SensitiveData 注解</li>
 * </ul>
 * 当返回类型为String且Mapper方法有 @SensitiveData 注解时，直接对字符串解密
 **/
@Intercepts({
        @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {Statement.class})
})
public class SensitiveResultSetInterceptor implements Interceptor {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Value("${app.mybatis.sensitive.key:RXD8rxIG43_9XMeP}")
    private String sensitiveKey;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 取出查询的结果
        Object resultObject = invocation.proceed();
        if (Objects.isNull(resultObject)) {
            return null;
        }

        // 获取Mapper方法上的 @SensitiveData 注解
        SensitiveData methodSensitiveData = getMethodSensitiveData(invocation.getTarget());

        if (resultObject instanceof List<?> resultList) {
            // MyBatis内部统一返回List（selectOne 内部也是走 selectList 再取第一个元素）
            if (CollectionUtils.isEmpty(resultList)) {
                return resultObject;
            }

            Object firstItem = resultList.getFirst();

            if (firstItem instanceof String) {
                // 返回值为String列表，且Mapper方法有 @SensitiveData 注解时解密
                if (Objects.nonNull(methodSensitiveData)) {
                    decryptStringList(resultList);
                }
            } else {
                // 返回值为POJO列表
                boolean classNeedDecrypt = needToDecrypt(firstItem);
                if (classNeedDecrypt || Objects.nonNull(methodSensitiveData)) {
                    for (Object result : resultList) {
                        SensitiveUtil.decrypt(result.getClass(), result, sensitiveKey);
                    }
                }
            }
        } else {
            // 理论上MyBatis内部都走List，此处作为兜底
            if (resultObject instanceof String) {
                if (Objects.nonNull(methodSensitiveData)) {
                    try {
                        resultObject = SensitiveUtil.decrypt((String) resultObject, sensitiveKey);
                    } catch (Exception e) {
                        log.error("字符串返回值解密失败.", e);
                    }
                }
            } else {
                if (needToDecrypt(resultObject) || Objects.nonNull(methodSensitiveData)) {
                    SensitiveUtil.decrypt(resultObject.getClass(), resultObject, sensitiveKey);
                }
            }
        }
        return resultObject;
    }

    /**
     * 解密String列表
     */
    @SuppressWarnings("unchecked")
    private void decryptStringList(List<?> resultList) {
        List<Object> list = (List<Object>) resultList;
        for (int i = 0; i < list.size(); i++) {
            Object val = list.get(i);
            if (val instanceof String strVal) {
                try {
                    list.set(i, SensitiveUtil.decrypt(strVal, sensitiveKey));
                } catch (Exception e) {
                    log.error("list第[{}]值解密失败.", i, e);
                }
            }
        }
    }

    /**
     * 获取Mapper方法上的 @SensitiveData 注解
     */
    private SensitiveData getMethodSensitiveData(Object target) {
        try {
            MappedStatement mappedStatement = getMappedStatement(target);
            if (Objects.isNull(mappedStatement)) {
                return null;
            }
            return MapperMethodResolver.getMethodSensitiveData(mappedStatement.getId());
        } catch (Exception e) {
            log.warn("获取Mapper方法的@SensitiveData注解失败.", e);
            return null;
        }
    }

    /**
     * 从ResultSetHandler中获取MappedStatement<br/>
     * 使用MyBatis的SystemMetaObject安全获取，避免脆弱的反射
     */
    private MappedStatement getMappedStatement(Object target) {
        try {
            MetaObject metaObject = SystemMetaObject.forObject(target);
            return (MappedStatement) metaObject.getValue("mappedStatement");
        } catch (Exception e) {
            log.warn("获取MappedStatement失败.", e);
            return null;
        }
    }

    /**
     * 判断是否需要解密处理<br>
     * 逻辑：对象上是否有 @SensitiveData 注解
     */
    private boolean needToDecrypt(Object object) {
        Class<?> objectClass = object.getClass();
        SensitiveData sensitiveData = AnnotationUtils.findAnnotation(objectClass, SensitiveData.class);
        return Objects.nonNull(sensitiveData);
    }
}
