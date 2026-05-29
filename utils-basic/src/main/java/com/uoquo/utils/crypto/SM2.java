/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.crypto;

import com.uoquo.utils.StringUtil;

import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithID;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.math.ec.ECPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * 描述：SM2 非对称加密/签名工具类. <br>
 * 备注：基于国密 SM2 椭圆曲线算法（sm2p256v1），提供密钥对生成、加密/解密、签名/验签功能.
 * <ul>
 *   <li>密钥对生成：公钥 130 字符 hex（04 前缀 + 64 字节坐标），私钥 64 字符 hex</li>
 *   <li>加密/解密：使用 SM2Engine，C1C3C2 模式（符合 GB/T 32918.4-2016）</li>
 *   <li>签名/验签：使用 SM2Signer，默认 userId 为 1234567812345678</li>
 *   <li>SM2不支持「私钥加密 / 公钥解密」</li>
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
public class SM2 {
    // 日志
    protected static final Logger log = LoggerFactory.getLogger(SM2.class);

    /**
     * SM2 推荐曲线名称.
     */
    private static final String CURVE_NAME = "sm2p256v1";

    /**
     * SM2 曲线参数.
     */
    private static final X9ECParameters SM2_PARAMS = GMNamedCurves.getByName(CURVE_NAME);

    /**
     * SM2 域参数.
     */
    private static final ECDomainParameters DOMAIN_PARAMS = new ECDomainParameters(
            SM2_PARAMS.getCurve(),
            SM2_PARAMS.getG(),
            SM2_PARAMS.getN(),
            SM2_PARAMS.getH()
    );

    /**
     * SM2 签名/验签默认 userId（国密标准默认值）.
     */
    private static final byte[] DEFAULT_USER_ID = "1234567812345678".getBytes(StandardCharsets.UTF_8);

    /**
     * 私有构造函数，防止实例化.
     */
    private SM2() {
    }

    /**
     * SM2 密钥对.
     */
    public static class KeyPair {
        private final String publicKey;   // 130 字符 hex（04 + 64字节坐标）
        private final String privateKey;  // 64 字符 hex（32 字节大整数）

        /**
         * 构造密钥对.
         * @param publicKey  公钥（130 字符 hex）
         * @param privateKey 私钥（64 字符 hex）
         */
        public KeyPair(String publicKey, String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        /**
         * 获取公钥.
         * @return 公钥（130 字符 hex，04 前缀 + 64 字节坐标）
         */
        public String getPublicKey() {
            return publicKey;
        }

        /**
         * 获取私钥.
         * @return 私钥（64 字符 hex，32 字节大整数）
         */
        public String getPrivateKey() {
            return privateKey;
        }
    }

    /**
     * 生成 SM2 密钥对.
     * @return KeyPair，getPublicKey() 返回 130 字符 hex 公钥，getPrivateKey() 返回 64 字符 hex 私钥
     * @throws GeneralSecurityException 如果 BC Provider 不可用
     */
    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        BCProvider.ensureInitialized();

        try {
            // 初始化密钥对生成器
            ECKeyPairGenerator generator = new ECKeyPairGenerator();
            ECKeyGenerationParameters genParams = new ECKeyGenerationParameters(DOMAIN_PARAMS, new SecureRandom());
            generator.init(genParams);

            // 生成密钥对
            AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();

            // 提取公钥（非压缩格式：04 + X + Y，共 65 字节 = 130 hex 字符）
            ECPublicKeyParameters publicKeyParams = (ECPublicKeyParameters) keyPair.getPublic();
            byte[] publicKeyBytes = publicKeyParams.getQ().getEncoded(false);
            String publicKeyHex = StringUtil.byte2hex(publicKeyBytes);

            // 提取私钥（大整数 D，32 字节 = 64 hex 字符，不足前补零）
            ECPrivateKeyParameters privateKeyParams = (ECPrivateKeyParameters) keyPair.getPrivate();
            BigInteger d = privateKeyParams.getD();
            String privateKeyHex = padLeft(d.toString(16), 64);

            return new KeyPair(publicKeyHex, privateKeyHex);
        } catch (RuntimeException e) {
            log.error("SM2密钥对生成失败", e);
            throw new GeneralSecurityException("SM2密钥对生成失败: " + e.getMessage(), e);
        }
    }

    // ===== 加密/解密（String 接口）=====

