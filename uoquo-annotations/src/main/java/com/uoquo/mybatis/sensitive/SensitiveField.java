/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.mybatis.sensitive;

import java.lang.annotation.*;

/**
 * 该注解有两种使用方式
 * <ul>
 *     <li>当注解定义在类的属性上时，则对应类需要添加@SensitiveData注解</li>
 *     <li>当注解定义在方法的参数上时，则直接使用</li>
 * </ul>
 * @author xuhz
 **/
@Documented
@Inherited
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveField {

}
