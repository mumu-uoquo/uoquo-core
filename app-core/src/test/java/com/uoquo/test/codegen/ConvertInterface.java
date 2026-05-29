/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.codegen;

import com.uoquo.test.codegen.openapi.ServiceBean;
import com.uoquo.test.codegen.openapi.ServiceTag;

import java.util.Map;

public interface ConvertInterface {

    void convert(String baseUrl, String baseDir, Map<String, ServiceBean> beanMap, Map<String, ServiceTag> tagsMap);
}
