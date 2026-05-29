/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.mybatis.sharding;

import org.apache.ibatis.reflection.MetaObject;

/**
 * 分表策略
 */
public interface ITableShardStrategy {

    /**
     * 分表策略
     *
     * @param metaStatementHandler MetaObject包装的RoutingStatementHandler对象
     * @param tableName            原始表名
     * @param shardParamKey        可以在mapper文件的方法里面传递一些参数key过来，在分表策略里面通过key获取到对应的值
     * @return 包装之后的sql语句
     * @throws Exception
     */
    String tableShard(MetaObject metaStatementHandler, String tableName, String[] shardParamKey, boolean lastMonth) throws Exception;

}
