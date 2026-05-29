/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.exception;

import com.uoquo.web.SystemReturnCode;

/**
 * 异常定义：参数错误.
 * @author: xuhz
 * @date：2020-06-11 12:35:30
 */
public class ParamErrorException extends AbstractBaseException {

	public ParamErrorException(String message) {
		super(SystemReturnCode.PARAM_ERROR, message);
	}
	public ParamErrorException(String message, Throwable ex) {
		super(SystemReturnCode.PARAM_ERROR, message, ex);
	}
	public ParamErrorException(Throwable ex) {
		super(SystemReturnCode.PARAM_ERROR, ex);
	}
}
