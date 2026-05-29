/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.mybatis.page;

import com.uoquo.mybatis.page.PageList;
import org.apache.ibatis.cache.CacheKey;

/**
 * 描述：分页方言. <br>
 * 日期：2018-04-02 14:49 <br>
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
public abstract class Dialect {
    
    /**
     * 实际拼装分页部分.
     * @param sql      SQL语句
     * @param page     分页对象
     * @param cacheKey 缓存对象
     */
    public abstract String getPageSql(String sql, PageList<?> page, CacheKey cacheKey);
}
