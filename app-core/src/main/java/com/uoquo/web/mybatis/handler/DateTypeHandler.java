/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.mybatis.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 描述：将 Date 类型转为 Long 型时间戳，实现java的date转为时间戳存储. <br>
 * 说明：java的bean对象为Date类型，对应数据库字段为number类型. <br>
 * 备注：该转换仅做技术储备，暂时不启用!!<br>
 * 启用方法
 * <ul>
 *  <li>配置文件中添加：mybatis.typeHandlersPackage=com.uoquo.web.mybatis</li>
 *  <li>在 resultMap 的属性添加：typeHandler="com.uoquo.web.mybatis.handler.DateTypeHandler"</li>
 *  <li>在 POJO 的属性添加：@TypeHandler(DateTypeHandler.class) </li>
 * </ul>
 * 日期：2018-03-30 10:18 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-03-30     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
@MappedTypes(Date.class)
public class DateTypeHandler extends BaseTypeHandler<Date> {
    private final Logger log = LoggerFactory.getLogger(DateTypeHandler.class);

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Date parameter, JdbcType jdbcType)
            throws SQLException {
        log.debug("idx={}, param={}, type={}", i, parameter, jdbcType);
        ps.setLong(i, parameter == null ? 0 : parameter.getTime());
    }

    @Override
    public Date getNullableResult(ResultSet rs, String columnName) throws SQLException {
        long val = rs.getLong(columnName);
        log.debug("columnName={}, val={}", columnName, val);
        if (val == 0) {
            return null;
        }
        return new Date(val);
    }

    @Override
    public Date getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        long val = rs.getLong(columnIndex);
        log.debug("columnIndex={}, val={}", columnIndex, val);
        if (val == 0) {
            return null;
        }
        return new Date(val);
    }

    @Override
    public Date getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        long val = cs.getLong(columnIndex);
        log.debug("columnIndex={}, val={}", columnIndex, val);
        if (val == 0) {
            return null;
        }
        return new Date(val);
    }
}
