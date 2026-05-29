/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.mybatis.interceptor;

import com.uoquo.web.mybatis.page.Dialect;
import com.uoquo.web.mybatis.page.MSUtils;
import com.uoquo.web.mybatis.page.PageHelper;
import com.uoquo.mybatis.page.PageList;
import com.uoquo.web.mybatis.sqlparser.SqlDeParser;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.*;

/**
 * 分页拦截�?br/>
 * 采用 PageHelper 的形式，通过 ThreadLocal 传递分页参�?
 *
 * @author xuhz
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class PageInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(PageInterceptor.class);

    private static final List<ResultMapping> EMPTY_RESULT_MAPPING = Collections.emptyList();

    /**
     * additionalParameters 字段缓存（BoundSql 中的私有字段�?
     */
    private static final Field ADDITIONAL_PARAMETERS_FIELD;

    static {
        try {
            ADDITIONAL_PARAMETERS_FIELD = BoundSql.class.getDeclaredField("additionalParameters");
            ADDITIONAL_PARAMETERS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("无法获取 BoundSql.additionalParameters 字段", e);
        }
    }

    private volatile Dialect dialect;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long bgn = System.currentTimeMillis();
        PageList<?> page = PageHelper.getPage();
        Object[] args = invocation.getArgs();
        Executor executor = (Executor) invocation.getTarget();

        // 延迟初始化分页方言
        if (dialect == null) {
            synchronized (this) {
                if (dialect == null) {
                    MappedStatement ms = (MappedStatement) args[0];
                    dialect = new PageHelper().getDialect(ms);
                }
            }
        }

        try {
            if (page == null) {
                RowBounds rowBounds = (RowBounds) args[2];
                // rowBounds 为空或为初始值，说明不分�?
                if (rowBounds == null
                        || (rowBounds.getLimit() == RowBounds.NO_ROW_LIMIT && rowBounds.getOffset() == RowBounds.NO_ROW_OFFSET)) {
                    return executeWithoutPage(invocation, args, bgn);
                }
                // �?RowBounds 转换�?page
                int pageSize = rowBounds.getLimit();
                int pageNum = rowBounds.getOffset() / pageSize + 1;
                page = new PageList<>(pageNum, pageSize);
            }
            return processIntercept(page, executor, args);
        } catch (Exception e) {
            log.warn("分页查询异常.", e);
            throw e;
        } finally {
            PageHelper.clearPage();
            if (log.isDebugEnabled()) {
                log.debug("PageInterceptor 执行耗时={}ms", System.currentTimeMillis() - bgn);
            }
        }
    }

    /**
     * 非分页查询执�?
     */
    private Object executeWithoutPage(Invocation invocation, Object[] args, long bgn) throws Throwable {
        try {
            Object result = invocation.proceed();
            if (log.isDebugEnabled()) {
                log.debug("非分页查询耗时={}ms", System.currentTimeMillis() - bgn);
            }
            return result;
        } catch (Exception e) {
            MappedStatement ms = (MappedStatement) args[0];
            BoundSql boundSql = (args.length == 4)
                    ? ms.getBoundSql(args[1])
                    : (BoundSql) args[5];
            log.error("查询异常 MappedStatement:{}, SQL:{}", ms.getId(), boundSql.getSql(), e);
            throw e;
        }
    }

    /**
     * 分页查询处理
     */
    private Object processIntercept(PageList<?> page, Executor executor, final Object[] args) throws SQLException {
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];
        RowBounds rowBounds = (RowBounds) args[2];
        ResultHandler<?> resultHandler = (ResultHandler<?>) args[3];

        CacheKey cacheKey;
        BoundSql boundSql;
        if (args.length == 4) {
            boundSql = ms.getBoundSql(parameter);
            cacheKey = executor.createCacheKey(ms, parameter, rowBounds, boundSql);
        } else {
            cacheKey = (CacheKey) args[4];
            boundSql = (BoundSql) args[5];
        }

        Configuration configuration = ms.getConfiguration();
        Map<String, Object> additionalParameters = getAdditionalParameters(boundSql);

        // 执行 count 查询
        if (page.isCount()) {
            long count = executeCount(ms, parameter, boundSql, configuration, additionalParameters, executor, resultHandler);
            page.setTotal(count);
        }

        // 执行分页查询
        String pageSql = dialect.getPageSql(boundSql.getSql().trim(), page, cacheKey);
        BoundSql pageBoundSql = new BoundSql(configuration, pageSql, boundSql.getParameterMappings(), parameter);
        copyAdditionalParameters(pageBoundSql, additionalParameters);

        List<?> list = executor.query(ms, parameter, RowBounds.DEFAULT, resultHandler, cacheKey, pageBoundSql);
        setPageResult(page, list);
        return page;
    }

    /**
     * 执行 count 查询
     */
    private long executeCount(MappedStatement ms, Object parameter, BoundSql boundSql,
                              Configuration configuration, Map<String, Object> additionalParameters,
                              Executor executor, ResultHandler<?> resultHandler) throws SQLException {
        String countMsId = ms.getId() + "-COUNT_SIZE";
        String countSql = buildCountSql(boundSql.getSql());

        BoundSql countBoundSql = new BoundSql(configuration, countSql, boundSql.getParameterMappings(), parameter);
        copyAdditionalParameters(countBoundSql, additionalParameters);

        // 构建 count 用的 MappedStatement
        MappedStatement.Builder msBuilder = MSUtils.copyMappedStatement(countMsId, ms, countBoundSql);
        ResultMap resultMap = new ResultMap.Builder(configuration, countMsId, Long.class, EMPTY_RESULT_MAPPING).build();
        msBuilder.resultMaps(Collections.singletonList(resultMap));
        MappedStatement countMs = msBuilder.build();

        CacheKey countCacheKey = executor.createCacheKey(countMs, parameter, RowBounds.DEFAULT, countBoundSql);
        List<?> countResult = executor.query(countMs, parameter, RowBounds.DEFAULT, resultHandler, countCacheKey, countBoundSql);
        return (Long) countResult.getFirst();
    }

    /**
     * 复制 additionalParameters 到新�?BoundSql
     */
    private void copyAdditionalParameters(BoundSql targetBoundSql, Map<String, Object> additionalParameters) {
        for (Map.Entry<String, Object> entry : additionalParameters.entrySet()) {
            targetBoundSql.setAdditionalParameter(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 获取 BoundSql 中的 additionalParameters
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getAdditionalParameters(BoundSql boundSql) {
        try {
            return (Map<String, Object>) ADDITIONAL_PARAMETERS_FIELD.get(boundSql);
        } catch (Exception e) {
            throw new RuntimeException("获取 BoundSql.additionalParameters 失败", e);
        }
    }

    /**
     * 构建 COUNT SQL<br/>
     * 使用 JSQLParser 解析 SQL，去除排序分组后替换查询列为 COUNT(1)
     */
    private String buildCountSql(String sql) {
        try {
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            PlainSelect ps = select.getPlainSelect();
            if (ps == null) {
                // �?PlainSelect（如 UNION），包装为子查询
                return "SELECT COUNT(1) FROM (" + sql + ") mybatis_count_table";
            }

            // 去除排序、分组等
            ps.setGroupByElement(null);
            ps.setOrderByElements(null);
            ps.setHaving(null);
            ps.setTop(null);
            ps.setLimit(null);

            if (ps.getDistinct() != null) {
                return "SELECT COUNT(1) FROM (" + deParserSql(select) + ") mybatis_count_table";
            }

            // 替换查询列为 COUNT(1)
            SelectItem<Column> countItem = new SelectItem<>();
            countItem.setExpression(new Column("COUNT(1)"));
            ps.setSelectItems(Collections.singletonList(countItem));

            return deParserSql(select);
        } catch (Exception e) {
            log.warn("SQL解析失败，使用简版count: <{}>", sql, e);
            return fallbackBuildCountSql(sql);
        }
    }

    /**
     * 获取解析后的SQL语句
     */
    private String deParserSql(Select select) {
        StringBuilder sb = new StringBuilder();
        SqlDeParser deParser = new SqlDeParser(sb);
        deParser.visit(select);
        return sb.toString();
    }

    /**
     * 设置分页结果（解决泛型通配符类型安全问题）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setPageResult(PageList page, List<?> list) {
        page.setResult(list);
    }

    /**
     * 简�?count SQL 构建（解析失败时的兜底）
     */
    private String fallbackBuildCountSql(String sql) {
        sql = sql.replaceAll("[\\s]+", " ").trim();
        int fromIdx = sql.toLowerCase().indexOf(" from ");
        if (fromIdx == -1) {
            return "SELECT COUNT(1) FROM (" + sql + ") mybatis_count_table";
        }
        String fromPart = sql.substring(fromIdx);
        // 去除 order by
        int orderIdx = fromPart.toLowerCase().indexOf(" order by");
        if (orderIdx != -1) {
            fromPart = fromPart.substring(0, orderIdx);
        }
        // 去除 for update
        int forUpdateIdx = fromPart.toLowerCase().indexOf(" for update");
        if (forUpdateIdx != -1) {
            fromPart = fromPart.substring(0, forUpdateIdx);
        }
        return "SELECT COUNT(1)" + fromPart;
    }
}
