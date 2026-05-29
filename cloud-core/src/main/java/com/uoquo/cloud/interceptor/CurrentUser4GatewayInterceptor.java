/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.interceptor;

import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.utils.CurrentUser;
import com.uoquo.web.BaseCacheKey;
import com.uoquo.utils.spring.RedisUtil;
import com.uoquo.web.interceptor.CurrentUserInterceptorAdapter;
import com.uoquo.utils.SignParamUtil;
import com.uoquo.web.utils.WebUtil;
import org.apache.seata.core.context.RootContext;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.HttpMethod;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 描述：登录用户信息拦�? <br>
 * 背景：从网关传入的用户信�? <br>
 * 日期�?018-01-25 11:13 <br>
 * 变更�?
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-25     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class CurrentUser4GatewayInterceptor extends CurrentUserInterceptorAdapter {
    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
            throws Exception {
        // 如果是跨域的OPTIONS请求，放�?
        if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 1. 如果是内部出错的跳转（此时保持用户信息）
        Throwable error = (Throwable)request.getAttribute(DefaultErrorAttributes.class.getName() + ".ERROR");
        if (error != null) {
            return true;
        }
        // 2. 清除线程缓存数据
        CurrentUser.clear();
        // 3. 缓存用户信息
        // 解析用户信息（优先从Redis中获取）
        CurrentUser.UserInfo user = this.getUserInfo4Redis(request);
        if (user == null) {
            // 全局免签判断
            boolean unsigned = getIgnoreAuthAnnotation(handler).all();
            if (!unsigned) {
                String path = request.getRequestURI();
                unsigned = RedisUtil.existSetItem(BaseCacheKey.GLOBAL_UNSIGNED, path);
            }
            // 免签的或者经过网关签名的请求，才从请求头中获取用户信�?
            // 即：只信任网关放入请求头的用户信息，防止前端私自放入用户信息，绕过登�?
            if (unsigned || this.checkGatewayParams(request)) {
                log.debug("从Redis中获取用户信息失败，从请求头中获取用户信�?");
                user = this.getUserInfo4Request(request);
            }
        }
        if (user != null) {
            CurrentUser.setInfo(user);
        }
        // 补全其他信息
        this.completionCurrentUser(request);
        // 4. 缓存全局分布式事务ID
        // 参考：com.alibaba.cloud.seata.web.SeataHandlerInterceptor
        String xid = RootContext.getXID();
        String rpcXid = request.getHeader(RootContext.KEY_XID);
        if (xid == null && rpcXid != null) {
            RootContext.bind(rpcXid);
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception e) throws Exception {
        super.afterCompletion(request, response, handler, e);
        // 清理seata的事务ID
        // 参考：com.alibaba.cloud.seata.web.SeataHandlerInterceptor
        String rpcXid = request.getHeader(RootContext.KEY_XID);
        if (StringUtil.isNull(rpcXid)) {
            return;
        }
        String unbindXid = RootContext.unbind();
        if (log.isDebugEnabled()) {
            log.debug("unbind {} from RootContext", unbindXid);
        }
        if (!rpcXid.equalsIgnoreCase(unbindXid)) {
            log.warn("xid in change during RPC from {} to {}", rpcXid, unbindXid);
            if (unbindXid != null) {
                RootContext.bind(unbindXid);
                log.warn("bind {} back to RootContext", unbindXid);
            }
        }
    }

    private Long parseToLong(String val) {
        try {
            return Long.parseLong(val);
        } catch (Exception e) {
            log.warn("转换字符串[{}]为Long时出�?", val, e);
            return null;
        }
    }

    /**
     * 网关签名校验
     */
    protected boolean checkGatewayParams(HttpServletRequest request) throws Exception {
        // 网关签名信息
        String reqGatewaySign = request.getHeader(CurrentUser.GATEWAY_SIGN);
        if (StringUtil.isNull(reqGatewaySign)) {
            log.warn("网关签名为空.");
            return false;
        }
        String timestamp = request.getHeader(CurrentUser.GATEWAY_TIME);
        if (StringUtil.isNull(timestamp)) {
            log.warn("网关时间为空.");
            return false;
        }
        // 客户端签名信�?
        String reqAppSign = WebUtil.getHeader(CurrentUser.SIGN_APP, request);
        if (StringUtil.isNull(reqAppSign)) {
            log.warn("应用签名为空.");
            return false;
        }
        // 缓存全局通信秘钥
        String globalSecret = getGlobalSecret();
        // 网关签名校验
        String clcGlobalSecret = SignParamUtil.sign(reqAppSign + timestamp, globalSecret);
        if (!reqGatewaySign.equalsIgnoreCase(clcGlobalSecret)) {
            log.warn("网关签名不匹配，原文[{}]，计算的密文[{}]，传入的密文[{}]", reqAppSign + timestamp, clcGlobalSecret, reqGatewaySign);
            return false;
        }
        return true;
    }

    /**
     * 获取用户信息：从网关的请求头提取
     * 优点：减少请求的次数，减少服务器压力
     */
    private CurrentUser.UserInfo getUserInfo4Request(HttpServletRequest request) {
        CurrentUser.UserInfo user = CurrentUser.getInfo();
        // 用户ID
        String uid = request.getHeader(CurrentUser.USER_ID);
        if (StringUtil.notNull(uid)) {
            user.setUserId(uid);
        }
        // 登录名称
        String uname = request.getHeader(CurrentUser.USER_NAME);
        if (StringUtil.notNull(uname)) {
            user.setUserName(URLDecoder.decode(uname, StandardCharsets.UTF_8));
        }
        // 真实姓名
        String rname = request.getHeader(CurrentUser.USER_REAL_NAME);
        if (StringUtil.notNull(rname)) {
            user.setRealName(URLDecoder.decode(rname, StandardCharsets.UTF_8));
        }
        // 用户尊称
        String nname = request.getHeader(CurrentUser.USER_NICK_NAME);
        if (StringUtil.notNull(nname)) {
            user.setNickName(URLDecoder.decode(nname, StandardCharsets.UTF_8));
        }
        // 用户所属机�?
        String institue = request.getHeader(CurrentUser.USER_INSTITUTE_ID);
        if (StringUtil.notNull(institue)) {
            user.setInstituteId(institue);
        }
        // 用户所属科�?
        String officeId = request.getHeader(CurrentUser.USER_OFFICE_ID);
        if (StringUtil.notNull(officeId)) {
            user.setOfficeId(officeId);
        }
        // 用户当前角色
        String roleId = request.getHeader(CurrentUser.USER_ROLE_ID);
        if (StringUtil.notNull(roleId)) {
            user.setCurrentRoleId(roleId);
        }
        // 用户所有角�?
        String roles = request.getHeader(CurrentUser.USER_ROLE_LIST);
        if (StringUtil.notNull(roles)) {
            user.setRoleList(JsonUtil.deserializeAsList(roles, String.class));
        }
        // 用户管理的科�?
        String offices = request.getHeader(CurrentUser.USER_OFFICE_LIST);
        if (StringUtil.notNull(offices)) {
            user.setOfficeList(JsonUtil.deserializeAsList(offices, String.class));
        }
        // 所属的用户�?
        String groups = request.getHeader(CurrentUser.USER_GROUP_LIST);
        if (StringUtil.notNull(groups)) {
            user.setGroupList(JsonUtil.deserializeAsList(groups, String.class));
        }
        return user;
    }

    /**
     * 获取用户信息：从Redis获取
     * 优点：数据更准确
     */
    private CurrentUser.UserInfo getUserInfo4Redis(HttpServletRequest request) {
        String token = WebUtil.getHeader(CurrentUser.TOKEN, request);
        if (StringUtil.notNull(token)) {
            // 2025-03-05：此处不能用RedisUtil.getLocalCache，因为后续有setNonce等赋值操作，若从缓存获取，容易影响前一个请�?
            return RedisUtil.get(BaseCacheKey.USER_INFO_PREFIX + token, CurrentUser.UserInfo.class);
        }
        return null;
    }
}
