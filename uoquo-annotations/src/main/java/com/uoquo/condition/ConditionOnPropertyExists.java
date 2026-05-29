/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

/**
 * 属性是否存在的校验（与 ConditionalOnProperty类似）
 * @author uoquo
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@Documented
@Conditional(OnPropertyExistsCondition.class)
public @interface ConditionOnPropertyExists {
    /**
     * Alias for {@link #name()}.
     * @return the names
     */
    String value() default "";

    /**
     * A prefix that should be applied to each property. The prefix automatically ends
     * with a dot if not specified. A valid prefix is defined by one or more words
     * separated with dots (e.g. {@code "acme.system.feature"}).
     * @return the prefix
     */
    String prefix() default "";

    /**
     * The name of the properties to test. If a prefix has been defined, it is applied to
     * compute the full key of each property. For instance if the prefix is
     * {@code app.config} and one value is {@code my-value}, the full key would be
     * {@code app.config.my-value}
     * <p>
     * Use the dashed notation to specify each property, that is all lower case with a "-"
     * to separate words (e.g. {@code my-long-property}).
     * @return the names
     */
    String name() default "";
}
