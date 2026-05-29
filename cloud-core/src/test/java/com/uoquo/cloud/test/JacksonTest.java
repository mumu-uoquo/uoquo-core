/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.test;

import com.uoquo.cloud.events.RemoteEvent;
import com.uoquo.utils.json.JsonUtil;
import org.junit.jupiter.api.Test;

public class JacksonTest {

    @Test
    public void test() {
//        RemoteEvent<String> event = new RemoteEvent<>("businessType", "operationType", "operationStatus");
//        event.setOldData("aa");
////        event.setDataType(String.class);
//        System.out.println(JsonUtil.serialize( event));

        TestRemoteEvent testEvent = new TestRemoteEvent("businessType", "operationType", "operationStatus");
        System.out.println(JsonUtil.serialize( testEvent));
    }
}
