/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.codegen.openapi;


/**
 * 入参、出参对象的属性定义
 */
public class ServiceProperty {

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
     * 是否必须
     */
    private boolean required;

    /**
     * 子类型（当type=array时，items为子类型）
     */
    private String items;

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

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getItems() {
        return items;
    }

    public void setItems(String items) {
        this.items = items;
    }
}
