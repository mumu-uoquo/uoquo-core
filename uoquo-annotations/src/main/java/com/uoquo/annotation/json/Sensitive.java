/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.annotation.json;

import java.lang.annotation.*;

/**
 * JSON序列化、反序列化时的脱敏和加解密处理，用法如下：
 * <ol>
 *   <li>在param、dto的对象类的字段上添加注解，并指定具体的处理类型</li>
 *   <li>特别说明：仅对String类型的字段有效</li>
 * </ol>
 * 
 * @author xuhz
 */
@Documented
@Inherited
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {

    /**
     * 脱敏或加解密类型
     */
    SensitiveType type() default SensitiveType.DEFAULT;

    /**
     * 秘钥（为空时，按以下规则加载）
     * <ol>
     *   <li>AES、SM4的秘钥
     *     <ul>
     *       <li>先加载缓存：security.aes.key </li>
     *       <li>再读取配置：app.security.aes.key </li>
     *     </ul>
     *   </li>
     *   <li>TAES、TSM4
     *     <ul>
     *       <li>计算当前时间片，并后置补0为16位字符串作为秘钥 </li>
     *       <li>先加载缓存：security.aes.time-step </li>
     *       <li>再读取配置：app.security.aes.time-step </li>
     *       <li>若未配置时间片长度，默认为5秒 </li>
     *     </ul>
     *   </li>
     *   <li>RSA、SM2的私钥
     *     <ul>
     *       <li>先加载缓存：security.rsa.private-key </li>
     *       <li>再读取配置：app.security.rsa.private-key </li>
     *       <li>若未配置秘钥，则降级为TAES或TSM4 </li>
     *       <li>特别说明：SM2只支持私钥解密，因此在序列化时降级为TSM4 </li>
     *     </ul>
     *   </li>
     * </ol>
     */
    String key() default "";

    /**
     * 自定义正则表达式（当type为CUSTOM时使用）
     * 默认：全替换为*
     */
    String pattern() default "(?s).";

    /**
     * 自定义替换字符
     */
    String replacement() default "*";

    /**
     * 保留前几位
     */
    int prefixLen() default Integer.MAX_VALUE;

    /**
     * 保留后几位
     */
    int suffixLen() default Integer.MAX_VALUE;
}
