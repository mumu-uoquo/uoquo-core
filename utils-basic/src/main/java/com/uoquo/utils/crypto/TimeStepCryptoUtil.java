/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.uoquo.utils.Config;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.spring.RedisUtil;

/**
 * 时间片加密工具类，提供 TAES 和 TSM4 的统一处理逻辑。
 *
 * <p>本工具类封装了 SensitiveSerializer 和 SensitiveDeserializer 中对 TAES 和 TSM4 的
 * 处理逻辑，提供统一的加密/解密接口，便于其他地方复用。</p>
 *
 * <h3>主要功能：</h3>
 * <ul>
 *   <li>时间片密钥生成</li>
 *   <li>TAES/TSM4 加密</li>
 *   <li>TAES/TSM4 解密（支持时间片滑动窗口）</li>
 *   <li>时间片长度配置管理</li>
 * </ul>
 *
 * <h3>时间片机制说明：</h3>
 * <p>以当前时间片（毫秒 / 时间片长度）作为密钥种子，对应 TOTP 类似的短时口令机制。
 * 解密时支持滑动窗口，可容忍跨时间片传输的时差。</p>
 *
 * @author xuhz
 */
public class TimeStepCryptoUtil {

    private static final Logger log = LoggerFactory.getLogger(TimeStepCryptoUtil.class);

    /** 时间片密钥目标长度（16 字符，后置补 0），与 AES 128 / SM4 的 16 字节块对齐. */
    public static final int TIME_STEP_KEY_LENGTH = 16;

    /** 时间片长度（毫秒），延迟初始化 */
    private static volatile Integer securityTimeStep;

    /**
     * 私有构造器，防止实例化
     */
    private TimeStepCryptoUtil() {
    }

    // ====================================================================
    // 时间片加密接口
    // ====================================================================

    /**
     * 时间片加密 - TAES
     *
     * @param value 待加密明文
     * @return 加密结果；失败时返回原值
     */
    public static String encryptTAES(String value) {
        long stepMs = getTimeStepMs();
        return encryptTimeStep(value, stepMs, false);
    }

    /**
     * 时间片加密 - TAES
     *
     * @param value 待加密明文
     * @param second 时间片长度（秒）
     * @return 加密结果；失败时返回原值
     */
    public static String encryptTAES(String value, int second) {
        long stepMs = second * 1000L;
        return encryptTimeStep(value, stepMs, false);
    }

    /**
     * 时间片加密 - TSM4
     *
     * @param value 待加密明文
     * @return 加密结果；失败时返回原值
     */
    public static String encryptTSM4(String value) {
        long stepMs = getTimeStepMs();
        return encryptTimeStep(value, stepMs, true);
    }

    /**
     * 时间片加密 - TSM4
     *
     * @param value 待加密明文
     * @param second 时间片长度（秒）
     * @return 加密结果；失败时返回原值
     */
    public static String encryptTSM4(String value, int second) {
        long stepMs = second * 1000L;
        return encryptTimeStep(value, stepMs, true);
    }

    /**
     * 时间片解密 - TAES
     *
     * @param value 待解密密文
     * @return 解密结果；失败时返回原值
     */
    public static String decryptTAES(String value) {
        long stepMs = getTimeStepMs();
        return decryptTimeStep(value, stepMs, false);
    }

    /**
     * 时间片解密 - TAES
     *
     * @param value 待解密密文
     * @param second 时间片长度（秒）
     * @return 解密结果；失败时返回原值
     */
    public static String decryptTAES(String value, int second) {
        long stepMs = second * 1000L;
        return decryptTimeStep(value, stepMs, false);
    }

    /**
     * 时间片解密 - TSM4
     *
     * @param value 待解密密文
     * @return 解密结果；失败时返回原值
     */
    public static String decryptTSM4(String value) {
        long stepMs = getTimeStepMs();
        return decryptTimeStep(value, stepMs, true);
    }

