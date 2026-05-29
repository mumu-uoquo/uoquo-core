/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.mybatis.sensitive;

import java.lang.annotation.*;
/**
 * 该注解定义在类或 Mapper 方法上
 * <ul>
 *     <li>当注解定义在类上时，表示该类中有字段需要加解密（加解密字段通过 @SensitiveField 注解指定）</li>
 *     <li>当注解定义在方法上时，表示该方法的返回参数需要解密（如果是 String 或 List&lt;String&gt; 则直接解密，如果是对象则通过 @SensitiveField 注解指定）</li>
 * </ul>
 * @author xuhz
 **/
@Inherited
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveData {

}
