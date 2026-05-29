/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.annotation.web;

import java.lang.annotation.*;

/**
 * 描述：忽略认证校验. <br>
 * 背景：在拦截器中忽略相关校验，如忽略token校验，忽略登录校验. <br>
 * 日期：2019-06-02 14:38 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2019-06-02     Administrator.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented // 允许生成javadoc文档
@Inherited  // 允许子类继承父类中的注解
public @interface IgnoreAuth {
    /**
     * 忽略所有校验，默认false.<br>
     * 慎用！！
     */
    boolean all() default false;
    
    /**
     * 忽略时间戳校验，默认false.<br>
     * 主要防止设备与服务器时间不同步的情况
     */
    boolean timestamp() default false;
    
    /**
     * 忽略登录校验，默认false.<br>
     * 主要用于不需要登录即可访问的接口
     */
    boolean login() default false;
    
    /**
     * 忽略param校验，默认false.<br>
     */
    boolean params() default false;

    /**
     * 仅内部请求，默认false.<br>
     * 只能由内部服务调用
     */
    boolean inner() default false;

    /**
     * 刷新token的过期时间，默认true.<br>
     * 在某些场景下（如定时拉取消息的接口），可以设置为false
     */
    boolean refreshExpiresTime() default true;
}
