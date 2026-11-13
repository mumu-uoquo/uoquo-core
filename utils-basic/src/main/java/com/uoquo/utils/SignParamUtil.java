/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.uoquo.utils.crypto.SHA;

/**
 * 描述：数据签名工具. <br>
 * 约定：所有字符串统一按 UTF-8 转为字节参与签名，避免前后台默认字符集不一致导致中文签名不匹配. <br>
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
     * @param body     请求体（原始字节，按实际传输的字节参与签名，不做字符集转换）
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
                out.write(appid.getBytes(StandardCharsets.UTF_8));
            }
            // 2. 拼入token
            log.debug("param sign: token={}", token);
            if (StringUtil.notNull(token)) {
                out.write(token.getBytes(StandardCharsets.UTF_8));
            }
            // 3. 拼入language
            log.debug("param sign: language={}", language);
            if (StringUtil.notNull(language)) {
                out.write(language.getBytes(StandardCharsets.UTF_8));
            }
            // 4. 拼入nonce
            log.debug("param sign: nonce={}", nonce);
            if (StringUtil.notNull(nonce)) {
                out.write(nonce.getBytes(StandardCharsets.UTF_8));
            }
            // 4. 拼入deviceId
            log.debug("param sign: deviceId={}", deviceId);
            if (StringUtil.notNull(deviceId)) {
                out.write(deviceId.getBytes(StandardCharsets.UTF_8));
            }
            // 4. 拼入timestamp
            log.debug("param sign: time={}", time);
            if (StringUtil.notNull(time)) {
                out.write(time.getBytes(StandardCharsets.UTF_8));
            }
            // 5. 拼入请求的params（值为null时按空串处理，避免NPE导致签名失败）
            if (param != null) {
                log.debug("param sign: param={}", param);
                for (Map.Entry<String, String> entry : param.entrySet()) {
                    out.write(entry.getKey().getBytes(StandardCharsets.UTF_8));
                    if (entry.getValue() != null) {
                        out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
            // 6. 拼入请求体
            if ((body != null) && (body.length > 0)) {
                out.write(body);
            }
            // 7. 拼入secret
            out.write(secret.getBytes(StandardCharsets.UTF_8));
            if (log.isDebugEnabled()) {
                log.debug("param sign before: {}", out.toString(StandardCharsets.UTF_8));
            }
            String sigStr = StringUtil.byte2hex(SHA.sha256(out.toByteArray()));
            log.debug("param sign after: {}", sigStr );
            return sigStr;
        } catch (Exception e) {
            log.warn("calc params signature error. {}", out.toString(StandardCharsets.UTF_8), e);
            return null;
        } finally {
            close(out);
        }
    }

    /**
     * 全局通信签名.<br>
     * 备注：主要用于网关到服务，服务到服务之间的参数签名（字符串按 UTF-8 编码）.
     * @param signature 参数签名数据
     * @param secret    全局密钥
     */
    public static String sign(String signature, String secret) {
        try {
            return SHA.sha256(signature + secret);
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
