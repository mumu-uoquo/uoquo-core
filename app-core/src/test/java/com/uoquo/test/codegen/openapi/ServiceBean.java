/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.codegen.openapi;

import java.util.List;

/**
 * 入参、出参对象定义
 */
public class ServiceBean {

    /**
     * 名称（可能为空）
     */
    private String name;
    /**
     * 描述
     */
    private String description;
    /**
     * 类型
     */
    private String type;
    /**
     * 包路径
     */
    private String packagePath;
    /**
     * 属性列表
     */
    private List<ServiceProperty> properties;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPackagePath() {
        return packagePath;
    }

    public void setPackagePath(String packagePath) {
        this.packagePath = packagePath;
    }

    public List<ServiceProperty> getProperties() {
        return properties;
    }

    public void setProperties(List<ServiceProperty> properties) {
        this.properties = properties;
    }
}
