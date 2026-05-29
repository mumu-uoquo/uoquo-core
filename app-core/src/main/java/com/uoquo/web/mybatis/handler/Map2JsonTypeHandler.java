/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.mybatis.handler;

import com.uoquo.utils.json.JsonUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * JSON 类型处理器（转换为MAP对象）
 * @author uoquo
 */
@MappedTypes({Map.class})
@MappedJdbcTypes(JdbcType.OTHER)
public class Map2JsonTypeHandler extends BaseTypeHandler<Map<String, ?>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, ?> parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, JsonUtil.serialize(parameter));
        } catch (Exception e) {
            throw new SQLException("Failed to convert JSON to String: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, ?> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getString(columnName));
    }

    @Override
    public Map<String, ?> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    @Override
    public Map<String, ?> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    private Map<String, ?> parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            Map<String, ?> deserialize = JsonUtil.deserialize(json, Map.class);
            return deserialize;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON: " + e.getMessage(), e);
        }
    }
}