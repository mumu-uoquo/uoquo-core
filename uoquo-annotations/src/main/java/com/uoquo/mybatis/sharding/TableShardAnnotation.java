/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.mybatis.sharding;

import java.lang.annotation.*;

/**
 * 分表注解
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface TableShardAnnotation {
    /**
     * 待分表的表名
     */
    String tableName();

    /**
     * 分表策略
     */
    Class<? extends ITableShardStrategy> shardStrategy();

    /**
     * 分表条件key, 通过key去参数列表里取对应的值，作为分表条件处理
     */
    String[] shardParamKey() default {};

    /**
     * 是否按上月分表（例如：当前是3月，是否使用2月的表）
     */
    boolean lastMonth() default false;
}
