/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.param;


import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 公用ID入参
 */
@Schema(description = "ID信息")
public class IdParam {

    @Schema(description = "主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    public IdParam() {
    }

    public IdParam(String id) {
        this.id = id;
    }

    public String getId() {
        return id;

    }

    public void setId(String id) {
        this.id = id;
    }
}
