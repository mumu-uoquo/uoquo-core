/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.sqlparser;

import com.uoquo.web.mybatis.sqlparser.SqlDeParser;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SqlParserTest {

    private CCJSqlParserManager parserManager = new CCJSqlParserManager();// SQL语法解析器

    @Test
    public void testSqlParser() {
        String sql = "select * from table1 as tb1 where id = 1";

        System.out.println(sql);
        String newSql = buildCountSql(sql);
        System.out.println(newSql);
    }



    private String buildCountSql(String sql) {

        // 解析SQL
        Select select = null;
        try {
            select = (Select)parserManager.parse(new StringReader(sql));
        } catch (JSQLParserException e) {
            return oldBBuildCountSql(sql);
        }
        PlainSelect ps = (PlainSelect) select.getSelectBody();

        // 去除排序、分组等语句
        ps.setGroupByElement(null);
        ps.setOrderByElements(null);
        ps.setHaving(null);
        ps.setTop(null);
        ps.setLimit(null);

        if (ps.getDistinct() != null) {
            return "SELECT count(1) FROM ( " + deParserSql(select) + " ) mybatis_count_table_1900";
        } else {
            // 替换查询列为统计值
//            SelectExpressionItem newCountSelect = new SelectExpressionItem();
//            newCountSelect.setExpression(new Column(new Table(), "count(1)"));
            SelectItem<Column> newCountSelect = new SelectItem<>();
            newCountSelect.setExpression(new Column("COUNT(1)"));

            ps.setSelectItems(Arrays.asList(newCountSelect));

            return deParserSql(select);
        }
    }

    private String deParserSql(Select select) {
        StringBuilder stringBuffer = new StringBuilder();
        SqlDeParser deParser = new SqlDeParser(stringBuffer);
        deParser.visit(select);
        return stringBuffer.toString();
    }

    private String oldBBuildCountSql(String sql) {
        // 去除换行符等字符
        sql = sql.trim();
        sql = sql.replaceAll("\\n", " ");
        sql = sql.replaceAll("\\r", " ");
        sql = sql.replaceAll("\\t", " ");

        int idx = -1;
        // 去除select语句
        idx = sql.toLowerCase().indexOf(" from ");
        if (idx != -1) {
            sql = sql.substring(idx);
        }
        // 去除order by排序语句
        idx = sql.toLowerCase().indexOf(" order by");
        if (idx != -1) {
            sql = sql.substring(0, idx);
        }
        // 去除group on分组语句
        idx = sql.toLowerCase().indexOf(" group on");
        if (idx != -1) {
            sql = sql.substring(0, idx);
        }
        // 去除for update语句
        idx = sql.toLowerCase().indexOf(" for update");
        if (idx != -1) {
            sql = sql.substring(0, idx);
        }

        // 拼接统计SELECT
        return "SELECT count(1) " + sql;
    }
}
