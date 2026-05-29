/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * 描述：当前线程的用户信息. <br>
 * 背景：主要用于同一请求中可以随时获取当前用户信息. <br>
 * 原理：当请求进入时，从session、redis、请求头等渠道中获取相关数据放入ThreadLocal. <br>
 * 使用：
 * <ul>
 *   <li>独立应用
 *     <ul>
 *       <li>全局拦截器：从session中获取，并保存到ThreadLocal</li>
 *     </ul>
 *   </li>
 *   <li>微服务端
 *     <ul>
 *       <li>全局拦截器：从request的header中获取，并保存到ThreadLocal</li>
 *       <li>feign拦截器：从ThreadLocal获取，添加到request的header</li>
 *       <li>MyBatis拦截器：从ThreadLocal获取，添加数据权限过滤</li>
 *     </ul>
 *   </li>
 * </ul>
 * 日期：2018-05-15 19:35 <br>
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
public class CurrentUser {
    // 客户端传入
    public static final String APPID        = "appid";
    public static final String TOKEN        = "token";
    public static final String NONCE        = "nonce";
    public static final String TIME         = "timestamp";
    public static final String DEVICE_ID    = "device-id";
    public static final String USER_LANGUAGE= "user-language";
    public static final String SIGN_APP     = "signature-app";
    // 网关签名及时间戳
    public static final String GATEWAY_SIGN = "gateway-signature";
    public static final String GATEWAY_TIME = "gateway-timestamp";
    // FEIGN签名
    public static final String FEIGN_SIGN   = "feign-signature";
    // 其他缓存
    public static final String APP_TYPE     = "appid-type";
    public static final String APP_VERSION  = "appid-version";
    public static final String TRACE_ID     = "trace-id";
    public static final String CLIENT_IP    = "client-ip";
    // 用户缓存
    public static final String USER_ID           = "login-user-id";
    public static final String USER_NAME         = "login-user-name";
    public static final String USER_REAL_NAME    = "login-real-name";
    public static final String USER_NICK_NAME    = "login-nick-name";
    public static final String USER_INSTITUTE_ID = "login-institute-id";
    public static final String USER_OFFICE_ID    = "login-office-id";
    public static final String USER_OFFICE_LIST  = "login-office-list";
    public static final String USER_GROUP_LIST   = "login-group-list";
    public static final String USER_ROLE_ID      = "login-role-id";
    public static final String USER_ROLE_LIST    = "login-role-list";

    // 存储当前线程的用户信息
    // 在响应式编程（如Gateway）或多线程中使用时，可考虑阿里的 TransmittableThreadLocal
    private static final ThreadLocal<User> local = ThreadLocal.withInitial(User::new);

    /**
     * 清空缓存数据.
     */
    public static void clear() {
        local.remove();
        local.set(new User());
    }

    /**
     * 获取当前请求的应用ID.
     * @return 当前请求的应用ID
     */
    public static String getAppkey() {
        return local.get().getAppkey();
    }

    /**
     * 设置当前请求的应用ID.
     * @param appid 当前请求的应用ID
     */
    public static void setAppkey(String appid) {
        local.get().setAppkey(appid);
    }

    /**
     * 获取当前请求的授权token.
     * @return 当前请求的授权token
     */
    public static String getToken() {
        return local.get().getToken();
    }

    /**
     * 设置当前请求的授权token.
     * @param token 当前请求的授权token
     */
    public static void setToken(String token) {
        local.get().setToken(token);
    }

    /**
     * 获取当前请求的应用类型.<br>
     * 注意：主要记录客户端来源，仅用于请求其他服务时feign签名，不进行上下文传输！！
     * @return 当前请求的应用类型
     */
    public static String getAppType() {
        return local.get().getAppType();
    }

    /**
     * 设置当前请求的应用类型.
     * @param appType 当前请求的应用类型
     */
    public static void setAppType(String appType) {
        local.get().setAppType(appType);
    }

    /**
     * 获取当前请求的应用密钥.<br>
     * 注意：仅用于请求其他服务时feign签名，不进行上下文传输！！
     * @return 当前请求的应用密钥
     */
    public static String getAppSecret() {
        return local.get().getAppSecret();
    }

