/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web;

/**
 * 系统响应码（0XXXX）
 * <ul>
 *     <li>00000：请求成功</li>
 *     <li>01XXX：系统错误</li>
 *     <li>02XXX：认证相关</li>
 * </ul>
 */
public class SystemReturnCode extends BaseReturnCode {
    SystemReturnCode(String code, String text) {
        this(code, text, ReturnLevel.ERROR);
    }
    SystemReturnCode(String code, String text, ReturnLevel level) {
        super(code, text, level);
    }

    /** =============================== 00000 请求成功 =============================== **/
    public static BaseReturnCode SUCCESS = new SystemReturnCode("00000", "请求成功", ReturnLevel.SUCCESS);

    /** =============================== 01XXX 系统错误 =============================== **/
    public static BaseReturnCode PARAM_ERROR  = new SystemReturnCode("01001", "参数错误");
    public static BaseReturnCode PARAM_EMPTY  = new SystemReturnCode("01002", "参数为空");
    public static BaseReturnCode FORBIDDEN    = new SystemReturnCode("01403", "无权操作");
    public static BaseReturnCode RESOURCE_NOT_FOUND = new SystemReturnCode("01404", "页面不存在");
    public static BaseReturnCode TOO_MANY_REQUEST   = new SystemReturnCode("01429", "并发请求过多");
    public static BaseReturnCode SYSTEM_INFO  = new SystemReturnCode("01490", "系统内部提示", ReturnLevel.INFO);
    public static BaseReturnCode SYSTEM_WARN  = new SystemReturnCode("01495", "系统内部警告", ReturnLevel.WARN);
    public static BaseReturnCode SYSTEM_ERROR = new SystemReturnCode("01500", "系统内部错误", ReturnLevel.ERROR);
    public static BaseReturnCode SERVICE_UNAVAILABLE = new SystemReturnCode("01503", "服务不可用", ReturnLevel.ERROR);

    /** =============================== 02XXX 认证相关 =============================== **/
    public static BaseReturnCode PARAM_SIGN_EMPTY = new SystemReturnCode("02001", "签名参数为空");
    public static BaseReturnCode PARAM_SIGN_ERROR = new SystemReturnCode("02002", "签名参数错误");
    public static BaseReturnCode APPKEY_EMPTY     = new SystemReturnCode("02003", "appkey 为空");
    public static BaseReturnCode APPKEY_INVALID   = new SystemReturnCode("02004", "appkey 失效");
    public static BaseReturnCode TOKEN_EMPTY      = new SystemReturnCode("02005", "token 为空");
    public static BaseReturnCode TOKEN_INVALID    = new SystemReturnCode("02006", "token 失效");
    public static BaseReturnCode ACCOUNT_NOTLOGIN = new SystemReturnCode("02020", "账户未登录");
    public static BaseReturnCode ACCOUNT_KICK_OUT = new SystemReturnCode("02021", "账号异地登录，强制下线");

}
