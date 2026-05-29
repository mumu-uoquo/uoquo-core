/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.mybatis.sqlparser;

import java.util.Iterator;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.StatementDeParser;

/**
 * 描述：重构SQL语句. <br>
 * 背景：去除表别名前的AS，防止ORACLE中出错<br>
 * 日期：2018-04-02 16:34 <br>
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
public class SqlDeParser extends StatementDeParser {

    public SqlDeParser(StringBuilder buffer) {
        super(buffer);
    }

    /**
     * 生成查询语句.<br>
     */
    @Override
    public void visit(Select select) {
        //SelectDeParser selectDeParser = new SelectDeParser();
        TableAliasDeParser selectDeParser = new TableAliasDeParser();
        selectDeParser.setBuilder(getBuilder());
        ExpressionDeParser expressionDeParser = new ExpressionDeParser(selectDeParser, getBuilder());
        selectDeParser.setExpressionVisitor(expressionDeParser);
        if (select.getWithItemsList() != null && !select.getWithItemsList().isEmpty()) {
            getBuilder().append("WITH ");
            for (Iterator<WithItem<?>> iter = select.getWithItemsList().iterator(); iter.hasNext();) {
                WithItem<?> withItem = iter.next();
                getBuilder().append(withItem);
                if (iter.hasNext()) {
                    getBuilder().append(",");
                }
                getBuilder().append(" ");
            }
        }
        select.accept(selectDeParser);
    }
}
