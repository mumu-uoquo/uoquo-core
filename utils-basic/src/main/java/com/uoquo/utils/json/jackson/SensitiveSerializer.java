/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.json.jackson;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.uoquo.annotation.json.Sensitive;
import com.uoquo.utils.Config;
import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.crypto.AES;
import com.uoquo.utils.crypto.RSA;
import com.uoquo.utils.crypto.SM4;
import com.uoquo.utils.crypto.TimeStepCryptoUtil;
import com.uoquo.utils.spring.RedisUtil;

/**
 * 自定义脱敏 / 加密序列化器，与 {@link SensitiveDeserializer} 配对使用。
 *
 * <p>对标注 {@link Sensitive} 注解的 String 字段，在序列化时根据
 * {@link  com.uoquo.annotation.json.SensitiveType} 执行脱敏（不可逆）或加密（可逆）。</p>
 *
 * <h3>处理策略</h3>
 * <ul>
 *   <li><b>脱敏类型</b>（NAME / ID_CARD / PHONE / EMAIL / BANK_CARD / PASSWORD / ADDRESS / CUSTOM / DEFAULT）：
 *       根据规则进行单向脱敏处理，返回原字符串经过保留前/后缀 + 替换字符的脱敏结果。</li>
 *   <li><b>对称加密</b>（CRYPT_AES / CRYPT_SM4）：使用注解上的 key 或配置的对称密钥执行加密。</li>
 *   <li><b>时间片加密</b>（CRYPT_TAES / CRYPT_TSM4）：以当前时间片（毫秒 / 时间片长度）作为密钥种子，
 *       对应 TOTP 类似的短时口令机制。</li>
 *   <li><b>RSA 私钥加密</b>（CRYPT_RSA）：使用配置的私钥进行加密；若未配置私钥则降级为 TAES。</li>
 *   <li><b>SM2</b>（CRYPT_SM2）：国密 SM2 算法不支持「私钥加密 / 公钥解密」，因此序列化端统一降级为 TSM4，
 *       对应反序列化端也按 TSM4 解密。</li>
 * </ul>
 *
 * <p>任何加密失败均会保留原值并记录 WARN 日志，避免局部加密异常阻断整个 JSON 序列化过程。</p>
 *
 * <h3>注解绑定</h3>
 * <p>实现 {@link ContextualSerializer}，由 Jackson 在 ObjectMapper 初始化阶段为每个目标字段
 * 通过 {@link #createContextual(SerializerProvider, BeanProperty)} 创建一个绑定了注解的实例，
 * 避免运行时通过反射查找字段注解的开销。</p>
 *
 * @author xuhz
 */
