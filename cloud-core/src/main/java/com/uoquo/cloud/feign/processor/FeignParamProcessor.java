/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.feign.processor;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;

import feign.Param;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.springframework.cloud.openfeign.AnnotatedParameterProcessor;

/**
 * 描述：将Feign.Param注解按自定义格式放入requestBody中. <br>
 * 日期：2018-03-10 01:40 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-03-10     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo teams
 */
public class FeignParamProcessor implements AnnotatedParameterProcessor {

    private static final Class<Param> ANNOTATION = Param.class;

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return ANNOTATION;
    }

    @Override
    public boolean processArgument(AnnotatedParameterContext context, Annotation annotation, Method method) {
        int parameterIndex = context.getParameterIndex();
        // 参数名
        Param requestParam = ANNOTATION.cast(annotation);
        String name = requestParam.value();
        checkState(emptyToNull(name) != null,
                "Param.value() was empty on parameter %s",
                parameterIndex);
        // 设置body模板，类似于@Body标签
        return RequestParamProcessor.replaceBodyTemplate(context, name, parameterIndex, method);
    }
}