    /**
     * 设置当前请求的应用密钥.<br>
     * 注意：仅用于请求其他服务时feign签名，不进行上下文传输！！
     * @param secret 当前请求的应用密钥
     */
    public static void setAppSecret(String secret) {
        local.get().setAppSecret(secret);
    }

    /**
     * 获取全局通信密钥.<br>
     * 注意：仅用于请求其他服务时feign签名，不进行上下文传输！！
     * @return 全局通信密钥
     */
    public static String getGlobalSecret() {
        return local.get().getGlobalSecret();
    }

    /**
     * 设置全局通信密钥.<br>
     * 注意：仅用于请求其他服务时feign签名，不进行上下文传输！！
     * @param secret 全局通信密钥
     */
    public static void setGlobalSecret(String secret) {
        local.get().setGlobalSecret(secret);
    }

    /**
     * 获取当前请求的APP版本.
     * @return 当前请求的APP版本
     */
    public static String getAppVersion() {
        return local.get().getAppVersion();
    }

    /**
     * 设置当前请求的APP版本.
     * @param appVersion 当前请求的APP版本
     */
    public static void setAppVersion(String appVersion) {
        local.get().setAppVersion(appVersion);
    }

    /**
     * 获取当前请求的来源IP.
     * @return 当前请求的来源IP
     */
    public static String getClientIp() {
        return local.get().getClientIp();
    }

    /**
     * 设置当前请求的授来源IP.
     * @param clientIp 当前请求的来源IP
     */
    public static void setClientIp(String clientIp) {
        local.get().setClientIp(clientIp);
    }

    /**
     * 获取当前请求的随机数（发起方传入）.
     * @return 当前请求的随机数
     */
    public static String getNonce() {
        return local.get().getNonce();
    }

    /**
     * 设置当前请求的随机数（发起方传入）.
     * @param nonce 当前请求的随机数
     */
    public static void setNonce(String nonce) {
        local.get().setNonce(nonce);
    }

    /**
     * 获取设备唯一标识（发起方传入）.
     * 因浏览器权限控制，WEB时该标识不准确
     * @return 当前请求的随机数
     */
    public static String getDeviceId() {
        return local.get().getDeviceId();
    }

    /**
     * 设置设备唯一标识（发起方传入）.
     * @param deviceId 设备唯一标识
     */
    public static void setDeviceId(String deviceId) {
        local.get().setDeviceId(deviceId);
    }

    /**
     * 获取当前请求的唯一标识（内部生成）.
     * @return 当前请求的唯一标识
     */
    public static String getTraceId() {
        return local.get().getTraceId();
    }

    /**
     * 设置当前请求的唯一标识（内部生成）.
     * @param traceId 当前请求的唯一标识
     */
    public static void setTraceId(String traceId) {
        local.get().setTraceId(traceId);
    }

    /**
     * 获取当前请求的语言标识.
     * @return 当前请求的语言标识
     */
    public static String getLanguage() {
        return local.get().getLanguage();
    }

    /**
     * 设置当前请求的语言标识.
     * @param language 当前请求的语言标识
     */
    public static void setLanguage(String language) {
        local.get().setLanguage(language);
    }

    /**
     * 获取当前用户信息.
     * @return 当前用户信息
     */
    public static UserInfo getInfo() {
        return local.get().getInfo();
    }

    /**
     * 设置当前用户信息（主要用于单应用，从session获取的信息）.
     */
    public static void setInfo(UserInfo userInfo) {
        local.get().setInfo(Objects.requireNonNullElseGet(userInfo, UserInfo::new));
    }

    /**
     * AppInfo简单信息
     */
    public static class AppInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = -202941401325905304L;
        private String appkey;
        private String secret;
        private String type;
        private String instituteId;

        public AppInfo() {
        }

        public static long getSerialVersionUID() {
            return serialVersionUID;
        }

        public String getAppkey() {
            return this.appkey;
        }

        public void setAppkey(String appkey) {
            this.appkey = appkey;
        }

