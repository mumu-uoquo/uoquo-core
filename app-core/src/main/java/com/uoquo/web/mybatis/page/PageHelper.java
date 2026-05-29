/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.mybatis.page;

import com.uoquo.mybatis.page.PageList;
import com.uoquo.web.mybatis.page.dialect.MySQLDialect;
import com.uoquo.web.mybatis.page.dialect.OracleDialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;

import org.apache.ibatis.mapping.MappedStatement;

/**
 * 描述：分页处理. <br>
 * 参考：https://gitee.com/free/Mybatis_PageHelper<br>
 * 使用：
 * <ul>
 *   <li><b>精准分页</b><br>
 *   PageHelper.startPage(page, pageSize);<br>
 *   或<br>
 *   PageHelper.startPage(page, pageSize, true);
 *   </li>
 *   <li><b>模糊分页</b><br>
 *   PageHelper.startPage(page, pageSize, false);
 *   </li>
 * </ul>
 * 示例：
 * <pre>
 * <b>Mapper中定义分页查询接口：</b>
 * public interface XXXXDateMapper {
 *     PageList&lt;XXXX&gt; getPageListByMap(Map map);
 * }
 * 
 * <b>Service实现中：</b>
 * public PageList&lt;XXXX&gt; getListByPage(Map map, int page, int pageSize) {
 *     PageHelper.startPage(page, pageSize, false);
 *     return XXXXDateMapper.getPageListByMap(map);
 * }
 * </pre>
 * 日期：2018-04-02 11:24 <br>
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
public class PageHelper {

    // 缓存分页Dialect
    private final Map<String,  Dialect> urlDialectMap = new ConcurrentHashMap<String, Dialect>();
    
    // 分页Dialect
    private static final Map<String, Class<?>> dialectAliasMap = new HashMap<String, Class<?>>();
    
    // 注册分页别名
    static {
        dialectAliasMap.put("mysql",  MySQLDialect.class);
        dialectAliasMap.put("oracle", OracleDialect.class);
    }
    
    // 当前分页对象
    protected static final ThreadLocal<PageList<?>> LOCAL_PAGE = new ThreadLocal<PageList<?>>();

    /**
     * 设置 Page 参数 .
     * @param page 分页对象
     */
    protected static <E> void setPage(PageList<E> page) {
        LOCAL_PAGE.set(page);
    }

    /**
     * 获取 Page 参数 .
     */
    public static PageList<?> getPage() {
        return LOCAL_PAGE.get();
    }

    /**
     * 移除本地变量 .
     */
    public static void clearPage() {
        LOCAL_PAGE.remove();
    }
    
    /**
     * 精准分页，会执行 count 操作.
     * @param pageNum  页码
     * @param pageSize 每页显示数量
     */
    public static <E> PageList<E> startPage(int pageNum, int pageSize) {
        return startPage(pageNum, pageSize, true);
    }

    /**
     * 开始分页.
     * @param pageNum  页码
     * @param pageSize 每页显示数量
     * @param count    是否进行count查询（默认true，false的时候则执行模糊分页）
     */
    public static <E> PageList<E> startPage(int pageNum, int pageSize, Boolean count) {
        clearPage();
        count = count == null || count;
        PageList<E> page = new PageList<E>(pageNum, pageSize, count);
        setPage(page);
        return page;
    }

    /**
     * 获取分页Dialect .
     * @param ms 现有ms对象
     * @return 分页Dialect
     */
    public Dialect getDialect(MappedStatement ms) {
        DataSource dataSource = ms.getConfiguration().getEnvironment().getDataSource();
        try {
            // JDBC的URL
            String url = getUrl(dataSource).toLowerCase();
            if (urlDialectMap.containsKey(url)) {
                return urlDialectMap.get(url);
            }
            // 从URL中提取数据库类型
            String dialectStr = fromJdbcUrl(url);
            Class<?> sqlDialectClass = dialectAliasMap.get(dialectStr);
            Dialect dialect = (Dialect) sqlDialectClass.getDeclaredConstructor().newInstance();
            urlDialectMap.put(url, dialect);
            return dialect;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取JDBC的URL.
     * @param dataSource 数据库信息
     * @return 连接URL
     * @throws SQLException 异常信息
     */
    private String getUrl(DataSource dataSource) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getURL();
        }
    }
    
    /**
     * 提取JDBC连接中的数据库类型.
     * @param jdbcUrl 数据库连接的URL
     */
    private String fromJdbcUrl(String jdbcUrl) {
        jdbcUrl = jdbcUrl.toLowerCase();
        for (String dialect : dialectAliasMap.keySet()) {
            dialect = dialect.toLowerCase();
            if (jdbcUrl.contains(":" + dialect + ":")) {
                return dialect;
            }
        }
        return null;
    }
}
