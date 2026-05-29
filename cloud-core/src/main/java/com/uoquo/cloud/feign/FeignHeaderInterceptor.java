/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.feign;

import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.SignParamUtil;

import feign.RequestInterceptor;
import feign.RequestTemplate;

import org.apache.seata.core.context.RootContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;

/**
 * 描述：设置请求头信息. <br>
 * 说明：主要用来传递当前的请求用户信息，便于后边做数据权限处理，并对参数进行签名. <br>
 * 原理：将当前登陆用户信息（主要是：user id, session id, role list）放到请求的header中，
 *      通过全局拦截器将读取header中的这些数据，放到当前线程（ThreadLocal）中，在后续需要用到的地方读取使用<br>
 * 日期：2018-05-15 18:58 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-05-15     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class FeignHeaderInterceptor implements RequestInterceptor {
    private static final Logger log = LoggerFactory.getLogger(FeignHeaderInterceptor.class);

    @Override
    public void apply(RequestTemplate template) {
        // 请求的FORM参数
        TreeMap<String, String> param = new TreeMap<>();
        Map<String, Collection<String>> query = template.queries();
        query.forEach((k, v) -> {
            if (v == null) {
                param.put(k, null);
                return;
            }
            List<String> list = new ArrayList<>();
            v.forEach( item -> {
                try {
                    list.add(URLDecoder.decode(item, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    log.warn("feign URLDecoder.decode({}) error.", item, e);
                }
            });
            if (list.isEmpty()) {
                param.put(k, "");
            } else if (list.size() == 1) {
                param.put(k, list.getFirst());
            } else {
                param.put(k, JsonUtil.serialize(list));
            }
        });
        // 请求体
        //TODO 需测试传递文件时的参数
        byte[] body = template.body();
        // 参数签名
        String timestamp = Long.toString(Clock.systemUTC().millis());
        String signParams = SignParamUtil.sign(CurrentUser.getAppkey(), CurrentUser.getAppSecret(), CurrentUser.getToken(),
                CurrentUser.getLanguage(), CurrentUser.getNonce(), CurrentUser.getDeviceId(), timestamp, param, body);
        if (signParams != null) {
            template.header(CurrentUser.SIGN_APP, signParams);
        } else {
            log.error("feign calc params signature error. appid={}, token={}, param={}, body={}", CurrentUser.getAppkey(), CurrentUser.getToken(), param, body);
        }
        // 网关签名
        String signGlobal = SignParamUtil.sign(signParams + timestamp, CurrentUser.getGlobalSecret());
        if (signGlobal != null) {
            template.header(CurrentUser.GATEWAY_SIGN, signGlobal);
            template.header(CurrentUser.GATEWAY_TIME, timestamp);
        } else {
            log.error("feign calc global signature error. signParams={}", signParams);
        }
        // feign签名（用于跳过登录拦截）
        String feignSign = SignParamUtil.sign(signParams + signGlobal, CurrentUser.getGlobalSecret());
        if (feignSign != null) {
            template.header(CurrentUser.FEIGN_SIGN, feignSign);
        } else {
            log.warn("feign calc feign signature error. signParams={} signGlobal={}", signParams, signGlobal);
        }
        // 当前用户信息传递
        putUserInfo(template);
        template.header(CurrentUser.TIME, timestamp);
        // 分布式事物ID
        String xid = RootContext.getXID();
        if (StringUtil.notNull(xid)) {
            template.header(RootContext.KEY_XID, xid);
        }
    }

    /**
     * 放入当前用户对象
     */
    protected void putUserInfo(RequestTemplate template) {
        // appid
        if (StringUtil.notNull(CurrentUser.getAppkey())) {
            template.header(CurrentUser.APPID, CurrentUser.getAppkey());
        }
        // appType
        if (StringUtil.notNull(CurrentUser.getAppType())) {
            template.header(CurrentUser.APP_TYPE, CurrentUser.getAppType());
        }
        // appVersion
        if (StringUtil.notNull(CurrentUser.getAppVersion())) {
            template.header(CurrentUser.APP_VERSION, CurrentUser.getAppVersion());
        }

        // token
        if (StringUtil.notNull(CurrentUser.getToken())) {
            template.header(CurrentUser.TOKEN, CurrentUser.getToken());
        }
        // language
        if (StringUtil.notNull(CurrentUser.getLanguage())) {
            template.header(CurrentUser.USER_LANGUAGE, CurrentUser.getLanguage());
        }
        // nonce
        if (StringUtil.notNull(CurrentUser.getNonce())) {
            template.header(CurrentUser.NONCE, CurrentUser.getNonce());
        }
        // deviceId
        if (StringUtil.notNull(CurrentUser.getDeviceId())) {
            template.header(CurrentUser.DEVICE_ID, CurrentUser.getDeviceId());
        }
        // 请求ID
        if (StringUtil.notNull(CurrentUser.getTraceId())) {
            template.header(CurrentUser.TRACE_ID, CurrentUser.getTraceId());
        }
        // 客户端IP
        if (StringUtil.notNull(CurrentUser.getClientIp())) {
            template.header(CurrentUser.CLIENT_IP, CurrentUser.getClientIp());
        }

        CurrentUser.UserInfo user = CurrentUser.getInfo();
        // 用户ID
        if (StringUtil.notNull(user.getUserId())) {
            template.header(CurrentUser.USER_ID, user.getUserId());
        }
        // 登录名称
        if (StringUtil.notNull(user.getUserName())) {
            template.header(CurrentUser.USER_NAME, URLEncoder.encode(user.getUserName(), StandardCharsets.UTF_8));
        }
        // 真实姓名
        if (StringUtil.notNull(user.getRealName())) {
            template.header(CurrentUser.USER_REAL_NAME, URLEncoder.encode(user.getRealName(), StandardCharsets.UTF_8));
        }
        // 用户尊称
        if (StringUtil.notNull(user.getNickName())) {
            template.header(CurrentUser.USER_NICK_NAME, URLEncoder.encode(user.getNickName(), StandardCharsets.UTF_8));
        }
        // 用户所属机构
        if (StringUtil.notNull(user.getInstituteId())) {
            template.header(CurrentUser.USER_INSTITUTE_ID, String.valueOf(user.getInstituteId()));
        }
        // 用户所属科室
        if (StringUtil.notNull(user.getOfficeId())) {
            template.header(CurrentUser.USER_OFFICE_ID, String.valueOf(user.getOfficeId()));
        }
        // 用户当前角色
        if (StringUtil.notNull(user.getCurrentRoleId())) {
            template.header(CurrentUser.USER_ROLE_ID, String.valueOf(user.getCurrentRoleId()));
        }
        // WARNING：以下三个LIST可能导致请求header头超大，出现400的错误风险，请谨慎使用
        // 用户所有角色
        if ((user.getRoleList() != null) && !user.getRoleList().isEmpty()) {
            template.header(CurrentUser.USER_ROLE_LIST, JsonUtil.serialize(user.getRoleList()));
        }
        // 用户管理的科室
        if ((user.getOfficeList() != null) && !user.getOfficeList().isEmpty()) {
            template.header(CurrentUser.USER_OFFICE_LIST, JsonUtil.serialize(user.getOfficeList()));
        }
        // 所属的用户组
        if ((user.getGroupList() != null) && !user.getGroupList().isEmpty()) {
            template.header(CurrentUser.USER_GROUP_LIST, JsonUtil.serialize(user.getGroupList()));
        }
    }
}
