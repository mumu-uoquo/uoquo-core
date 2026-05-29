/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.exception;

import com.uoquo.web.SystemReturnCode;

/**
 * 描述：禁止访问. <br>
 * 日期：2018-03-20 13:23 <br>
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
public class ForbiddenException extends AbstractBaseException {

    public ForbiddenException() {
        super(SystemReturnCode.FORBIDDEN);
    }
    public ForbiddenException(String message) {
        super(SystemReturnCode.FORBIDDEN, message);
    }
    public ForbiddenException(String message, Throwable ex) {
        super(SystemReturnCode.FORBIDDEN, message, ex);
    }
    public ForbiddenException(Throwable ex) {
        super(SystemReturnCode.FORBIDDEN, ex);
    }
}
