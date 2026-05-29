/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.mybatis.page.dialect;

import com.uoquo.web.mybatis.page.Dialect;

import com.uoquo.mybatis.page.PageList;
import org.apache.ibatis.cache.CacheKey;

/**
 * 描述：Oracle分页. <br>
 * 日期�?018-04-02 15:15 <br>
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
public class OracleDialect extends Dialect {

    @Override
    public String getPageSql(String sql, PageList<?> page, CacheKey cacheKey) {
        // �?for update 单独拼接
        boolean isForUpdate = false;
        if (sql.toLowerCase().endsWith(" for update")) {
            sql = sql.substring(0, sql.length() - 11);
            isForUpdate = true;
        }
        // 拼接分页语句
        StringBuilder sqlBuilder = new StringBuilder(sql.length() + 200);
        sqlBuilder.append("SELECT * FROM ( ");
        if (page.getBgnRow() > 0) {
            sqlBuilder.append(" SELECT TMP_PAGE.*, ROWNUM ROW_NUM FROM ( ");
        }
        sqlBuilder.append(sql);
        sqlBuilder.append(" ) TMP_PAGE WHERE ROWNUM <= ").append(page.getEndRow());
        if (page.getBgnRow() > 0) {
            sqlBuilder.append(" ) WHERE ROW_ID > ").append(page.getBgnRow());
        }
        
        // 拼接 for update
        if (isForUpdate) {
            sqlBuilder.append(" FOR UPDATE");
        }
        return sqlBuilder.toString();
    }
}
