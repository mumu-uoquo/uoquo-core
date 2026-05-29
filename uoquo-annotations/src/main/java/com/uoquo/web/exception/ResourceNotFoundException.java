/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.exception;

import com.uoquo.web.SystemReturnCode;

/**
 * 描述：资源不存在. <br>
 * 日期：2018-03-20 13:30 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-03-20     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class ResourceNotFoundException extends AbstractBaseException {

    public ResourceNotFoundException() {
        super(SystemReturnCode.RESOURCE_NOT_FOUND);
    }
    public ResourceNotFoundException(String message) {
        super(SystemReturnCode.RESOURCE_NOT_FOUND, message);
    }
    public ResourceNotFoundException(String message, Throwable ex) {
        super(SystemReturnCode.RESOURCE_NOT_FOUND, message, ex);
    }
    public ResourceNotFoundException(Throwable ex) {
        super(SystemReturnCode.RESOURCE_NOT_FOUND, ex);
    }
}
