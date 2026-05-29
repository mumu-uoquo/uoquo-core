/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
//package com.uoquo.web.swagger.plugin;
//
//import io.swagger.annotations.ApiOperation;
//import org.springframework.core.annotation.Order;
//import org.springframework.stereotype.Component;
//import springfox.documentation.service.Operation;
//import springfox.documentation.spi.DocumentationType;
//import springfox.documentation.spi.service.OperationBuilderPlugin;
//import springfox.documentation.spi.service.contexts.OperationContext;
//import springfox.documentation.swagger.common.SwaggerPluginSupport;
//
//import java.util.Optional;
//
///**
// * 格式化 swagger 的 uniqueId.
// * springfox使用
// */
////@Component
////@Order(SwaggerPluginSupport.SWAGGER_PLUGIN_ORDER + 1000)
//public class SwaggerOperationNicknamePlugin implements OperationBuilderPlugin {
//
//    @Override
//    public void apply(OperationContext context) {
//        Optional<ApiOperation> methodAnnotation = context.findControllerAnnotation(ApiOperation.class);
//        Operation operationBuilder = context.operationBuilder().build();
//        // 没指定 nickname 时，swagger会用方法名自动生成（类似于 methodNameUsingPOST）
//        String uniqueId = operationBuilder.getUniqueId().replaceAll("Using(GET|POST|PUT|DELETE)", "");
//        // If nickname exists, populate the value of nickname annotation into uniqueId
//        String fillId = methodAnnotation.map(ApiOperation::nickname).orElse(uniqueId);
//        // 重名方法 swagger 会自动添加（_1, _2）这种后缀，所以需要去掉
//        fillId = fillId.replaceAll("_.+","");
//        /*
//        String tag = operationBuilder.getTags().stream().findFirst().get();
//        tag = tag.replace("-controller","s");
//        int index = tag.indexOf("-");
//        while (index >= 0) {
//            tag = tag.substring(0, index) + tag.substring(index + 1,index + 2).toUpperCase(Locale.ROOT) + tag.substring(index + 2);
//            index = tag.indexOf("-");
//        }
//        fillId = tag + uniqueId;
//        */
//
//        context.operationBuilder().uniqueId(fillId);
//        context.operationBuilder().codegenMethodNameStem(fillId);
//    }
//
//    @Override
//    public boolean supports(DocumentationType delimiter) {
//        return SwaggerPluginSupport.pluginDoesApply(delimiter);
//    }
//}