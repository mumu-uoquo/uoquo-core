/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web;

/**
 * 缓存key的常量定义
 */
public class BaseCacheKey {

    /**
     * 全局通讯秘钥
     */
    public static final String GLOBAL_SECRET            = "UOQUO:GLOBAL:SECRET";

    /**
     * 全局免登陆权限（主要用于登录等接口）
     */
    public static final String GLOBAL_PERMISSION        = "UOQUO:GLOBAL:UNAUTH";

    /**
     * 全局免签名接口（主要用于下载等接口）
     */
    public static final String GLOBAL_UNSIGNED          = "UOQUO:GLOBAL:UNSIGNED";

    /**
     * 系统所有资源信息
     */
    public static final String GLOBAL_ALL_RESOURCE      = "UOQUO:GLOBAL:ALL_RESOURCE";

    /**
     * 用户登录后的有效时长（分钟）
     */
    public static final String GLOBAL_TIMEOUT           = "UOQUO:GLOBAL:TIMEOUT";

    /**
     * 应用信息前缀
     */
    public static final String APPKEY_INFO_PREFIX        = "UOQUO:APPKEY:INFO:";

    /**
     * 应用秘钥前缀
     */
    public static final String APPKEY_SECRET_PREFIX      = "UOQUO:APPKEY:SECRET:";

    /**
     * 应用访问TOKEN前缀
     */
    public static final String APPKEY_TOKEN_PREFIX       = "UOQUO:APPKEY:TOKEN:ACCESS:";

    /**
     * 应用刷新TOKEN前缀
     */
    public static final String APPKEY_TOKEN_REFRESH      = "UOQUO:APPKEY:TOKEN:REFRESH:";

    /**
     * 应用权限前缀
     */
    public static final String APPKEY_PERMISSION_PREFIX  = "UOQUO:APPKEY:PERMISSION:";

    /**
     * 系统配置前缀
     */
    public static final String SYSTEM_SETTINGS_PREFIX    = "UOQUO:SYSTEM:SETTINGS:";

    /**
     * 系统字典前缀
     */
    public static final String SYSTEM_DICTIONARY_PREFIX  = "UOQUO:SYSTEM:DICTIONARY:";

    /**
     * 用户信息前缀
     */
    public static final String USER_INFO_PREFIX          = "UOQUO:USER:INFO:";

    /**
     * 用户访问TOKEN信息前缀
     */
    public static final String USER_TOKEN_PREFIX         = "UOQUO:USER:TOKEN:ACCESS:";

    /**
     * 用户刷新TOKEN信息前缀
     */
    public static final String USER_TOKEN_REFRESH        = "UOQUO:USER:TOKEN:REFRESH:";

    /**
     * 角色权限前缀
     */
    public static final String ROLE_PERMISSION_PREFIX    = "UOQUO:ROLE:PERMISSION:";

    /**
     * 请求随机数前缀（用于防重复提交）
     */
    public static final String REQUEST_NONCE_PREFIX      = "UOQUO:REQUEST:NONCE:";

}
