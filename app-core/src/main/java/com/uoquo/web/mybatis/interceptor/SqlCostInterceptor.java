/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.mybatis.interceptor;

import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.DateUtil;
import com.uoquo.utils.json.JsonUtil;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Field;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Matcher;

/**
 * SQL执行时长记录插件<br/>
 * 记录慢SQL并支持debug模式下输出完整SQL
 *
 * @author xuhz
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
        @Signature(type = StatementHandler.class, method = "batch", args = {Statement.class})
})
public class SqlCostInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(SqlCostInterceptor.class);

    @Value("${app.db.slow-sql:500}")
    private int timeout;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startTime = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long sqlCost = System.currentTimeMillis() - startTime;
            if (log.isWarnEnabled() && sqlCost >= timeout) {
                String sql = formatSql((StatementHandler) invocation.getTarget());
                log.warn("执行SQL耗时[{}ms]超过[{}ms]：[{}], user={}", sqlCost, timeout, sql, JsonUtil.serialize(CurrentUser.getInfo()));
            } else if (log.isDebugEnabled()) {
                String sql = formatSql((StatementHandler) invocation.getTarget());
                log.debug("执行SQL耗时[{}ms]：[{}]", sqlCost, sql);
            }
        }
    }

    /**
     * 格式化SQL，替换占位符为实际参数值
     */
    private String formatSql(StatementHandler statementHandler) {
        BoundSql boundSql = statementHandler.getBoundSql();
        Object parameterObject = boundSql.getParameterObject();
        List<ParameterMapping> parameterMappingList = boundSql.getParameterMappings();

        String sql = beautifySql(boundSql.getSql());

        // 无参数直接返回
        if (parameterObject == null || parameterMappingList == null || parameterMappingList.isEmpty()) {
            return appendSemicolon(sql);
        }

        String originalSql = sql;
        try {
            Class<?> parameterObjectClass = parameterObject.getClass();

            if (parameterObject instanceof Map<?, ?> paramMap) {
                // 处理 <foreach> 传入 List 的场景
                Object listParam = paramMap.get("list");
                if (listParam instanceof Collection<?> col) {
                    sql = handleListParameter(sql, col);
                } else {
                    sql = handleMapParameter(sql, paramMap, parameterMappingList);
                }
            } else {
                sql = handleCommonParameter(sql, parameterMappingList, parameterObjectClass, parameterObject);
            }
            return appendSemicolon(sql);
        } catch (Exception e) {
            // 替换失败返回格式化后的原始SQL
            return appendSemicolon(originalSql);
        }
    }

    /**
     * 美化SQL：去除换行符、多余空白
     */
    private String beautifySql(String sql) {
        return sql.replaceAll("[\\s]+", " ").trim()
                .replace("( ", "(")
                .replace(" )", ")")
                .replace(" ,", ",");
    }

    /**
     * 处理参数为List的场景（foreach）
     */
    private String handleListParameter(String sql, Collection<?> col) {
        if (col == null || col.isEmpty()) {
            return sql;
        }
        for (Object obj : col) {
            String value = formatValue(obj);
            sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(value));
        }
        return sql;
    }

    /**
     * 处理参数为Map的场景
     */
    private String handleMapParameter(String sql, Map<?, ?> paramMap, List<ParameterMapping> parameterMappingList) {
        for (ParameterMapping parameterMapping : parameterMappingList) {
            Object propertyName = parameterMapping.getProperty();
            Object propertyValue = paramMap.get(propertyName);
            String value = formatValue(propertyValue);
            sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(value));
        }
        return sql;
    }

    /**
     * 处理通用场景（POJO、基本类型）
     */
    private String handleCommonParameter(String sql, List<ParameterMapping> parameterMappingList,
                                         Class<?> parameterObjectClass, Object parameterObject) {
        for (ParameterMapping parameterMapping : parameterMappingList) {
            String value;
            if (isPrimitiveOrWrapper(parameterObjectClass) || parameterObjectClass == String.class) {
                // 基本类型或String，直接取值
                value = formatValue(parameterObject);
            } else {
                // POJO：通过反射获取字段值（支持继承链）
                String propertyName = parameterMapping.getProperty();
                Object fieldValue = getFieldValue(parameterObjectClass, parameterObject, propertyName);
                value = formatValue(fieldValue);
            }
            sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(value));
        }
        return sql;
    }

    /**
     * 格式化参数值为可直接执行的SQL字面量
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof String strVal) {
            // SQL 字符串用单引号，内部单引号转义为 ''
            return "'" + strVal.replace("'", "''") + "'";
        }
        if (value instanceof Date) {
            return "'" + DateUtil.toString((Date) value, DateUtil.FORMAT_DATE_TIME) + "'";
        }
        if (value instanceof Boolean boolVal) {
            return boolVal ? "1" : "0";
        }
        return value.toString();
    }

    /**
     * 获取对象字段值（支持父类继承链）
     */
    private Object getFieldValue(Class<?> clazz, Object obj, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(obj);
                field.setAccessible(false);
                return value;
            } catch (NoSuchFieldException e) {
                // 当前类没有，向父类查找
                current = current.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 判断是否基本类型或其包装类
     */
    private boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive()
                || Number.class.isAssignableFrom(clazz)
                || Character.class == clazz
                || Boolean.class == clazz;
    }

    /**
     * 追加分号，确保SQL可直接复制执行
     */
    private String appendSemicolon(String sql) {
        if (sql != null && !sql.endsWith(";")) {
            return sql + ";";
        }
        return sql;
    }
}
