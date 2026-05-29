/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.codegen.openapi;

import java.util.List;

/**
 * 入参、出参对象定义
 */
public class ServiceTag {

    /**
     * 名称
     */
    private String name;
    /**
     * 描述
     */
    private String description;
    /**
     * 属性列表
     */
    private List<ServiceMethod> services;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ServiceMethod> getServices() {
        return services;
    }

    public void setServices(List<ServiceMethod> services) {
        this.services = services;
    }
}
