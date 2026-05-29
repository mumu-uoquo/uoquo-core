/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.exception;

import com.uoquo.web.SystemReturnCode;

/**
 * 异常定义：非法的appkey.
 * @author: xuhz
 * @date：2020-06-11 12:35:30
 */
public class AppkeyInvalidException extends AbstractBaseException {

	public AppkeyInvalidException() {
		super(SystemReturnCode.APPKEY_INVALID);
	}
}
