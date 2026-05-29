/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.test;

import com.uoquo.cloud.events.RemoteEvent;

public class TestRemoteEvent extends RemoteEvent<String> {
    public TestRemoteEvent(String businessType, String operationType, String operationStatus) {
        super(businessType, operationType, operationStatus);
    }
}
