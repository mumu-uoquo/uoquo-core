/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;

/**
 * 描述：Bouncy Castle 安全提供者初始化辅助类. <br>
 * 备注：线程安全，懒加载，确保 BouncyCastleProvider 只注册一次.
 * <ul>
 *   <li>使用双重检查锁保证线程安全</li>
 *   <li>仅在 Provider 未注册时执行注册</li>
 * </ul>
 * 日期：2025-01-01 00:00 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2025-01-01     uoquo team       创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
class BCProvider {
    // 日志
    protected static final Logger log = LoggerFactory.getLogger(BCProvider.class);

    /**
     * 初始化标志（volatile 保证可见性）.
     */
    private static volatile boolean initialized = false;

    /**
     * 确保 Bouncy Castle Provider 已注册.
     * 使用双重检查锁保证线程安全，避免重复注册.
     * @throws RuntimeException 如果 Provider 注册失败
     */
    static void ensureInitialized() {
        if (!initialized) {
            synchronized (BCProvider.class) {
                if (!initialized) {
                    try {
                        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                            int result = Security.addProvider(new BouncyCastleProvider());
                            if (result == -1) {
                                throw new RuntimeException("Bouncy Castle Provider 注册失败");
                            }
                            log.info("Bouncy Castle Provider 注册成功");
                        }
                        initialized = true;
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        log.error("Bouncy Castle Provider 注册异常", e);
                        throw new RuntimeException("Bouncy Castle Provider 注册失败: " + e.getMessage(), e);
                    }
                }
            }
        }
    }
}
