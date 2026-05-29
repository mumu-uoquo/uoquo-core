/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.mybatis.page;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 描述：分页返回对象. <br>
 * 说明：查询的分页数据组装到该类中.<br>
 * 日期：2018-01-30 13:14 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-30     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
@Schema(description = "分页信息")
public class PageResult<E> {

    /**
     * 当前页码（从1开始）.
     */
    @Schema(name = "pageNum", description = "当前页码（从1开始）")
    private int pageNum = 1;

    /**
     * 每页数据量.
     */
    @Schema(name = "pageSize", description = "每页数据量")
    private int pageSize   = PageList.DEFAULT_PAGE_SIZE;

    /**
     * 当前页数量.
     */
    @Schema(name = "size", description = "当前页数量")
    private int size;

    /**
     * 总数据量.
     */
    @Schema(name = "total", description = "总数据量")
    private long total;

    /**
     * 总页数.
     */
    @Schema(name = "pages", description = "总页数")
    private int pages;

    /**
     * 是否有上一页.
     */
    @Schema(name = "prevPage", description = "是否有上一页")
    private boolean prevPage = false;

    /**
     * 是否有下一页.
     */
    @Schema(name = "nextPage", description = "是否有下一页")
    private boolean nextPage = false;

    /**
     * 数据集（继承父类）.
     */
    @Schema(name = "result", description = "数据集")
    private List<E> result = new ArrayList<E>();

    /**
     * 构造函数（主要用于反序列化）
     */
    public PageResult() {
    }

    /**
     * 构造函数.
     */
    public PageResult(List<? extends E> list) {
        if (list instanceof PageList) {
            PageList<E> page = (PageList<E>) list;
            this.pageNum  = page.getPageNum();
            this.pageSize = page.getPageSize();
            this.size  = page.size();
            this.total = page.getTotal();
            this.pages = page.getPages();
            this.prevPage = page.hasPrevPage();
            this.nextPage = page.hasNextPage();
            this.result.addAll(list);
        } else if (list != null) {
            this.pageSize = list.size();
            this.size  = this.pageSize;
            this.total = this.pageSize;
            this.pages = this.pageSize > 0 ? 1 : 0;
            this.prevPage = false;
            this.nextPage = false;
            this.result.addAll(list);
        }
    }

    public static <E> PageResult<E> of(List<? extends E> list) {
        return new PageResult<E>(list);
    }

    public static <E, T> PageResult<E> of(PageList<T> page, List<? extends E> data) {
        PageResult<E> result = new PageResult<E>(data);
        result.pageNum  = page.getPageNum();
        result.pageSize = page.getPageSize();
        result.total = page.getTotal();
        result.pages = page.getPages();
        result.prevPage = page.hasPrevPage();
        result.nextPage = page.hasNextPage();
        return result;
    }

    /**
     * 返回一个空对象
     */
    public static <E> PageResult<E> empty() {
        return new PageResult<E>(Collections.emptyList());
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    public boolean isPrevPage() {
        return prevPage;
    }

    public void setPrevPage(boolean prevPage) {
        this.prevPage = prevPage;
    }

    public boolean isNextPage() {
        return nextPage;
    }

    public void setNextPage(boolean nextPage) {
        this.nextPage = nextPage;
    }

    public List<E> getResult() {
        return result;
    }

    public void setResult(List<E> result) {
        this.result = result;
    }

    /**
     * 格式化为JSON字符串.
     * @return JSON字符串
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"pageNum\":").append(this.pageNum).append(",");
        sb.append("\"pageSize\":").append(this.pageSize).append(",");
        sb.append("\"size\":").append(this.size).append(",");
        sb.append("\"total\":").append(this.total).append(",");
        sb.append("\"pages\":").append(this.pages).append(",");
        sb.append("\"prevPage\":").append(this.prevPage).append(",");
        sb.append("\"nextPage\":").append(this.nextPage).append(",");
        sb.append("\"result\":").append(this.result.toString());
        sb.append("}");
        return sb.toString();
    }

}
