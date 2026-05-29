/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.codegen.openapi;

/**
 * 接口方法
 */
public class ServiceMethod {

    /**
     * 分类标签（目标文件名）
     */
    private String tags;
    /**
     * 接口名称（operationId）
     */
    private String name;
    /**
     * 接口地址
     */
    private String url;
    /**
     * 请求方式
     */
    private String method;
    /**
     * 响应类型
     */
    private String contentType;
    /**
     * 接口描述
     */
    private String description;
    /**
     * 请求参数（主，多个参数时，组装为一个data）
     */
    private ServiceBean requestBean;
    /**
     * 请求参数（一般是即有请求体，又有URL入参时）
     */
    private ServiceBean requestParam;
    /**
     * 请求参数的注释
     */
    private String requestDescription;
    /**
     * 返回参数
     */
    private ServiceBean responseBean;

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ServiceBean getRequestBean() {
        return requestBean;
    }

    public void setRequestBean(ServiceBean requestBean) {
        this.requestBean = requestBean;
    }

    public ServiceBean getRequestParam() {
        return requestParam;
    }

    public void setRequestParam(ServiceBean requestParam) {
        this.requestParam = requestParam;
    }

    public String getRequestDescription() {
        return requestDescription;
    }

    public void setRequestDescription(String requestDescription) {
        this.requestDescription = requestDescription;
    }

    public ServiceBean getResponseBean() {
        return responseBean;
    }

    public void setResponseBean(ServiceBean responseBean) {
        this.responseBean = responseBean;
    }
}
