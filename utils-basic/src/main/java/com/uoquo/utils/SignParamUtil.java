/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils;

import com.uoquo.utils.crypto.MD5;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.util.Map;
import java.util.TreeMap;

/**
 * 描述：数据签名工具. <br>
 * 日期：2018-01-18 16:28 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-18     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class SignParamUtil {
    private static final Logger log = LoggerFactory.getLogger(SignParamUtil.class);

    /**
     * 请求参数签名计算.<br>
     * 备注：主要用于feign拦截器<br>
     * @param appid    应用ID
     * @param secret   应用密钥
     * @param token    授权token
     * @param language 语言
     * @param nonce    请求随机数
     * @param deviceId 设备识别码
     * @param time     请求时间戳
     * @param param    请求参数
     * @param body     请求体
     * @return boolean
     * @author xuhz
     */
    public static String sign(String appid, String secret, String token, String language, String nonce, String deviceId, String time, TreeMap<String, String> param, byte[] body) {
        if (StringUtil.isNull(secret)) {
            throw new IllegalArgumentException("应用密钥为空");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // 1. 拼入appid
            log.debug("param sign: appid={}", appid);
            if (StringUtil.notNull(appid)) {
                out.write(appid.getBytes());
            }
            // 2. 拼入token
            log.debug("param sign: token={}", token);
            if (StringUtil.notNull(token)) {
                out.write(token.getBytes());
            }
            // 3. 拼入language
            log.debug("param sign: language={}", language);
            if (StringUtil.notNull(language)) {
                out.write(language.getBytes());
            }
            // 4. 拼入nonce
            log.debug("param sign: nonce={}", nonce);
            if (StringUtil.notNull(nonce)) {
                out.write(nonce.getBytes());
            }
            // 4. 拼入deviceId
            log.debug("param sign: deviceId={}", deviceId);
            if (StringUtil.notNull(deviceId)) {
                out.write(deviceId.getBytes());
            }
            // 4. 拼入timestamp
            log.debug("param sign: time={}", time);
            if (StringUtil.notNull(time)) {
                out.write(time.getBytes());
            }
            // 5. 拼入请求的params
            if (param != null) {
                log.debug("param sign: param={}", param);
                for (Map.Entry<String, String> entry : param.entrySet()) {
                    out.write(entry.getKey().getBytes());
                    out.write(entry.getValue().getBytes());
                }
            }
            // 6. 拼入请求体
            if ((body != null) && (body.length > 0)) {
                out.write(body);
            }
            // 7. 拼入secret
            out.write(secret.getBytes());
            log.debug("param sign before: {}", out.toString());
            String sigStr = MD5.encrypt(out.toByteArray());
            log.debug("param sign after: {}", sigStr );
            return sigStr;
        } catch (Exception e) {
            log.warn("calc params signature error. {}", out.toString(), e);
            return null;
        } finally {
            close(out);
        }
    }

    /**
     * 全局通信签名.<br>
     * 备注：主要用于网关到服务，服务到服务之间的参数签名.
     * @param signature 参数签名数据
     * @param secret    全局密钥
     */
    public static String sign(String signature, String secret) {
        try {
            return MD5.encrypt(signature + secret);
        } catch (Exception e) {
            log.warn("calc global signature error. {}", signature, e);
            return null;
        }
    }

    private static void close(Closeable obj) {
        if (obj != null) {
            try {
                obj.close();
            } catch (Exception e) {
                // do nothing
            }
        }
    }
}
