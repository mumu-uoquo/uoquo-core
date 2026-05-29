/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test;

import com.uoquo.web.exception.ParamEmtpyException;
import com.uoquo.web.exception.SystemErrorException;
import com.uoquo.web.exception.UoquoException;
import org.junit.jupiter.api.Test;

public class TestClass {

    @Test
    public void testException() {
        SystemErrorException ex = new SystemErrorException("hello %1$s %2$d %1$s", "张三", 2025);
        System.out.println(ex.getMesg());

        ParamEmtpyException ex2 = new ParamEmtpyException("hello %1$s %2$d %1$s");
        System.out.println(ex2.getMesg());

        ParamEmtpyException ex3 = new ParamEmtpyException("hello %1$s %2$d %1$s", "张三", 2025);
        System.out.println(ex3.getMesg());

        UoquoException ex4 = new UoquoException(ex3, "你好 %1$s %2$d %1$s");
        System.out.println(ex4.getMesg());
        System.out.println(ex4.toJson());
    }

}
