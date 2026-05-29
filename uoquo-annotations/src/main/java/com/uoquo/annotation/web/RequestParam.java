/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.annotation.web;

import java.lang.annotation.*;

/**
 * 描述：自定义参数注解. <br>
 * 示例：@RequestParam("user") 将参数 user.name=XX（或请求体 {user:{name:'XX'}} ）解析为 {name:'XX'} 对象. <br/>
 * 注意：
 * <ul>
 *  <li>目前仅支持单对象，不支持List或者数组</li>
 *  <li>消息体（或者post提交的form表单）中的参数会优先于url中的参数，<br/>即：url中同名的参数会被消息体中的参数覆盖</li>
 *  <li>作为feign接口参数注解时，BaseEntity、map、array、collection这几种类型不允许传null值</li>
 * </ul>
 * 日期：2018-01-25 09:19 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-25     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestParam {
    
    /**
     * 用于绑定的请求参数名字.
     */
    String value() default "";
    
    /**
     * 将form的key按指定字符进行分割，如果为空，则不分割.
     */
    String split() default "\\.";
    
    /**
     * 读取request body的内容.
     */
    boolean readBody() default true;
    
    /**
     * 是否必须，默认是.
     */
    boolean required() default true;

}
