/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.json.jackson;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.uoquo.annotation.json.Sensitive;
import com.uoquo.utils.Config;
import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.crypto.AES;
import com.uoquo.utils.crypto.RSA;
import com.uoquo.utils.crypto.SM2;
import com.uoquo.utils.crypto.SM4;
import com.uoquo.utils.crypto.TimeStepCryptoUtil;
import com.uoquo.utils.spring.RedisUtil;

/**
 * 自定义解密反序列化器，与 {@link SensitiveSerializer} 配对使用。
 *
 * <p>对标注 {@link Sensitive} 注解（且类型为 {@code CRYPT_*}）的 String 字段，
 * 在反序列化时尝试还原密文为明文。</p>
 *
 * <h3>处理策略</h3>
 * <ul>
 *   <li>脱敏类型（NAME / ID_CARD / PHONE / EMAIL / BANK_CARD / PASSWORD / ADDRESS / CUSTOM / DEFAULT）：
 *       序列化时已做不可逆脱敏，反序列化保留原始字符串。</li>
 *   <li>CRYPT_AES / CRYPT_SM4：使用注解上的 key 或配置的对称密钥执行解密。</li>
 *   <li>CRYPT_TAES / CRYPT_TSM4：基于时间片的密钥解密；先尝试当前时间片，再尝试上一时间片，
 *       以容忍跨片传输的时差。</li>
 *   <li>CRYPT_RSA：使用配置的 RSA 私钥解密（标准公钥加密的反向操作）。未配置私钥时降级为 TAES 解密。</li>
 *   <li>CRYPT_SM2：使用配置的 SM2 私钥解密（公钥加密的反向操作）。未配置私钥时降级为 TSM4 解密。</li>
 * </ul>
 *
 * <p>任何解密失败均返回原始字符串（与序列化端"失败时返回原值"的行为对齐），仅记录警告日志。</p>
 *
 * @author xuhz
 */
public class SensitiveDeserializer extends JsonDeserializer<String> implements ContextualDeserializer {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** 字段上的注解，由 {@link #createContextual(DeserializationContext, BeanProperty)} 注入；为 null 时按普通 String 处理。 */
    private final Sensitive annotation;

    /** 对称密钥（AES / SM4 共用），延迟初始化，使用双重校验锁 + volatile 保证可见性. */
    private volatile String securityAesKey;

    /** RSA 私钥，延迟初始化，使用双重校验锁 + volatile 保证可见性. */
    private volatile String securityPrivateKey;

    public SensitiveDeserializer() {
        this(null);
    }

    private SensitiveDeserializer(Sensitive annotation) {
        this.annotation = annotation;
    }

    /**
     * 在 ObjectMapper 初始化时为每个目标字段创建一个绑定了注解的实例。
     * <p>仅当声明类带有类级别 {@link Sensitive} 注解、且字段也带有 {@link Sensitive} 注解时启用解密；
     * 否则返回无注解实例，按普通 String 处理。</p>
     */
    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        if (property == null || property.getMember() == null) {
            return this;
        }
        // 20260521：优化为只判断字段的注解
        // Class<?> declaringClass = property.getMember().getDeclaringClass();
        // if (declaringClass.getAnnotation(Sensitive.class) == null) {
        //     return this;
        // }
        Sensitive fieldAnn = property.getAnnotation(Sensitive.class);
        if (fieldAnn == null) {
            return this;
        }
        return new SensitiveDeserializer(fieldAnn);
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        String raw = p.getValueAsString();
        if (raw == null || raw.isEmpty() || annotation == null) {
            return raw;
        }
        // Feign 微服务调用时跳过解密，返回原始值
        if (CurrentUser.isFeignRequest()) {
            return raw;
        }
        try {
            String plain = reverseSensitive(raw, annotation);
            if (log.isDebugEnabled() && !raw.equals(plain)) {
                log.debug("自定义解密：type=[{}], 原值长度={}, 解密后长度={}", annotation.type(), raw.length(), plain.length());
            }
            return plain;
        } catch (Exception e) {
            log.warn("自定义反序列化解密失败：type={}, msg={}", annotation.type(), e.getMessage());
            return raw;
        }
    }

