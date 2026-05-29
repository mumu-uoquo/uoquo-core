/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.controller;


import com.uoquo.web.ReturnData;
import com.uoquo.annotation.web.IgnoreAuth;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

@Hidden
@RestController
public class AppVersionController {

    @Resource
    ApplicationContext applicationContext;

    /**
     * 获取启动类所在包的版本号
     * 注意：启动类必须实现ApplicationRunner
     * */
    @Operation(hidden = true)
    @IgnoreAuth(all = true)
    @RequestMapping("/get/version")
    public ReturnData<String> getGitInfo() {
        String version = null;
        Map<String, ApplicationRunner> applicationRunnerMap = applicationContext.getBeansOfType(ApplicationRunner.class);
        Iterator<Entry<String, ApplicationRunner>> iterator = applicationRunnerMap.entrySet().iterator();
        if(iterator.hasNext()) {
            Entry<String, ApplicationRunner> entry=iterator.next();
            version = entry.getValue().getClass().getPackage().getImplementationVersion();
        }
        return new ReturnData<>(version);
    }
}
