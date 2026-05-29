/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.exception;

import com.uoquo.web.BaseReturnCode;
import com.uoquo.web.SystemReturnCode;

/**
 * 异常定义：通用异常.
 */
public class UoquoException extends AbstractBaseException {
    public UoquoException(BaseReturnCode code) {
        super(code);
    }
    public UoquoException(BaseReturnCode code, String message) {
        super(code, message);
    }
    public UoquoException(BaseReturnCode code, String message, Object... args) {
        super(code, message, args);
    }

    public UoquoException(BaseReturnCode code, Throwable ex) {
        super(code, ex);
    }
    public UoquoException(BaseReturnCode code, Throwable ex, String message) {
        super(code, message, ex);
    }
    public UoquoException(BaseReturnCode code, Throwable ex, String message, Object... args) {
        super(code, message, args, ex);
    }

    /**
     * 主要用于多语言转换
     * @param ex 原始异常
     * @param message 多语言内容
     */
    public UoquoException(AbstractBaseException ex, String message) {
        super(SystemReturnCode.SYSTEM_ERROR, ex);
        this.status = ex.getStatus();
        this.code   = ex.getCode();
        this.mesg   = (message == null) ? ex.getMesg() : message;
        this.args   = ex.getArgs();
        this.level  = ex.getLevel();
    }
}
