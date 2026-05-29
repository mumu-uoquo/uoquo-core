/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.codegen.util;

/**
 * Created by whf on 8/28/15.
 */
public enum ResourceType {
    JAR("jar"),
    FILE("file"),

    CLASS_FILE(".class");

    private String typeString;

    private ResourceType(String type) {
        this.typeString = type;
    }

    public String getTypeString() {
        return this.typeString;
    }
}
