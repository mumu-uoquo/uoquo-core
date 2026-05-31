/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.exception;

import com.uoquo.web.SystemReturnCode;

import java.io.Serial;

/**
 * 异常定义：Feign解析其他服务返回的异常信息.
 * @author: xuhz
 * @date：2020-06-11 12:35:30
 */
public class RemoteServiceException extends AbstractBaseException {
	@Serial
    private static final long serialVersionUID = -7034897190745766959L;

	private String trace;

	public RemoteServiceException() {
		super(SystemReturnCode.SYSTEM_ERROR);
	}

    public RemoteServiceException(String message) {
        super(SystemReturnCode.SYSTEM_ERROR, message);
    }

    public RemoteServiceException(String message, Throwable ex) {
        super(SystemReturnCode.SYSTEM_ERROR, message, ex);
    }

	@Override
	public String getTrace() {
		return this.trace;
	}
	// 由于该异常主要接收其他服务返回的异常，因此不需要讲自身的堆栈信息输出，只需要输出后端返回的堆栈即可
	@Override
	public void setTrace(String trace) {
		if (this.trace == null) {
			this.trace = trace;
		} else {
			this.trace = String.format("%s\r\n%s", trace, this.trace);
		}
	}
}