public class SensitiveSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** 默认替换字符（脱敏处理）. */
    private static final String DEFAULT_REPLACEMENT = "*";

    /** 字段上的注解，由 {@link #createContextual(SerializerProvider, BeanProperty)} 注入；为 null 时按普通 String 处理. */
    private final Sensitive annotation;

    /** 对称密钥（AES / SM4 共用），延迟初始化，使用双重校验锁 + volatile 保证可见性. */
    private volatile String securityAesKey;

    /** RSA 私钥，延迟初始化，使用双重校验锁 + volatile 保证可见性. */
    private volatile String securityPrivateKey;

    /**
     * 默认构造器，由 Jackson 通过 {@code addSerializer} 或 AnnotationIntrospector 注册时调用.
     */
    public SensitiveSerializer() {
        this(null);
    }

    /**
     * 私有构造器，仅在 {@link #createContextual(SerializerProvider, BeanProperty)} 中创建带注解的实例时使用.
     *
     * @param annotation 字段上的 {@link Sensitive} 注解；可为 null 表示无脱敏 / 加密配置
     */
    private SensitiveSerializer(Sensitive annotation) {
        this.annotation = annotation;
    }

    /**
     * 在 ObjectMapper 初始化时为每个目标字段创建一个绑定了注解的实例.
     * <p>仅当声明类带有类级别 {@link Sensitive} 注解、且字段也带有 {@link Sensitive} 注解时启用脱敏 / 加密；
     * 否则返回无注解实例，按普通 String 处理。</p>
     *
     * @param prov     Jackson 序列化上下文
     * @param property 目标属性（可能为字段、getter 等）
     * @return 绑定了注解的序列化器实例；若条件不满足则返回当前实例（无注解版本）
     */
    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
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
        return new SensitiveSerializer(fieldAnn);
    }

    /**
     * 序列化入口.
     * <p>当字段绑定了 {@link Sensitive} 注解时，按注解类型执行脱敏 / 加密；否则原样输出。</p>
     *
     * @param value    待序列化的字符串字段值
     * @param gen      Jackson JSON 生成器
     * @param provider Jackson 序列化上下文
     * @throws IOException 写入 JSON 失败时抛出
     */
    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        if (value.isEmpty() || annotation == null) {
            gen.writeString(value);
            return;
        }
        // Feign 微服务调用时跳过加解密，传递原始值
        if (CurrentUser.isFeignRequest()) {
            gen.writeString(value);
            return;
        }
        String processed = process(value, annotation);
        if (log.isDebugEnabled() && !value.equals(processed)) {
            log.debug("自定义脱敏：type=[{}], 原值长度={}, 处理后长度={}", annotation.type(), value.length(), processed.length());
        }
        gen.writeString(processed);
    }

    // ====================================================================
    // 脱敏 / 加密分发
    // ====================================================================

    /**
     * 根据注解类型执行脱敏或加密.
     *
     * @param value 原始值（已确保非 null）
     * @param ann   字段上的 {@link Sensitive} 注解
     * @return 脱敏 / 加密后的值
     */
    private String process(String value, Sensitive ann) {
        switch (ann.type()) {
            // 1. 脱敏（不可逆）
            case NAME:
                // 姓名脱敏（张*，张*三）
                return desensitizeString(value,
                        lengthValidate(ann.prefixLen(), 1),
                        lengthValidate(ann.suffixLen(), 1),
                        ann.replacement(), -1);
            case ID_CARD:
                // 证件号码脱敏（510********8283）
                return desensitizeString(value, 3, 4, ann.replacement(), 8);
            case PHONE:
                // 手机号码脱敏（138****000）
                return desensitizeString(value, 3, 3, ann.replacement(), 4);
            case EMAIL:
                // 邮箱脱敏（abc****@host）
                return value.replaceAll("(\\w{3}).*@(.*)", "$1****@$2");
            case BANK_CARD:
                // 银行卡号脱敏（123456********1234）
                return desensitizeString(value, 6, 4, ann.replacement(), -1);
            case PASSWORD:
                return "******";
            case ADDRESS:
                // 地址脱敏（北京市海淀区**号**栋）
                return value.replaceAll("\\d", ann.replacement());
            case CUSTOM: {
                if (ann.pattern().isEmpty()) {
                    return desensitizeString(value,
                            lengthValidate(ann.prefixLen(), 0),
                            lengthValidate(ann.suffixLen(), 0),
                            ann.replacement(), -1);
                }
                return value.replaceAll(ann.pattern(), ann.replacement());
            }

            // 2. 对称加密
            case CRYPT_AES:
                return tryEncryptAes(value, ann);
            case CRYPT_SM4:
                return tryEncryptSm4(value, ann);

            // 3. 时间片加密
            case CRYPT_TAES:
                return tryEncryptTimeStep(value, false);
            case CRYPT_TSM4:
                return tryEncryptTimeStep(value, true);

            // 4. RSA 私钥加密；未配置私钥时按注解约定降级为 TAES
            case CRYPT_RSA:
                return tryEncryptRsa(value, ann);

            // 5. SM2：国密 SM2 不支持私钥加密 / 公钥解密，统一降级为 TSM4，与反序列化端对齐
            case CRYPT_SM2:
                return tryEncryptTimeStep(value, true);

            // 6. 默认：全脱敏
            default:
                return desensitizeString(value,
                        lengthValidate(ann.prefixLen(), 0),
                        lengthValidate(ann.suffixLen(), 0),
                        ann.replacement(), -1);
        }
    }

    // ====================================================================
    // 加密尝试（失败时返回原值并打印 WARN）
    // ====================================================================

    /**
     * AES 加密；密钥优先取注解 key，否则取配置项；密钥缺失或加密失败则原样返回.
     *
     * @param value 待加密明文
     * @param ann   字段注解
     * @return 加密结果或原值
     */
    private String tryEncryptAes(String value, Sensitive ann) {
        String key = resolveAesKey(ann);
        if (StringUtil.isNull(key)) {
            return value;
        }
        try {
            return AES.encrypt(value, key);
        } catch (Exception e) {
            log.warn("AES 加密失败：{}", e.getMessage());
            return value;
        }
    }

    /**
     * SM4 加密；密钥优先取注解 key，否则取配置项；密钥缺失或加密失败则原样返回.
     *
     * @param value 待加密明文
     * @param ann   字段注解
     * @return 加密结果或原值
     */
    private String tryEncryptSm4(String value, Sensitive ann) {
        String key = resolveAesKey(ann);
        if (StringUtil.isNull(key)) {
            return value;
        }
        try {
            return SM4.encrypt(value, key);
        } catch (Exception e) {
            log.warn("SM4 加密失败：{}", e.getMessage());
            return value;
        }
    }

    /**
     * 时间片加密；以当前时间片作为密钥；加密失败原样返回.
     *
     * @param value 待加密明文
     * @param sm4   true 使用 SM4，false 使用 AES
     * @return 加密结果或原值
     */
    private String tryEncryptTimeStep(String value, boolean sm4) {
        return sm4 ? TimeStepCryptoUtil.encryptTSM4(value) : TimeStepCryptoUtil.encryptTAES(value);
    }

    /**
     * RSA 私钥加密；未配置私钥时按 {@link Sensitive} 注解约定降级为 TAES；加密失败原样返回.
     *
     * @param value 待加密明文
     * @param ann   字段注解
     * @return 加密结果或原值
     */
    private String tryEncryptRsa(String value, Sensitive ann) {
        String priKey = resolvePrivateKey(ann);
        if (StringUtil.isNull(priKey)) {
            // 注解 javadoc 约定：未配置秘钥则降级为 TAES
            return tryEncryptTimeStep(value, false);
        }
        try {
            return RSA.encryptByPrivateKey(value, priKey);
        } catch (Exception e) {
            log.warn("RSA 加密失败，降级为TAES加密：{}", e.getMessage());
            return tryEncryptTimeStep(value, false);
        }
    }

    // ====================================================================
    // 密钥解析
    // ====================================================================

    /**
     * 解析对称密钥：注解 key 优先，否则使用全局配置的 AES 密钥.
     *
     * @param ann 字段注解
     * @return 对称密钥；可能为 null / 空
     */
    private String resolveAesKey(Sensitive ann) {
        if (StringUtil.notNull(ann.key())) {
            return ann.key();
        }
        checkAesKey();
        return securityAesKey;
    }

    /**
     * 解析 RSA 私钥：注解 key 优先，否则使用全局配置的 RSA 私钥.
     *
     * @param ann 字段注解
     * @return RSA 私钥；可能为 null / 空
     */
    private String resolvePrivateKey(Sensitive ann) {
        if (StringUtil.notNull(ann.key())) {
            return ann.key();
        }
        checkPrivateKey();
        return securityPrivateKey;
    }

    // ====================================================================
    // 通用脱敏
    // ====================================================================

    /**
     * 长度参数有效性处理.
     *
     * @param value    用户配置值
     * @param defValue 默认值（当配置无效时使用）
     * @return 有效长度
     */
    private int lengthValidate(int value, int defValue) {
        if (value < 0 || value == Integer.MAX_VALUE) {
            return defValue;
        }
        return value;
    }

    /**
     * 通用脱敏处理.
     * <ul>
     *   <li>当原值长度不足前缀长度时，整体脱敏；</li>
     *   <li>当长度介于前缀和总保留长度之间时，保留前缀，其余部分脱敏；</li>
     *   <li>当长度超过总保留长度时，保留前后缀，中间部分按指定长度脱敏。</li>
     * </ul>
     *
     * @param value          待脱敏的字符串
     * @param prefixLen      前缀保留长度
     * @param suffixLen      后缀保留长度
     * @param replacement    掩码字符（为空时使用默认 *）
     * @param replacementLen 掩码字符长度（&lt;=0 时使用实际字符长度）
     * @return 脱敏结果
     */
    private String desensitizeString(String value, int prefixLen, int suffixLen,
                                     String replacement, int replacementLen) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (replacement == null || replacement.isEmpty()) {
            replacement = DEFAULT_REPLACEMENT;
        }
        int len = prefixLen + suffixLen;
        if (value.length() <= prefixLen) {
            // 不足前缀长度，整体处理
            int repeatLen = Math.ceilDiv(value.length(), replacement.length());
            return replacement.repeat(repeatLen);
        }
        if (value.length() <= len) {
            // 不足前后缀总长度，保留前缀，其余处理
            int repeatLen = Math.ceilDiv(value.length() - prefixLen, replacement.length());
            return value.substring(0, prefixLen) + replacement.repeat(repeatLen);
        }
        int actualReplacementLen = replacementLen <= 0
                ? Math.ceilDiv(value.length() - len, replacement.length())
                : Math.ceilDiv(replacementLen, replacement.length());
        return value.substring(0, prefixLen)
                + replacement.repeat(actualReplacementLen)
                + value.substring(value.length() - suffixLen);
    }

    // ====================================================================
    // 配置加载（双重校验锁 + volatile）
    // ====================================================================

    /**
     * AES KEY 初始化：先取缓存 {@code security.aes.key}，再取配置 {@code app.security.aes.key}.
     * <p>使用类对象锁避免锁歧义，结合 {@code volatile} 字段确保可见性。</p>
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
     * RSA 私钥初始化：先取缓存 {@code security.rsa.private-key}，再取配置 {@code app.security.rsa.private-key}.
     * <p>使用类对象锁避免锁歧义，结合 {@code volatile} 字段确保可见性。</p>
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
