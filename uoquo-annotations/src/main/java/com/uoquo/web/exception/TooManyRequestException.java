/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.exception;

import com.uoquo.web.SystemReturnCode;

/**
 * 异常定义：并发请求过多.
 * @author: xuhz
 * @date：2020-06-11 12:35:30
 */
public class TooManyRequestException extends AbstractBaseException {

	public TooManyRequestException() {
		super(SystemReturnCode.TOO_MANY_REQUEST);
	}
	public TooManyRequestException(String message) {
		super(SystemReturnCode.TOO_MANY_REQUEST, message);
	}
}
