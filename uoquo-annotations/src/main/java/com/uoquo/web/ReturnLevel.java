/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web;

/**
 * 描述：消息级别定义. <br>
 * 日期：2018-06-05 09:21 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-06-05     xuhz.            创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public enum ReturnLevel {
    SILENT("SILENT"), // 静默
    INFO("INFO"),     // 提示
    WARN("WARN"),     // 警告
    ERROR("ERROR"),   // 错误
    NOTICE("NOTICE"), // 通知
    SUCCESS("SUCCESS"); // 成功


    // ============================== //
    private String text;
    ReturnLevel(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
