/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.exception;

import com.uoquo.web.SystemReturnCode;

/**
 * 异常定义：常规异常.
 */
public class SystemWarnException extends AbstractBaseException {

	public SystemWarnException(String message, Object... args) {
		super(SystemReturnCode.SYSTEM_WARN, message, args);
	}
	public SystemWarnException(Throwable ex, String message, Object... args) {
		super(SystemReturnCode.SYSTEM_WARN, message, args, ex);
	}
	public SystemWarnException(Throwable ex) {
		super(SystemReturnCode.SYSTEM_WARN, ex);
	}
}
