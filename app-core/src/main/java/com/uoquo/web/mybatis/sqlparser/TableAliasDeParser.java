/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.mybatis.sqlparser;

import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.ExpressionVisitor;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Pivot;
import net.sf.jsqlparser.util.deparser.SelectDeParser;

/**
 * 描述：SQL解析器，去除表别名前的AS. <br>
 * 说明：重写visit(Table)，表别名前不加AS，防止Oracle中出错. <br>
 * 日期：2018-04-02 16:37 <br>
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
public class TableAliasDeParser extends SelectDeParser {

    public TableAliasDeParser() {
        // do nothing
    }
    
    public TableAliasDeParser(ExpressionVisitor expressionVisitor, StringBuilder buffer) {
        super(expressionVisitor, buffer);
    }
    
    /**
     * 拼接表名.<br>
     * @param tableName 表名
     */
    @Override
    public void visit(Table tableName) {
        getBuilder().append(tableName.getFullyQualifiedName());
        Pivot pivot = tableName.getPivot();
        if (pivot != null) {
            pivot.accept(this, this);
        }
        Alias alias = tableName.getAlias();
        if (alias != null) {
            if (alias.isUseAs()) {
                getBuilder().append(" AS ").append(alias);
            } else {
                getBuilder().append(" ").append(alias);
            }
        }
    }
}
