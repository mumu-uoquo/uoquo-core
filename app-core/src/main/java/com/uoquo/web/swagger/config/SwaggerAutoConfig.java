/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
//package com.uoquo.web.swagger.config;
//
//import io.swagger.annotations.Api;
//import io.swagger.annotations.ApiOperation;
//import org.springframework.beans.BeansException;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.beans.factory.config.BeanPostProcessor;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.boot.autoconfigure.condition.SearchStrategy;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.http.ResponseEntity;
//import org.springframework.util.ReflectionUtils;
//import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
//import springfox.documentation.builders.ApiInfoBuilder;
//import springfox.documentation.builders.PathSelectors;
//import springfox.documentation.builders.RequestHandlerSelectors;
//import springfox.documentation.service.ApiInfo;
//import springfox.documentation.service.Contact;
//import springfox.documentation.spi.DocumentationType;
//import springfox.documentation.spring.web.plugins.Docket;
//import springfox.documentation.spring.web.plugins.WebFluxRequestHandlerProvider;
//import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;
//
//import java.lang.reflect.Field;
//import java.util.List;
//import java.util.stream.Collectors;
//
///**
// * swagger 配置
// * springfox使用
// * 参考：https://blog.csdn.net/zzg19950824/article/details/103228230
// * 说明：当项目比较庞大，接口较多时，可以注入多个 Docket、ApiInfo，用于对不同模块的接口进行分组
// */
////@Configuration
//////@ConditionalOnProperty(name = "app.swagger.enabled", havingValue = "true", matchIfMissing = false)
//////@ConditionalOnExpression("${app.swagger.enabled:false} || ${springfox.documentation.enabled:false}")
//////@ConditionalOnExpression("${app.swagger.enabled:${springfox.documentation.enabled:false}} == 'true'")
////@ConditionalOnExpression("${app.swagger.enabled:${springfox.documentation.enabled:false}}")
//public class SwaggerAutoConfig {
//
//    @Value("${app.swagger.enabled:${springfox.documentation.enabled:false}}")
//    private boolean enableSwagger;
//
//    public static final String BASE_PACKAGE = "com.uoquo.**.controller";
//
//    /********************** 通用接口 ****************************/
//    @Bean
//    @ConditionalOnMissingBean(value = Docket.class, search = SearchStrategy.CURRENT)
//    public Docket createRestApi() {
//        return new Docket(DocumentationType.OAS_30)
//                .groupName("RestfulApi") // 分组名称，尽量不要使用中文和符号
//                .apiInfo(appInfo())      // 基本信息，定义版本号、title、描述等信息
//                .genericModelSubstitutes(ResponseEntity.class)
//                .useDefaultResponseMessages(true)
//                .forCodeGeneration(false)
//                .enable(enableSwagger)
//                .select()
//                // 配置暴露到文档中的接口
//                /*
//                 * RequestHandlerSelectors 配置要扫描接口的方式
//                 * 指定的包：basePackage
//                 * 扫描所有：any()
//                 * 全不扫描：none()
//                 * 扫描注解的类：withClassAnnotation()
//                 * 扫描注解方法：withMethodAnnotation()
//                 */
//                // 方法1：通过注解（在Controller上添加 @Api 注解）
//                .apis(RequestHandlerSelectors.withClassAnnotation(Api.class))
//                // 方法1：通过注解（在方法上添加 @ApiOperation 注解）
//                .apis(RequestHandlerSelectors.withMethodAnnotation(ApiOperation.class))
//                // 方法2：指定包路径（默认扫描所有包下的接口，若不需暴露，可配置 @ApiIgnore 注解）
//                .apis(RequestHandlerSelectors.basePackage(BASE_PACKAGE))
//                // 方法3：指定路径（默认扫描所有路径下的接口）
//                .paths(PathSelectors.any())
//                // 使用正则表达式排除含/admin/的路径
//                .paths(PathSelectors.regex("^(?!.*\\/admin\\/).*$"))
//                //.paths(PathSelectors.regex(".*(/v1/account/).*"))
//                //.paths(PathSelectors.ant("/**/v1/account/**"))
//                .build();
//    }
//
//    private ApiInfo appInfo() {
//        Contact contact = new Contact("uoquo",null, null);
//        return new ApiInfoBuilder()
//                .title("通用接口")    // 标题
//                .description("")    // 简介描述
//                .version("1.0.0")   // 版本号
//                .contact(contact)   // 联系人信息
//                .license("")        // 版权信息
//                .build();
//    }
//
//    /**
//     * 解决springboot2.6 和springfox不兼容问题
//     */
//    @Bean
//    @ConditionalOnMissingBean(value = BeanPostProcessor.class, search = SearchStrategy.CURRENT)
//    public static BeanPostProcessor springfoxHandlerProviderBeanPostProcessor() {
//        return new BeanPostProcessor() {
//            @Override
//            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
//                if (bean instanceof WebMvcRequestHandlerProvider || bean instanceof WebFluxRequestHandlerProvider) {
//                    customizeSpringfoxHandlerMappings(getHandlerMappings(bean));
//                }
//                return bean;
//            }
//
//            private <T extends RequestMappingInfoHandlerMapping> void customizeSpringfoxHandlerMappings(List<T> mappings) {
//                List<T> copy = mappings.stream()
//                        .filter(mapping -> mapping.getPatternParser() == null)
//                        .collect(Collectors.toList());
//                mappings.clear();
//                mappings.addAll(copy);
//            }
//
//            @SuppressWarnings("unchecked")
//            private List<RequestMappingInfoHandlerMapping> getHandlerMappings(Object bean) {
//                try {
//                    Field field = ReflectionUtils.findField(bean.getClass(), "handlerMappings");
//                    field.setAccessible(true);
//                    return (List<RequestMappingInfoHandlerMapping>) field.get(bean);
//                } catch (IllegalArgumentException | IllegalAccessException e) {
//                    throw new IllegalStateException(e);
//                }
//            }
//        };
//    }
//}
