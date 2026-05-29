/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web;

import com.uoquo.web.exception.AbstractBaseException;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 描述：API统一响应体. <br>
 * 日期：2018-02-01 12:53 <br>
 * 说明：在返回参数用ReturnData包装<br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-02-01     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
@Schema(description = "响应出参")
public class ReturnData<T> {
    /**
     * 状态码，正常为00000，其他则是业务错误码 .
     * 错误时，可用于展现.
     */
    @Schema(description = "状态码（正常为00000，其他则是业务错误码")
    private String status;

    /**
     * 完整错误码（含服务和节点信息） .
     */
    @Schema(description = "完整错误码")
    private String code;

    /**
     * 消息级别，主要用于出错时，前端的提示方式 .
     */
    @Schema(description = "消息级别")
    private String level;

    /**
     * 错误消息内容 .
     */
    @Schema(description = "错误消息内容")
    private String message;

    /**
     * 具体数据 .
     */
    @Schema(description = "具体数据")
    private T data;

    public ReturnData() {
        this.status  = SystemReturnCode.SUCCESS.getCode();
        this.code    = SystemReturnCode.SUCCESS.getCode();
        this.message = SystemReturnCode.SUCCESS.getText();
        this.level   = SystemReturnCode.SUCCESS.getLevel().getText();
    }

    public ReturnData(T data) {
        this.status  = SystemReturnCode.SUCCESS.getCode();
        this.code    = SystemReturnCode.SUCCESS.getCode();
        this.message = SystemReturnCode.SUCCESS.getText();
        this.level   = SystemReturnCode.SUCCESS.getLevel().getText();
        this.data    = data;
    }

    public ReturnData(AbstractBaseException ex) {
        if (ex == null) {
            // 如果返回空对象（此时说明是成功返回的）
            this.status  = SystemReturnCode.SUCCESS.getCode();
            this.code    = SystemReturnCode.SUCCESS.getCode();
            this.message = SystemReturnCode.SUCCESS.getText();
            this.level   = SystemReturnCode.SUCCESS.getLevel().getText();
        } else {
            this.status  = ex.getStatus();
            this.code    = ex.getCode();
            this.message = ex.getMesg();
            this.level   = ex.getLevel();
            // 生产时只返回请求ID，便于调试；不返回堆栈信息，防止泄露信息
            String activeType = System.getProperty("spring.profiles.active");
            if ("prod".equalsIgnoreCase(activeType)) {
                this.data    = (T)ex.getTraceId();
            } else {
                this.data    = (T)ex.getTrace();
            }
        }
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

}