        public String getSecret() {
            return this.secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getType() {
            return this.type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getInstituteId() {
            return this.instituteId;
        }

        public void setInstituteId(String instituteId) {
            this.instituteId = instituteId;
        }
    }

    /**
     * 登录后不会变更的用户信息
     */
    public static class UserInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 6404705464365123060L;
        private String userId;    // 用户ID
        private String userName;  // 用户的登录名称
        private String realName;  // 用户的真实姓名
        private String nickName;  // 用户的昵称
        private Integer expires = 1800;   // 过期时间（秒，默认30分钟）
        private String instituteId;       // 所属机构ID
        private String officeId;          // 所属科室ID
        private List<String> officeList;  // 管理的科室
        private List<String> groupList;   // 所属用户组
        private String currentRoleId;     // 当前角色
        private List<String> roleList;    // 所有角色

        public UserInfo() {
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getRealName() {
            return realName;
        }

        public void setRealName(String realName) {
            this.realName = realName;
        }

        public String getNickName() {
            return nickName;
        }

        public void setNickName(String nickName) {
            this.nickName = nickName;
        }

        public Integer getExpires() {
            return this.expires == null ? 1800 : expires;
        }

        public void setExpires(Integer expires) {
            this.expires = expires;
        }

        public String getInstituteId() {
            return instituteId;
        }

        public void setInstituteId(String instituteId) {
            this.instituteId = instituteId;
        }

        public String getOfficeId() {
            return officeId;
        }

        public void setOfficeId(String officeId) {
            this.officeId = officeId;
        }

        public List<String> getOfficeList() {
            return officeList;
        }

        public void setOfficeList(List<String> officeList) {
            this.officeList = officeList;
        }

        public List<String> getGroupList() {
            return groupList;
        }

        public void setGroupList(List<String> groupList) {
            this.groupList = groupList;
        }

        public String getCurrentRoleId() {
            return currentRoleId;
        }

        public void setCurrentRoleId(String currentRoleId) {
            this.currentRoleId = currentRoleId;
        }

        public List<String> getRoleList() {
            return roleList;
        }

        public void setRoleList(List<String> roleList) {
            this.roleList = roleList;
        }
    }

    /**
     * 临时存储的用户关键信息.
     */
    private static class User  implements Serializable {
        @Serial
        private static final long serialVersionUID = 1615641063861688291L;
        private String appkey;       // 授权应用key
        private String token;        // 授权token（会话标识）
        private String appSecret;    // 授权应用密钥（仅缓存，方便同一会话使用，不进行上下文传输）
        private String globalSecret; // 全局通信密钥（仅缓存，方便同一会话使用，不进行上下文传输）
        // 客户端传入
        private String clientIp;     // 用户客户端IP
        private String language;     // 客户端语言
        private String nonce;        // 请求随机数（发起方）
        private String deviceId;     // 设备标识（发起方）
        // 服务端内部
        private String traceId;      // 用户请求ID（服务端）
        private String appType;      // APP类型
        private String appVersion;   // APP版本

        private UserInfo info = new UserInfo(); // 用户信息

        public User() {
        }

        public String getAppkey() {
            return appkey;
        }

        public void setAppkey(String appkey) {
            this.appkey = appkey;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getGlobalSecret() {
            return globalSecret;
        }

        public void setGlobalSecret(String globalSecret) {
            this.globalSecret = globalSecret;
        }

        public String getClientIp() {
            return clientIp;
        }

        public void setClientIp(String clientIp) {
            this.clientIp = clientIp;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getNonce() {
            return nonce;
        }

        public void setNonce(String nonce) {
            this.nonce = nonce;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        public String getAppType() {
            return appType;
        }

        public void setAppType(String appType) {
            this.appType = appType;
        }

        public String getAppVersion() {
            return appVersion;
        }

        public void setAppVersion(String appVersion) {
            this.appVersion = appVersion;
        }

        public UserInfo getInfo() {
            return info;
        }

        public void setInfo(UserInfo info) {
            this.info = info;
        }
    }
}