    /**
     * SM2 公钥加密.
     * @param data      明文（UTF-8 编码）
     * @param publicKey 公钥（130 字符 hex，04 前缀 + 64 字节坐标）
     * @return 密文（hex 编码）
     * @throws IllegalArgumentException 参数无效（null/空/格式错误/无效曲线点）
     * @throws GeneralSecurityException 加密失败
     */
    public static String encrypt(String data, String publicKey) throws GeneralSecurityException {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("加密数据不能为空");
        }
        if (publicKey == null || publicKey.isEmpty()) {
            throw new IllegalArgumentException("公钥不能为空");
        }
        if (publicKey.length() != 130) {
            throw new IllegalArgumentException("SM2公钥长度必须为130个hex字符");
        }
        byte[] publicKeyBytes = hexToBytes(publicKey);
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] cipherBytes = encrypt(dataBytes, publicKeyBytes);
        return StringUtil.byte2hex(cipherBytes);
    }

    /**
     * SM2 私钥解密.
     * @param cipherHex 密文（hex 编码）
     * @param privateKey 私钥（64 字符 hex）
     * @return 明文（UTF-8 字符串）
     * @throws IllegalArgumentException 参数无效（null/空/格式错误）
     * @throws GeneralSecurityException 解密失败
     */
    public static String decrypt(String cipherHex, String privateKey) throws GeneralSecurityException {
        if (cipherHex == null || cipherHex.isEmpty()) {
            throw new IllegalArgumentException("密文不能为空");
        }
        if (privateKey == null || privateKey.isEmpty()) {
            throw new IllegalArgumentException("私钥不能为空");
        }
        if (privateKey.length() != 64) {
            throw new IllegalArgumentException("SM2私钥长度必须为64个hex字符");
        }
        byte[] privateKeyBytes = hexToBytes(privateKey);
        byte[] cipherBytes = hexToBytes(cipherHex);
        byte[] plainBytes = decrypt(cipherBytes, privateKeyBytes);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    // ===== 加密/解密（byte[] 接口）=====

    /**
     * SM2 公钥加密（byte[] 接口）.
     * @param data      明文字节数组
     * @param publicKey 公钥字节数组（65 字节，非压缩格式）
     * @return 密文字节数组
     * @throws IllegalArgumentException 参数无效
     * @throws GeneralSecurityException 加密失败
     */
    public static byte[] encrypt(byte[] data, byte[] publicKey) throws GeneralSecurityException {
        BCProvider.ensureInitialized();

        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("加密数据不能为空");
        }
        if (publicKey == null || publicKey.length == 0) {
            throw new IllegalArgumentException("公钥不能为空");
        }

        try {
            // 解析公钥字节为 ECPoint
            ECPoint ecPoint = DOMAIN_PARAMS.getCurve().decodePoint(publicKey);
            // 验证曲线点有效性
            if (!ecPoint.isValid()) {
                throw new IllegalArgumentException("无效的SM2公钥曲线点");
            }

            ECPublicKeyParameters publicKeyParams = new ECPublicKeyParameters(ecPoint, DOMAIN_PARAMS);
            CipherParameters params = new ParametersWithRandom(publicKeyParams, new SecureRandom());

            SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            engine.init(true, params);

            return engine.processBlock(data, 0, data.length);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InvalidCipherTextException e) {
            log.error("SM2加密失败", e);
            throw new GeneralSecurityException("SM2加密失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("SM2加密失败", e);
            throw new GeneralSecurityException("SM2加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * SM2 私钥解密（byte[] 接口）.
     * @param data       密文字节数组
     * @param privateKey 私钥字节数组（32 字节）
     * @return 明文字节数组
     * @throws IllegalArgumentException 参数无效
     * @throws GeneralSecurityException 解密失败
     */
    public static byte[] decrypt(byte[] data, byte[] privateKey) throws GeneralSecurityException {
        BCProvider.ensureInitialized();

        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("密文不能为空");
        }
        if (privateKey == null || privateKey.length == 0) {
            throw new IllegalArgumentException("私钥不能为空");
        }

        try {
            // 解析私钥字节为 BigInteger
            BigInteger d = new BigInteger(1, privateKey);
            ECPrivateKeyParameters privateKeyParams = new ECPrivateKeyParameters(d, DOMAIN_PARAMS);

            SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            engine.init(false, privateKeyParams);

            return engine.processBlock(data, 0, data.length);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InvalidCipherTextException e) {
            log.error("SM2解密失败", e);
            throw new GeneralSecurityException("SM2解密失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("SM2解密失败", e);
            throw new GeneralSecurityException("SM2解密失败: " + e.getMessage(), e);
        }
    }

    // ===== 签名/验签（String 接口）=====

    /**
     * SM2 私钥签名.
     * @param data       原始数据（UTF-8 编码）
     * @param privateKey 私钥（64 字符 hex）
     * @return 签名（hex 编码）
     * @throws IllegalArgumentException 参数无效（null/空/格式错误）
     * @throws GeneralSecurityException 签名失败
     */
    public static String sign(String data, String privateKey) throws GeneralSecurityException {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("签名数据不能为空");
        }
        if (privateKey == null || privateKey.isEmpty()) {
            throw new IllegalArgumentException("私钥不能为空");
        }
        if (privateKey.length() != 64) {
            throw new IllegalArgumentException("SM2私钥长度必须为64个hex字符");
        }
        byte[] privateKeyBytes = hexToBytes(privateKey);
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signatureBytes = sign(dataBytes, privateKeyBytes);
        return StringUtil.byte2hex(signatureBytes);
    }

    /**
     * SM2 公钥验签.
     * @param data      原始数据（UTF-8 编码）
     * @param publicKey 公钥（130 字符 hex，04 前缀 + 64 字节坐标）
     * @param signHex   签名（hex 编码）
     * @return true=验签通过，false=验签失败
     * @throws IllegalArgumentException 参数无效（null/空/格式错误）
     */
    public static boolean verify(String data, String publicKey, String signHex) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("验签数据不能为空");
        }
        if (publicKey == null || publicKey.isEmpty()) {
            throw new IllegalArgumentException("公钥不能为空");
        }
        if (publicKey.length() != 130) {
            throw new IllegalArgumentException("SM2公钥长度必须为130个hex字符");
        }
        if (signHex == null || signHex.isEmpty()) {
            throw new IllegalArgumentException("签名不能为空");
        }
        byte[] publicKeyBytes = hexToBytes(publicKey);
        byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signatureBytes = hexToBytes(signHex);
        return verify(dataBytes, publicKeyBytes, signatureBytes);
    }

    // ===== 签名/验签（byte[] 接口）=====

    /**
     * SM2 私钥签名（byte[] 接口）.
     * @param data       原始数据字节数组
     * @param privateKey 私钥字节数组（32 字节）
     * @return 签名字节数组
     * @throws IllegalArgumentException 参数无效
     * @throws GeneralSecurityException 签名失败
     */
    public static byte[] sign(byte[] data, byte[] privateKey) throws GeneralSecurityException {
        BCProvider.ensureInitialized();

        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("签名数据不能为空");
        }
        if (privateKey == null || privateKey.length == 0) {
            throw new IllegalArgumentException("私钥不能为空");
        }
        if (privateKey.length != 32) {
            throw new IllegalArgumentException("SM2私钥长度必须为32字节");
        }

        try {
            // 解析私钥字节为 BigInteger
            BigInteger d = new BigInteger(1, privateKey);
            ECPrivateKeyParameters privateKeyParams = new ECPrivateKeyParameters(d, DOMAIN_PARAMS);

            // 使用默认 userId 包装参数
            ParametersWithID paramsWithId = new ParametersWithID(privateKeyParams, DEFAULT_USER_ID);

            SM2Signer signer = new SM2Signer();
            signer.init(true, paramsWithId);
            signer.update(data, 0, data.length);

            return signer.generateSignature();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (CryptoException e) {
            log.error("SM2签名失败", e);
            throw new GeneralSecurityException("SM2签名失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("SM2签名失败", e);
            throw new GeneralSecurityException("SM2签名失败: " + e.getMessage(), e);
        }
    }

    /**
     * SM2 公钥验签（byte[] 接口）.
     * @param data      原始数据字节数组
     * @param publicKey 公钥字节数组（65 字节，非压缩格式）
     * @param signature 签名字节数组
     * @return true=验签通过，false=验签失败
     */
    public static boolean verify(byte[] data, byte[] publicKey, byte[] signature) {
        try {
            BCProvider.ensureInitialized();

            if (data == null || data.length == 0) {
                throw new IllegalArgumentException("验签数据不能为空");
            }
            if (publicKey == null || publicKey.length == 0) {
                throw new IllegalArgumentException("公钥不能为空");
            }
            if (publicKey.length != 65) {
                throw new IllegalArgumentException("SM2公钥长度必须为65字节");
            }
            if (signature == null || signature.length == 0) {
                throw new IllegalArgumentException("签名不能为空");
            }

            // 解析公钥字节为 ECPoint
            ECPoint ecPoint = DOMAIN_PARAMS.getCurve().decodePoint(publicKey);
            if (!ecPoint.isValid()) {
                return false;
            }

            ECPublicKeyParameters publicKeyParams = new ECPublicKeyParameters(ecPoint, DOMAIN_PARAMS);

            // 使用默认 userId 包装参数
            ParametersWithID paramsWithId = new ParametersWithID(publicKeyParams, DEFAULT_USER_ID);

            SM2Signer signer = new SM2Signer();
            signer.init(false, paramsWithId);
            signer.update(data, 0, data.length);

            return signer.verifySignature(signature);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("SM2验签异常", e);
            return false;
        }
    }

    // ===== 内部辅助方法 =====

    /**
     * 将 hex 字符串转换为 byte 数组，带格式校验.
     * @param hex hex 字符串
     * @return byte 数组
     * @throws IllegalArgumentException hex 格式无效
     */
    private static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("无效的hex字符串：长度必须为偶数");
        }
        // 校验 hex 格式
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                throw new IllegalArgumentException("无效的hex字符串：包含非法字符 '" + c + "'");
            }
        }
        return StringUtil.hex2byte(hex);
    }

    /**
     * 左补零至指定长度.
     * @param hex    原始 hex 字符串
     * @param length 目标长度
     * @return 补零后的 hex 字符串
     */
    private static String padLeft(String hex, int length) {
        if (hex.length() >= length) {
            return hex;
        }
        StringBuilder sb = new StringBuilder(length);
        sb.append("0".repeat(length - hex.length()));
        sb.append(hex);
        return sb.toString();
    }
}
