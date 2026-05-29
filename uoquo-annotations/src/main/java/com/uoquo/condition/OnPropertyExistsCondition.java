/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.condition;

import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 属性是否存在的校验
 * @author uoquo
 */
public class OnPropertyExistsCondition implements Condition {
    @Override
    public boolean matches(@NonNull ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionOnPropertyExists.class.getName());
        if (attributes == null) {
            return false;
        }
        String propertyName  = getPropertyName(attributes);
        return context.getEnvironment().containsProperty(propertyName);
    }

    private String getPropertyName(Map<String, Object> attributes) {
        String prefix = getPropertyValue(attributes.get("prefix"));
        String name = getPropertyValue(attributes.get("name"));
        if (name.isEmpty()) {
            name = getPropertyValue(attributes.get("value"));
        }
        if (prefix.isEmpty()) {
            return name;
        } else {
            return prefix + "." + name;
        }
    }

    private String getPropertyValue(Object value) {
        if (value == null || value.toString().trim().isEmpty()) {
            return "";
        } else {
            return value.toString().trim();
        }
    }

}
