/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.param;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分页查询入参
 */
@Schema(description = "分页查询请求入参")
public class PageRequest {

    @Schema(description = "当前页码（从1开始）")
    private int pageNum;

    @Schema(description = "每页数量（默认10条）")
    private int pageSize;

    public int getPageNum() {
        return pageNum < 1 ? 1 : pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize < 1 ? 10 : pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public String toString() {
        return "PageRequest{" +
            "pageNum=" + pageNum +
            ", pageSize=" + pageSize +
            '}';
    }
}
