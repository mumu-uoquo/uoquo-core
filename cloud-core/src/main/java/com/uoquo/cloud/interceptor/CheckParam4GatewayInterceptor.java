/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.interceptor;

import com.uoquo.utils.StringUtil;
import com.uoquo.utils.CurrentUser;
import com.uoquo.web.exception.ParamSignEmptyException;
import com.uoquo.web.exception.ParamSignErrorException;
import com.uoquo.web.interceptor.CheckParamInterceptor;
import com.uoquo.utils.SignParamUtil;

import jakarta.servlet.http.HttpServletRequest;

import com.uoquo.web.utils.WebUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 描述：微服务全局拦截器. <br>
 * 背景：微服务的所有请求（包括免签、忽略所有校验的接口）都必须由网关转发（或由内部微服务 FEIGN 发起），
 *      因此在父类的参数校验之前，先校验网关签名，防止绕过网关直接访问服务. <br>
 * 日期：2018-01-25 11:13 <br>
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
public class CheckParam4GatewayInterceptor extends CheckParamInterceptor {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * 请求来源校验：必须是网关转发的请求.
     */
    @Override
    protected void checkSource(HttpServletRequest request) throws Exception {
        this.checkGatewayParams(request);
    }

    /**
     * 网关签名校验.<br>
     * 要求网关转发的所有请求都带有 gateway-signature 和 gateway-time：
     * 网关签名 = sign(客户端签名 + 网关时间, 全局密钥)，免签的请求没有客户端签名时，按空串参与计算.
     */
    protected void checkGatewayParams(HttpServletRequest request) throws Exception {
        // 网关签名信息
        String reqGatewaySign = request.getHeader(CurrentUser.GATEWAY_SIGN);
        if (StringUtil.isNull(reqGatewaySign)) {
            throw new ParamSignEmptyException(CurrentUser.GATEWAY_SIGN);
        }
        String timestamp = request.getHeader(CurrentUser.GATEWAY_TIME);
        if (StringUtil.isNull(timestamp)) {
            throw new ParamSignEmptyException(CurrentUser.GATEWAY_TIME);
        }
        // 网关时间必须在有效期内（防止抓包后的网关签名被永久重放）
        if (!WebUtil.isGatewayTimeValid(timestamp)) {
            log.warn("网关时间不正确或已过期：[{}]", timestamp);
            throw new ParamSignErrorException(CurrentUser.GATEWAY_TIME);
        }
        // 客户端签名信息（免签的接口可能没有）
        String reqAppSign = WebUtil.getHeader(CurrentUser.SIGN_APP, request);
        if (reqAppSign == null) {
            reqAppSign = "";
        }
        // 缓存全局通信秘钥
        String globalSecret = CurrentUser.getGlobalSecret();
        // 网关签名校验
        String clcGlobalSecret = SignParamUtil.sign(reqAppSign + timestamp, globalSecret);
        if (!reqGatewaySign.equalsIgnoreCase(clcGlobalSecret)) {
            log.debug("网关签名原文[{}]，计算的密文[{}]，传入的密文[{}]", reqAppSign + timestamp, clcGlobalSecret, reqGatewaySign);
            throw new ParamSignErrorException(CurrentUser.GATEWAY_SIGN);
        }
    }
}
