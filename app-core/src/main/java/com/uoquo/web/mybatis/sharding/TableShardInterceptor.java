/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.mybatis.sharding;

import com.uoquo.mybatis.sharding.ITableShardStrategy;
import com.uoquo.mybatis.sharding.TableShardAnnotation;
import com.uoquo.utils.StringUtil;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.Properties;

/**
 * mybatis分表拦截器 -- 水平切分
 */
@Intercepts({
        @Signature(
                type = StatementHandler.class,
                method = "prepare",
                args = {Connection.class, Integer.class}
        )
})
public class TableShardInterceptor implements Interceptor {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final ObjectFactory DEFAULT_OBJECT_FACTORY = new DefaultObjectFactory();
    private static final ObjectWrapperFactory DEFAULT_OBJECT_WRAPPER_FACTORY = new DefaultObjectWrapperFactory();
    private static final ReflectorFactory REFLECTOR_FACTORY = new DefaultReflectorFactory();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (invocation.getTarget() instanceof RoutingStatementHandler statementHandler) {
            try {
                // MetaObject是mybatis里面提供的一个工具类，类似反射的效果
                MetaObject metaStatementHandler = MetaObject.forObject(statementHandler, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY, REFLECTOR_FACTORY);
                BoundSql boundSql = (BoundSql) metaStatementHandler.getValue("delegate.boundSql");//获取sql语句
                String originSql = boundSql.getSql();
                if (StringUtil.notNull(originSql)) {
                    MappedStatement mappedStatement = (MappedStatement) metaStatementHandler.getValue("delegate.mappedStatement");
                    // 判断方法上是否添加了 TableShardAnnotation 注解，因为只有添加了TableShard注解的方法我们才会去做分表处理
                    TableShardAnnotation tableShardAnnotation = getTableShardAnnotation(mappedStatement);
                    if (tableShardAnnotation != null) {
                        String tableName = tableShardAnnotation.tableName();
                        Class<?> strategyClazz = tableShardAnnotation.shardStrategy();
                        String[] shardParamKey = tableShardAnnotation.shardParamKey();
                        boolean lastMonth = tableShardAnnotation.lastMonth();
                        Constructor<?> con1 = strategyClazz.getDeclaredConstructor();
                       ITableShardStrategy tableStrategy = (ITableShardStrategy) con1.newInstance();
                        String newSql = tableStrategy.tableShard(metaStatementHandler, tableName, shardParamKey, lastMonth);
                        // 把新语句设置回去
                        metaStatementHandler.setValue("delegate.boundSql.sql", newSql);
                    }

                }
            } catch (Exception e) {
                // ignore 任何一个地方有异常都去执行原始操作 -- invocation.proceed()
                log.error("拦截器异常.",e);
            }

        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        // 当目标类是StatementHandler类型时，才包装目标类，否者直接返回目标本身,减少目标被代理的次数
        return (target instanceof RoutingStatementHandler) ? Plugin.wrap(target, this) : target;
    }

    @Override
    public void setProperties(Properties properties) {

    }

    /**
     * 获取方法上的TableShardAnnotation注解
     *
     * @param mappedStatement MappedStatement
     * @return TableShardAnnotation注解
     */
    private TableShardAnnotation getTableShardAnnotation(MappedStatement mappedStatement) {
        TableShardAnnotation tableShardAnnotation = null;
        try {
            String id = mappedStatement.getId();
            String className = id.substring(0, id.lastIndexOf("."));
            String methodName = id.substring(id.lastIndexOf(".") + 1);
            final Method[] method = Class.forName(className).getMethods();
            for (Method me : method) {
                if (me.getName().equals(methodName) && me.isAnnotationPresent(TableShardAnnotation.class)) {
                    tableShardAnnotation = me.getAnnotation(TableShardAnnotation.class);
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("获取TableShardAnnotation注解失败！", ex);
        }
        return tableShardAnnotation;
    }
}
