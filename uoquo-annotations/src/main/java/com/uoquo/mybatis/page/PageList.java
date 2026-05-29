/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.mybatis.page;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * 描述：分页集合对象. <br>
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
public class PageList<E> extends ArrayList<E> {
    
    /**
     * serialVersionUID .
     */
    @Serial
    private static final long serialVersionUID = 8021975116706013012L;

    /**
     * 每页默认数据量（10条）.
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 当前页码（从1开始）.
     */
    private int pageNum = 1;
    
    /**
     * 每页数据量.
     */
    private int pageSize   = DEFAULT_PAGE_SIZE;
    
    /**
     * 总数据量.
     */
    private long total;
    
    /**
     * 总页数.
     */
    private int pages;

    /**
     * 是否有上一页.
     */
    private Boolean hasPrevPage = null;
    
    /**
     * 是否有下一页.
     */
    private Boolean hasNextPage = null;

    /**
     * 精准分页.
     */
    private transient boolean count = true;
    
    /**
     * 分页起始行.
     */
    private transient int bgnRow;
    
    /**
     * 分页结束行.
     */
    private transient int endRow;
    
    public PageList() {
        super();
    }
    
    /**
     * 构造函数.
     * @param pageNum  当前页码
     * @param pageSize 页面大小
     */
    public PageList(int pageNum, int pageSize) {
        this(pageNum, pageSize, true);
    }

    /**
     * 构造函数.
     * @param pageNum  当前页码
     * @param pageSize 页面大小
     * @param count    是否精准分页
     */
    public PageList(int pageNum, int pageSize, boolean count) {
        // 参数合理化处理
        if (pageNum <= 0) {
            pageNum = 1; // 默认第一页
        }
        if (pageSize <= 0) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        this.pageNum  = pageNum;
        this.pageSize = pageSize;
        this.count    = count;
        
        calculateBeginAndEndRow();
    }

    public PageList(List<? extends E> list) {
        if (list instanceof PageList) {
            PageList<E> page = (PageList<E>) list;
            this.pageNum  = page.getPageNum();
            this.pageSize = page.getPageSize();
            this.total = page.getTotal();
            this.pages = page.getPages();
            this.hasPrevPage = page.hasPrevPage();
            this.hasNextPage = page.hasNextPage();
            super.addAll(list);
        } else if (list != null) {
            this.pageSize = list.size();
            this.total = this.pageSize;
            this.pages = this.pageSize > 0 ? 1 : 0;
            this.hasPrevPage = false;
            this.hasNextPage = false;
            super.addAll(list);
        }
    }
    public static <E> PageList<E> of(List<? extends E> list) {
        return new PageList<E>(list);
    }
    public static <E, T> PageList<E> of(PageList<T> page, List<? extends E> data) {
        PageList<E> result = new PageList<E>();
        result.pageNum  = page.getPageNum();
        result.pageSize = page.getPageSize();
        result.total = page.getTotal();
        result.pages = page.getPages();
        result.hasPrevPage = page.hasPrevPage();
        result.hasNextPage = page.hasNextPage();
        result.addAll(data);
        return result;
    }
    
    /**
     * 计算起止行号.
     */
    private void calculateBeginAndEndRow() {
        this.bgnRow = (this.pageNum - 1) * this.pageSize;
        this.endRow = this.pageNum * this.pageSize;
        // 模糊查询时，比需要的多查询一条数据，便于判断是否有下一页
        if (!this.count) {
            this.endRow += 1;
            this.total   = -1;
        }
    }
    
    /**
     * 设置总数据量.<br>
     * 注：如果总数据量为负数，则会执行模糊分页操作
     * @param total 总数据量
     */
    public void setTotal(long total) {
        this.total = total;
        // 分页合理化，针对不合理的页码自动处理
        if (total < 0) {
            this.pages = 0;
            this.count = false;
        } else {
            this.pages = (int) Math.ceil((double) total /  this.pageSize);
            //this.pageNum = (this.pageNum > this.pages) ? (this.pages == 0) ? 1 : this.pages : this.pageNum;
            if (this.pageNum > this.pages) {
                if (this.pages == 0) {
                    this.pageNum = 1;
                } else {
                    this.pageNum = this.pages;
                }
            }
        }
        calculateBeginAndEndRow();
    }

    public long getTotal() {
        return total;
    }
    
    public List<E> getResult() {
        return this;
    }
    
    public int getPageNum() {
        return pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getPages() {
        return pages;
    }

    public boolean isCount() {
        return count;
    }

    public int getBgnRow() {
        return bgnRow;
    }

    public int getEndRow() {
        return endRow;
    }
    
    /**
     * 是否有下一页.
     * @return 下一页标识
     */
    public boolean hasNextPage() {
        if (hasNextPage != null) {
            return hasNextPage;
        }
        if (this.count) {
            // 精准分页，则根据页码判断
            this.hasNextPage = this.pages > this.pageNum;
        } else {
            // 模糊分页，则根据返回的数据量与页面大小判断
            this.hasNextPage = this.size() > this.pageSize;
        }
        return hasNextPage;
    }
    
    public void setHasNextPage(Boolean hasNextPage) {
        this.hasNextPage = hasNextPage;
    }
    
    /**
     * 是否有上一页.
     * @return 上一页标识
     */
    public boolean hasPrevPage() {
        if (hasPrevPage != null) {
            return hasPrevPage;
        }
        this.hasPrevPage = this.pageNum > 1;
        return hasPrevPage;
    }
    
    public void setHasPrevPage(Boolean hasPrevPage) {
        this.hasPrevPage = hasPrevPage;
    }

    /**
     * 放入查询结果.<br>
     * 注：放入前会计算是否有下一页
     */
    public void setResult(List<? extends E> list) {
        super.clear();
        // 精准分页，不处理
        if (this.count) {
            super.addAll(list);
            return;
        }
        // 模糊分页，计算是否有下一页
        // 数量不多于pageSize，说明已经是最后一页了
        if (list.size() <= this.pageSize) {
            this.hasNextPage = false;
            super.addAll(list);
            return;
        }
        // 数量多于pageSize，说明还有下一页数据
        List<E> result = new ArrayList<E>();
        for (int i = 0; i < this.pageSize; i++) {
            result.add(list.get(i));
        }
        this.hasNextPage = true;
        super.addAll(result);
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
        sb.append("\"total\":").append(this.total).append(",");
        sb.append("\"pages\":").append(this.pages).append(",");
        sb.append("\"hasPrevPage\":").append(this.hasPrevPage()).append(",");
        sb.append("\"hasNextPage\":").append(this.hasNextPage()).append(",");
        sb.append("\"result\":").append(super.toString());
        sb.append("}");
        return sb.toString();
    }

}
