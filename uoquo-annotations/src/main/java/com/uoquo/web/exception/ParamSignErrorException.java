/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.exception;

import com.uoquo.web.SystemReturnCode;

/**
 * 异常定义：签名校验失败.
 * @author: xuhz
 * @date：2020-06-11 12:35:30
 */
public class ParamSignErrorException extends AbstractBaseException {

	public ParamSignErrorException() {
		super(SystemReturnCode.PARAM_SIGN_ERROR);
	}

	public ParamSignErrorException(String message) {
		super(SystemReturnCode.PARAM_SIGN_ERROR, message);
	}
}
