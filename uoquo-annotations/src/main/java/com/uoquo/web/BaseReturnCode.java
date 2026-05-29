/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web;

/**
 * 描述：错误码定义. <br>
 * <ul>
 *     <li>00XXX：系统预留</li>
 *     <li>01XXX：系统错误</li>
 *     <li>02XXX：认证相关</li>
 *     <li>其他：自定义错误</li>
 * </ul>
 */
public abstract class BaseReturnCode {


    // ============================== //
    protected String code = null;
    protected String text = null;
    protected ReturnLevel level = null;


    protected BaseReturnCode(String code, String text) {
        this(code, text, ReturnLevel.ERROR);
    }
    protected BaseReturnCode(String code, String text, ReturnLevel level) {
        this.code = code;
        this.text = text;
        this.level = level;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public ReturnLevel getLevel() {
        return level;
    }

    public void setLevel(ReturnLevel level) {
        this.level = level;
    }
}
