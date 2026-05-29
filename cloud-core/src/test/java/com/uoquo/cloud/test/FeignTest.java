/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.test;

import com.uoquo.utils.json.JsonUtil;
import com.uoquo.utils.json.TypeToken;
import com.uoquo.web.ReturnData;
import com.uoquo.web.SystemReturnCode;
import com.uoquo.web.exception.RemoteServiceException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;

public class FeignTest {

    @Test
    public void test() {
        String responseText = "{\"status\":\"01001\",\"code\":\"01101001\",\"level\":\"ERROR\",\"message\":\"[operatorId]operatorId 不能为空\",\"data\":\"K7A88W0JHRRFK2GH\"}";
        Type respType = TypeToken.getParameterized(ReturnData.class, String.class).getType();
        ReturnData<?> data = JsonUtil.deserialize(responseText, respType);
        if (SystemReturnCode.SUCCESS.getCode().equals(data.getStatus())) {
            System.out.println("success");
            return;
        }
        RemoteServiceException error = new RemoteServiceException();
        error.setStatus(data.getStatus());
        error.setCode(data.getCode());
        error.setLevel(data.getLevel());
        error.setMesg(data.getMessage());
        if (data.getData() != null) {
            error.setTrace(data.getData().toString());
        }
        System.out.println("ERROR");
    }
}
