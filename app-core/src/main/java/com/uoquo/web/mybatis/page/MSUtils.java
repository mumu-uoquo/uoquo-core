/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.mybatis.page;

import com.uoquo.utils.StringUtil;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

/**
 * 描述：Mybatis对象操作. <br>
 * 日期：2018-04-02 16:08 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-04-02     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class MSUtils {
    
    /**
     * 新建MappedStatement.Builder对象.<br>
     * @param ms 现有ms对象
     * @return 新的ms对象
     */
    public static MappedStatement.Builder copyMappedStatement(MappedStatement ms) {
        return copyMappedStatement(ms.getId(), ms, ms.getSqlSource());
    }
    
    /**
     * 新建MappedStatement.Builder对象.<br>
     * @param ms       现有ms对象
     * @param boundSql 新SQL（替换ms中的sql）
     * @return 新的ms对象
     */
    public static MappedStatement.Builder copyMappedStatement(MappedStatement ms, BoundSql boundSql) {
        return copyMappedStatement(ms.getId(), ms, boundSql);
    }
    
    /**
     * 新建MappedStatement.Builder对象.<br>
     * @param msId     新对象的ID
     * @param ms       现有ms对象
     * @param boundSql 新SQL（替换ms中的sql）
     * @return 新的ms对象
     */
    public static MappedStatement.Builder copyMappedStatement(String msId, MappedStatement ms, BoundSql boundSql) {
        SqlSource sqlSource = new MSUtils.BoundSqlSqlSource(boundSql);
        return copyMappedStatement(msId, ms, sqlSource);
    }
    
    /**
     * 新建MappedStatement.Builder对象.<br>
     * @param msId     新对象的ID
     * @param ms       现有ms对象
     * @param sqlSource 新SQL（替换ms中的sql）
     * @return 新的ms对象
     */
    public static MappedStatement.Builder copyMappedStatement(String msId, MappedStatement ms, SqlSource sqlSource) {
        if (StringUtil.isNull(msId)) {
            msId = ms.getId();
        }
        MappedStatement.Builder builder = new MappedStatement.Builder(ms.getConfiguration(), msId, sqlSource, ms.getSqlCommandType());
        
        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.databaseId(ms.getDatabaseId());
        builder.keyGenerator(ms.getKeyGenerator());
        // 查询参数
        if (ms.getKeyProperties() != null && ms.getKeyProperties().length != 0) {
            StringBuilder keyProperties = new StringBuilder();
            for (String keyProperty : ms.getKeyProperties()) {
                keyProperties.append(keyProperty).append(",");
            }
            keyProperties.delete(keyProperties.length() - 1, keyProperties.length());
            builder.keyProperty(keyProperties.toString());
        }
        // 查询列
        if (ms.getKeyColumns() != null && ms.getKeyColumns().length != 0) {
            StringBuilder keyColumns = new StringBuilder();
            for (String keyColumn : ms.getKeyColumns()) {
                keyColumns.append(keyColumn).append(",");
            }
            keyColumns.delete(keyColumns.length() - 1, keyColumns.length());
            builder.keyColumn(keyColumns.toString());
        }
        
        //setStatementTimeout()
        builder.timeout(ms.getTimeout());
        
        //setStatementResultMap()
        builder.parameterMap(ms.getParameterMap());
        
        //setStatementResultMap()
        builder.resultMaps(ms.getResultMaps());
        builder.resultSetType(ms.getResultSetType());
        
        //setStatementCache()
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());
        
        return builder;
    }
    
    /**
     * SqlSource对象.
     */
    public static class BoundSqlSqlSource implements SqlSource {
        BoundSql boundSql;
        
        /**
         * 构造函数 .
         * @param boundSql SQL
         */
        public BoundSqlSqlSource(BoundSql boundSql) {
            this.boundSql = boundSql;
        }
        
        /**
         * 获取SQL.
         * @see org.apache.ibatis.mapping.SqlSource#getBoundSql(java.lang.Object)
         */
        @Override
        public BoundSql getBoundSql(Object parameterObject) {
            return boundSql;
        }
    }
}
