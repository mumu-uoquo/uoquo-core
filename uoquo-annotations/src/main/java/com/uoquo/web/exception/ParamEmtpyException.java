/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.exception;

import com.uoquo.web.SystemReturnCode;

/**
 * 异常定义：参数为空.
 * @author: xuhz
 * @date：2020-06-11 12:35:30
 */
public class ParamEmtpyException extends AbstractBaseException {

	public ParamEmtpyException(String message, Object... args) {
		super(SystemReturnCode.PARAM_EMPTY, message, args);
	}
	public ParamEmtpyException(String message, Throwable ex) {
		super(SystemReturnCode.PARAM_EMPTY, message, ex);
	}
	public ParamEmtpyException(Throwable ex) {
		super(SystemReturnCode.PARAM_EMPTY, ex);
	}
}