    /**
     * 根据注解类型尝试反向处理；脱敏类型保留原值。
     */
    private String reverseSensitive(String value, Sensitive ann) {
        switch (ann.type()) {
            // 1. 脱敏类型 —— 已不可逆，原样返回
            case NAME:
            case ID_CARD:
            case PHONE:
            case EMAIL:
            case BANK_CARD:
            case PASSWORD:
            case ADDRESS:
            case CUSTOM:
            case DEFAULT:
                return value;

            // 2. 对称解密
            case CRYPT_AES: {
                String key = resolveAesKey(ann);
                if (StringUtil.isNull(key)) {
                    return value;
                }
                try {
                    return AES.decrypt(value, key);
                } catch (Exception e) {
                    log.warn("AES 解密失败：{}", e.getMessage());
                    return value;
                }
            }
            case CRYPT_SM4: {
                String key = resolveAesKey(ann);
                if (StringUtil.isNull(key)) {
                    return value;
                }
                try {
                    return SM4.decrypt(value, key);
                } catch (Exception e) {
                    log.warn("SM4 解密失败：{}", e.getMessage());
                    return value;
                }
            }

            // 3. 时间片密钥
            case CRYPT_TAES:
                return tryTimeStepDecrypt(value, false);
            case CRYPT_TSM4:
                return tryTimeStepDecrypt(value, true);

            // 4. RSA：私钥解密；未配置降级为 TAES
            case CRYPT_RSA: {
                String priKey = resolvePrivateKey(ann);
                if (StringUtil.isNull(priKey)) {
                    return tryTimeStepDecrypt(value, false);
                }
                try {
                    return RSA.decrypt(value, priKey);
                } catch (Exception e) {
                    log.warn("RSA 解密失败，降级为TAES解密：{}", e.getMessage());
                    return tryTimeStepDecrypt(value, false);
                }
            }

            // 5. SM2：私钥解密；未配置降级为 TSM4
            case CRYPT_SM2: {
                String priKey = resolvePrivateKey(ann);
                if (StringUtil.isNull(priKey)) {
                    return tryTimeStepDecrypt(value, true);
                }
                try {
                    return SM2.decrypt(value, priKey);
                } catch (Exception e) {
                    log.warn("SM2 解密失败，降级为TSM4解密：{}", e.getMessage());
                    return tryTimeStepDecrypt(value, true);
                }
            }

            default:
                return value;
        }
    }

    private String resolveAesKey(Sensitive ann) {
        if (StringUtil.notNull(ann.key())) {
            return ann.key();
        }
        checkAesKey();
        return securityAesKey;
    }

    private String resolvePrivateKey(Sensitive ann) {
        if (StringUtil.notNull(ann.key())) {
            return ann.key();
        }
        checkPrivateKey();
        return securityPrivateKey;
    }

    /**
     * 时间片解密：先尝试当前时间片，再尝试上一时间片，覆盖跨片网络传输场景。
     *
     * @param value 密文（hex 编码）
     * @param sm4   true 使用 SM4，false 使用 AES
     */
    private String tryTimeStepDecrypt(String value, boolean sm4) {
        return sm4 ? TimeStepCryptoUtil.decryptTSM4(value) : TimeStepCryptoUtil.decryptTAES(value);
    }



    /**
     * AES KEY 初始化（双重校验锁）.
     */
    private void checkAesKey() {
        if (securityAesKey != null) {
            return;
        }
        synchronized (getClass()) {
            if (securityAesKey != null) {
                return;
            }
            String key = RedisUtil.get("security.aes.key", String.class);
            if (StringUtil.isNull(key)) {
                key = Config.getString("app.security.aes.key", "");
            }
            securityAesKey = key;
        }
    }

    /**
     * RSA / SM2 私钥初始化（双重校验锁）.
     */
    private void checkPrivateKey() {
        if (securityPrivateKey != null) {
            return;
        }
        synchronized (getClass()) {
            if (securityPrivateKey != null) {
                return;
            }
            String key = RedisUtil.get("security.rsa.private-key", String.class);
            if (StringUtil.isNull(key)) {
                key = Config.getString("app.security.rsa.private-key", "");
            }
            securityPrivateKey = key;
        }
    }


}