    /**
     * 时间片解密 - TSM4
     *
     * @param value 待解密密文
     * @param second 时间片长度（秒）
     * @return 解密结果；失败时返回原值
     */
    public static String decryptTSM4(String value, int second) {
        long stepMs = second * 1000L;
        return decryptTimeStep(value, stepMs, true);
    }

    // ====================================================================
    // 核心加密/解密逻辑
    // ====================================================================

    /**
     * 时间片加密核心方法
     *
     * @param value  待加密明文
     * @param stepMs 时间片长度（毫秒）
     * @param sm4    true 使用 SM4，false 使用 AES
     * @return 加密结果或原值
     */
    private static String encryptTimeStep(String value, long stepMs, boolean sm4) {
        if (StringUtil.isNull(value)) {
            return value;
        }
        try {
            String key = generateCurrentTimeStepKey(stepMs);
            return sm4 ? SM4.encrypt(value, key) : AES.encrypt(value, key);
        } catch (Exception e) {
            log.warn("{} 时间片加密失败：{}", sm4 ? "TSM4" : "TAES", e.getMessage());
            return value;
        }
    }

    /**
     * 时间片解密核心方法
     *
     * @param value  密文（hex 编码）
     * @param stepMs 时间片长度（毫秒）
     * @param sm4    true 使用 SM4，false 使用 AES
     * @return 解密结果或原值
     */
    private static String decryptTimeStep(String value, long stepMs, boolean sm4) {
        if (StringUtil.isNull(value)) {
            return value;
        }
        
        long currentStep = System.currentTimeMillis() / stepMs;
        Exception lastError = null;
        
        // 尝试当前时间片和上一时间片，覆盖跨片网络传输场景
        for (long delta = 0; delta <= 2; delta++) {
            String key = generateTimeStepKey(currentStep - delta);
            try {
                return sm4 ? SM4.decrypt(value, key) : AES.decrypt(value, key);
            } catch (Exception e) {
                lastError = e;
            }
        }

        log.warn("{} 时间片解密失败（已尝试当前/上一时间片）：{}", sm4 ? "TSM4" : "TAES", lastError.getMessage());
        return value;
    }

    // ====================================================================
    // 时间片密钥生成
    // ====================================================================

    /**
     * 基于当前时间生成时间片密钥
     *
     * @return 16 字符的时间片密钥
     */
    private static String generateCurrentTimeStepKey(long stepMs) {
        long step = System.currentTimeMillis() / stepMs;
        return generateTimeStepKey(step);
    }

    /**
     * 基于指定时间片生成密钥
     *
     * @param time 时间片（毫秒/时间片长度 的商）
     * @return 16 字符的时间片密钥
     */
    public static String generateTimeStepKey(long time) {
        StringBuilder sb = new StringBuilder();
        sb.append(time);
        if (sb.length() < TIME_STEP_KEY_LENGTH) {
            sb.append("0".repeat(TIME_STEP_KEY_LENGTH - sb.length()));
        }
        return sb.toString();
    }

    // ====================================================================
    // 配置管理
    // ====================================================================

    /**
     * 获取时间片长度（毫秒）
     *
     * @return 时间片长度（毫秒）
     */
    public static int getTimeStepMs() {
        checkTimeStep();
        return securityTimeStep;
    }

    // ====================================================================
    // 配置加载（双重校验锁）
    // ====================================================================

    /**
     * 时间片长度初始化（毫秒）：先取缓存 {@code security.aes.time-step}，再取配置 {@code app.security.aes.time-step}，
     * 默认 5 秒.
     */
    private static void checkTimeStep() {
        if (securityTimeStep != null) {
            return;
        }
        synchronized (TimeStepCryptoUtil.class) {
            if (securityTimeStep != null) {
                return;
            }
            Integer step = RedisUtil.get("security.aes.time-step", Integer.class);
            if (step == null) {
                step = Config.getInt("app.security.aes.time-step", 5);
            }
            securityTimeStep = step * 1000;
        }
    }
}