/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.mybatis.page.dialect;

import com.uoquo.web.mybatis.page.Dialect;

import com.uoquo.mybatis.page.PageList;
import org.apache.ibatis.cache.CacheKey;

/**
 * 描述：MySQL分页. <br>
 * 日期�?018-04-02 15:03 <br>
 * 变更�?
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-04-02     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class MySQLDialect extends Dialect {

    @Override
    public String getPageSql(String sql, PageList<?> page, CacheKey cacheKey) {
        StringBuilder sqlBuilder = new StringBuilder(sql.length() + 100);
        // 模糊查询时，分页参数多返回一�?
        int pageSize = page.isCount() ? page.getPageSize() : page.getPageSize() + 1;
        sqlBuilder.append(sql);
        if (page.getBgnRow() == 0) {
            sqlBuilder.append(" LIMIT ");
            sqlBuilder.append(pageSize);
        } else {
            sqlBuilder.append(" LIMIT ");
            sqlBuilder.append(page.getBgnRow());
            sqlBuilder.append(" , ");
            sqlBuilder.append(pageSize);
        }
        //cacheKey.update(page.getPageSize());
        return sqlBuilder.toString();
    }
}
