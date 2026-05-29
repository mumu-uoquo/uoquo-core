/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.feign.processor;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;

import com.uoquo.web.base.BaseEntity;
import com.uoquo.annotation.web.RequestParam;
import com.uoquo.utils.StringUtil;

import feign.MethodMetadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

import org.springframework.cloud.openfeign.AnnotatedParameterProcessor;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 描述：将YocalyParam注解按自定义格式放入requestBody中. <br>
 * 注意：yocaly的BaseEntity、map、array、collection这几种类型不允许传null值
 * 日期：2018-03-11 02:01 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-03-11     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class RequestParamProcessor implements AnnotatedParameterProcessor {

    private static final Class<RequestParam> ANNOTATION = RequestParam.class;

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return ANNOTATION;
    }

    @Override
    public boolean processArgument(AnnotatedParameterContext context, Annotation annotation, Method method) {
        int parameterIndex = context.getParameterIndex();
        // 参数名
        RequestParam requestParam = ANNOTATION.cast(annotation);
        String name = requestParam.value();
        checkState(emptyToNull(name) != null,
                "YocalyParam.value() was empty on parameter %s",
                parameterIndex);
        // 设置body模板，类似于@Body标签
        return replaceBodyTemplate(context, name, parameterIndex, method);
    }
    
    /**
     * 设置BodyTemplate，即 @Body 标签的值.<br>
     * @param context  注解信息
     * @param name     参数名称
     * @param paramIdx 参数序号
     * @param method   方法信息
     */
    public static boolean replaceBodyTemplate(AnnotatedParameterContext context, String name, int paramIdx, Method method) {
        MethodMetadata data = context.getMethodMetadata();
        String bodyTpl = data.template().bodyTemplate();
        if (StringUtil.isNull(bodyTpl)) {
            // 检测是否有 RequestBody 注解
            int annotNum = 0;
            for (Annotation[] anos : method.getParameterAnnotations()) {
                for (Annotation ano : anos) {
                    checkState(!(ano instanceof RequestBody),
                            "on method %s 参数注解冲突：YocalyParam, Param 与 org.springframework.web.bind.annotation.RequestBody 只能同时有一个",
                            method);
                }
                if (anos.length > 0) {
                    annotNum++;
                }
            }
            // 检测是否所有参数都有注解
            int paramNum = method.getParameterCount();
            checkState(paramNum == annotNum,
                    "on method %s 所有参数都必须有注解",
                    method);
            // 设置bodyIndex，由feign检测，但会导致传递的参数不对，需要看看源码bodyIdex表示什么意思
            // data.bodyIndex(paramIdx);
            bodyTpl = "%7B%7D"; // 即：bodyTpl = "{}"
        }
        Class<?> parameterType = method.getParameterTypes()[paramIdx];
        if (parameterType.isPrimitive()) {
            // 基础类型
            if (char.class.isAssignableFrom(parameterType)) {
                bodyTpl = bodyTpl.replaceAll("%7D", ", \"" + name + "\": \"{" + name + "}\"%7D"); // 带双引号
            } else {
                //bodyTpl = bodyTpl.replaceAll("%7D", ", \"" + name + "\": {" + name + "}%7D"); // 当传入值为null时，接收端解析会报错
                bodyTpl = bodyTpl.replaceAll("%7D", ", \"" + name + "\": \"{" + name + "}\"%7D"); // 带双引号
            }
        } else if (Number.class.isAssignableFrom(parameterType) 
                || Boolean.class.isAssignableFrom(parameterType)) {
            // 数字及布尔类型
            bodyTpl = bodyTpl.replaceAll("%7D", ", \"" + name + "\": \"{" + name + "}\"%7D"); // 带双引号
        } else if (BaseEntity.class.isAssignableFrom(parameterType)
                || Map.class.isAssignableFrom(parameterType)
                || Collection.class.isAssignableFrom(parameterType)
                || Array.class.isAssignableFrom(parameterType)) {
            // 自定义的pojo类型、map、集合、数组类型
            // 注意：不可以为null，否则解析端会报错
            bodyTpl = bodyTpl.replaceAll("%7D", ", \"" + name + "\": {" + name + "}%7D");
        } else {
            // 字符串及其他类型
            bodyTpl = bodyTpl.replaceAll("%7D", ", \"" + name + "\": \"{" + name + "}\"%7D"); // 带双引号
        }
        
        /* ***************************************** *
         * TODO 如果feignd的方法能处理null，则拼接的body的json模板可以不带双引号
         * （详见feign的 RequestTemplate feign.ReflectiveFeign.BuildTemplateByResolvingArgs.create(Object[] argv)）
         * ***************************************** */
        //bodyTpl = bodyTpl.replaceAll("%7D", ", \"" + name + "\": {" + name + "}%7D"); // 当数据为null时，会报错
        bodyTpl = bodyTpl.replaceAll("%7B, ", "%7B");
        data.template().bodyTemplate(bodyTpl);
        data.indexToExpander().put(paramIdx, new ToJsonExpander());
        context.setParameterName(name);
        return true;
    }
}
