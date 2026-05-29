/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.mybatis.sharding;

import com.uoquo.mybatis.sharding.ITableShardStrategy;
import com.uoquo.utils.crypto.ULID;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * 按月分表,根据当前时间
 */
public class MonthTableShardStrategy implements ITableShardStrategy {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final ObjectFactory DEFAULT_OBJECT_FACTORY = new DefaultObjectFactory();
    private static final ObjectWrapperFactory DEFAULT_OBJECT_WRAPPER_FACTORY = new DefaultObjectWrapperFactory();
    private static final ReflectorFactory REFLECTOR_FACTORY = new DefaultReflectorFactory();

    @Override
    public String tableShard(MetaObject metaStatementHandler, String tableName, String[] shardParamKey, boolean lastMonth) {
        String alarmOccurTimeParamKey = shardParamKey[0];
        Object paramKey = null;
        Object parameterObject = metaStatementHandler.getValue("delegate.boundSql.parameterObject");//获取参数
        if (parameterObject instanceof String || parameterObject instanceof Date) {
            // 参数是一个String或Date,那我们就认为这个就是用来分表的参数了
            paramKey =  parameterObject;
        } else if (parameterObject instanceof Map) {
            // 参数是一个Map
            Map<String, Object> map = (Map<String, Object>) parameterObject;
            Set<String> set = map.keySet();
            for (String key : set) {
                if (key.equals(alarmOccurTimeParamKey)) {
                    paramKey = map.get(alarmOccurTimeParamKey);
                    break;
                }
            }
        } else {
            // 参数为某个对象
            MetaObject metaParamObject = MetaObject.forObject(parameterObject, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY, REFLECTOR_FACTORY);
            paramKey = metaParamObject.getValue(alarmOccurTimeParamKey);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMM");
        String newTableName;
        if (null == paramKey) {
            newTableName = tableName + "_" + sdf.format(new Date());
        } else {
            Date paramKeyDate;
            if (paramKey instanceof Date) {
                paramKeyDate =  (Date) paramKey;
            } else {
                long timeBySeqGuid = ULID.parse(String.valueOf(paramKey)).getTimestamp();
                paramKeyDate = new Date(timeBySeqGuid);
            }
            //如果是当月第一天，且查上个月标识为true
            if (lastMonth) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(paramKeyDate);
                cal.add(Calendar.MONTH, -1);
                newTableName = tableName + "_" + sdf.format(cal.getTime());
                cal.clear();
            } else {
                newTableName = tableName + "_" + sdf.format(paramKeyDate);
            }
        }
        BoundSql boundSql = (BoundSql) metaStatementHandler.getValue("delegate.boundSql");
        String originSql = boundSql.getSql();
        return originSql.replaceAll(tableName, newTableName);
    }
}
