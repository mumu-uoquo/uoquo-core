/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.crypto;

import com.uoquo.utils.StringUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;

/**
 * RSA 非对称加密/签名工具类.
 * <p>提供 RSA 密钥对生成、公钥加密/私钥解密、私钥加密/公钥解密、数字签名/验签等功能。</p>
 * <p>备注：默认使用 SHA256withRSA 签名算法，2048 位密钥长度。</p>
 * <ul>
 *   <li>公钥加密，私钥解密（常用）</li>
 *   <li>私钥加密，公钥解密（常用于服务器处理授权来源的数据）</li>
 *   <li>私钥签名，公钥验签（常用于 license 颁发校验）</li>
 * </ul>
 *
 * <table border="1">
 *   <caption>变更记录</caption>
 *   <tr><th>版本</th><th>日期</th><th>描述</th></tr>
 *   <tr><td>1.0</td><td>2018-02-24</td><td>基础加解密与签名</td></tr>
 *   <tr><td>1.1</td><td>2019-05-30</td><td>实现分段加解密</td></tr>
 *   <tr><td>2.0</td><td>优化版本</td><td>统一异常处理、输入校验、Javadoc 规范化</td></tr>
 * </table>
 *
 * @author uoquo team
 * @version 2.0
 * @since 1.0
 */
public class RSA {
    
    /**
     * 加密算法.
     */
    private static final String ALGORITHM_ENCRYPT = "RSA";
    
    /**
     * 默认签名算法.
     */
    private static final String ALGORITHM_SIGNATURE_DEFAULT = "SHA256withRSA";

    /**
     * 支持的签名算法列表.
     */
    private static final java.util.Set<String> SUPPORTED_SIGN_ALGORITHMS = java.util.Set.of(
            "SHA256withRSA", "SHA512withRSA", "MD5withRSA"
    );

    /**
     * 私有构造函数，防止工具类被实例化.
     */
    private RSA() {}
    
    /**
     * 默认密钥长度（位）.
     */
    private static final int DEFAULT_KEY_SIZE = 2048;
    
    /**
     * 最大解密密文大小（基于默认密钥长度）.
     */
    private static final int MAX_DECRYPT_BLOCK = DEFAULT_KEY_SIZE / 8;
    
    /**
     * 最大加密明文大小（基于默认密钥长度）.
     * RSA 加密有 11 字节的 PKCS#1 v1.5 padding 开销
     */
    private static final int MAX_ENCRYPT_BLOCK = MAX_DECRYPT_BLOCK - 11;
    
    /**
     * 支持的密钥长度集合.
     */
    private static final java.util.Set<Integer> SUPPORTED_KEY_SIZES = java.util.Set.of(1024, 2048, 4096);

    /**
     * 生成 RSA 密钥对（默认 2048 位）.
     * <p>使用默认构造的 SecureRandom 实例（不调用 setSeed），确保密钥生成过程符合安全最佳实践。</p>
     *
     * @return 生成的 RSA 密钥对（{@link KeyPair}），包含公钥和私钥
     * @throws GeneralSecurityException 当 RSA 算法不可用或密钥生成器初始化失败时抛出
     */
    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        return generateKeyPair(DEFAULT_KEY_SIZE);
    }

    /**
     * 生成 RSA 密钥对（指定密钥长度）.
     * <p>使用默认构造的 SecureRandom 实例（不调用 setSeed），确保密钥生成过程符合安全最佳实践。</p>
     * <p>注意：使用非默认密钥长度时，分段加解密的最大块大小将根据实际密钥长度动态计算：
     * 加密块 = keySize/8 - 11 字节，解密块 = keySize/8 字节。</p>
     *
     * @param keySize 密钥长度（仅支持 1024、2048、4096）
     * @return 生成的 RSA 密钥对（{@link KeyPair}），包含公钥和私钥
     * @throws IllegalArgumentException 当密钥长度不在支持列表（1024、2048、4096）中时抛出
     * @throws GeneralSecurityException 当 RSA 算法不可用或密钥生成器初始化失败时抛出
     */
    public static KeyPair generateKeyPair(int keySize) throws GeneralSecurityException {
        if (!SUPPORTED_KEY_SIZES.contains(keySize)) {
            throw new IllegalArgumentException("不支持的密钥长度: " + keySize + "，仅支持 1024、2048、4096");
        }
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(ALGORITHM_ENCRYPT);
        keyPairGen.initialize(keySize, new SecureRandom());
        return keyPairGen.generateKeyPair();
    }
    
    /**
     * 数据签名（默认 SHA256withRSA 算法）.
     * <p>使用私钥对数据进行数字签名，默认采用 SHA256withRSA 签名算法。</p>
     *
     * @param data       待签名的原始数据（UTF-8 编码字符串）
     * @param privateKey 私钥（小写 hex 编码字符串，PKCS#8 格式）
     * @return 签名结果（小写 hex 编码字符串）
     * @throws IllegalArgumentException 当私钥或原始数据为 null 或空字符串时抛出
     * @throws GeneralSecurityException 当私钥格式无效、签名算法不可用或签名引擎内部错误时抛出
     */
    public static String sign( String data, String privateKey) throws GeneralSecurityException {
        return sign(data, privateKey, ALGORITHM_SIGNATURE_DEFAULT);
    }

    /**
     * 数据签名（指定签名算法）.
     * <p>使用私钥和指定的签名算法对数据进行数字签名。</p>
     *
     * @param data       待签名的原始数据（UTF-8 编码字符串）
     * @param privateKey 私钥（小写 hex 编码字符串，PKCS#8 格式）
     * @param algorithm  签名算法名称（支持 SHA256withRSA、SHA512withRSA、MD5withRSA）
     * @return 签名结果（小写 hex 编码字符串）
     * @throws IllegalArgumentException 当私钥、原始数据或算法为 null 或空字符串时抛出，或算法不在支持列表中时抛出
     * @throws GeneralSecurityException 当私钥格式无效、签名算法不可用或签名引擎内部错误时抛出
     */
    public static String sign(String data, String privateKey, String algorithm) throws GeneralSecurityException {
        if (StringUtil.isNull(privateKey)) {
            throw new IllegalArgumentException("私钥不能为空");
        }
        if (StringUtil.isNull(data)) {
            throw new IllegalArgumentException("原始数据不能为空");
        }
        if (StringUtil.isNull(algorithm)) {
            throw new IllegalArgumentException("签名算法不能为空");
        }
        if (!SUPPORTED_SIGN_ALGORITHMS.contains(algorithm)) {
            throw new IllegalArgumentException("不支持的签名算法: " + algorithm);
        }
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(StringUtil.hex2byte(privateKey));
        KeyFactory keyFct = KeyFactory.getInstance(ALGORITHM_ENCRYPT);
        PrivateKey priKey = keyFct.generatePrivate(keySpec);
        Signature signature = Signature.getInstance(algorithm);
        signature.initSign(priKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        byte[] signed = signature.sign();
        return StringUtil.byte2hex(signed);
    }
    
    /**
     * 签名数据验证（默认 SHA256withRSA 算法）.
     * <p>使用公钥验证数字签名，默认采用 SHA256withRSA 签名算法。</p>
     * <p>若验签流程正常执行但签名与数据不匹配，返回 false（不抛出异常）。</p>
     *
     * @param infoData  待验签的原始数据（UTF-8 编码字符串）
     * @param publicKey 公钥（小写 hex 编码字符串，X.509 格式）
     * @param signData  签名数据（小写 hex 编码字符串）
     * @return {@code true} 验签通过；{@code false} 签名与数据不匹配
     * @throws IllegalArgumentException 当公钥、原始数据或签名数据为 null 或空字符串时抛出
     * @throws GeneralSecurityException 当公钥格式无效、签名算法不可用或验签引擎内部错误时抛出
     */
    public static boolean verify(String infoData, String publicKey, String signData) throws GeneralSecurityException {
        return verify(infoData, signData, publicKey, ALGORITHM_SIGNATURE_DEFAULT);
    }

    /**
     * 签名数据验证（指定签名算法）.
     * <p>使用公钥和指定的签名算法验证数字签名。</p>
     * <p>若验签流程正常执行但签名与数据不匹配，返回 false（不抛出异常）。</p>
     *
     * @param data      待验签的原始数据（UTF-8 编码字符串）
     * @param publicKey 公钥（小写 hex 编码字符串，X.509 格式）
     * @param signHex   签名数据（小写 hex 编码字符串）
     * @param algorithm 签名算法名称（支持 SHA256withRSA、SHA512withRSA、MD5withRSA）
     * @return {@code true} 验签通过；{@code false} 签名与数据不匹配
     * @throws IllegalArgumentException 当公钥、原始数据、签名数据或算法为 null 或空字符串时抛出，或算法不在支持列表中时抛出
     * @throws GeneralSecurityException 当公钥格式无效、签名算法不可用或验签引擎内部错误时抛出
     */
    public static boolean verify(String data, String publicKey, String signHex, String algorithm) throws GeneralSecurityException {
        if (StringUtil.isNull(publicKey)) {
            throw new IllegalArgumentException("公钥不能为空");
        }
        if (StringUtil.isNull(data)) {
            throw new IllegalArgumentException("原始数据不能为空");
        }
        if (StringUtil.isNull(signHex)) {
            throw new IllegalArgumentException("签名数据不能为空");
        }
        if (StringUtil.isNull(algorithm)) {
            throw new IllegalArgumentException("签名算法不能为空");
        }
        if (!SUPPORTED_SIGN_ALGORITHMS.contains(algorithm)) {
            throw new IllegalArgumentException("不支持的签名算法: " + algorithm);
        }
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(StringUtil.hex2byte(publicKey));
            KeyFactory keyFct = KeyFactory.getInstance(ALGORITHM_ENCRYPT);
            PublicKey pubKey = keyFct.generatePublic(keySpec);
            // 公钥验签
            byte[] signed = StringUtil.hex2byte(signHex);
            Signature signature = Signature.getInstance(algorithm);
            signature.initVerify(pubKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            // 验签
            return signature.verify(signed);
        } catch (GeneralSecurityException e) {
            throw new GeneralSecurityException("RSA.verify 验签失败", e);
        }
    }
    
    /**
     * 公钥加密（String 接口）.
     * <p>使用 RSA 公钥对明文数据进行加密，支持分段加密处理超长数据。</p>
     * <p>常与 {@link #decrypt(String, String)} 配合使用。</p>
     *
     * @param infoData  待加密的明文数据（UTF-8 编码字符串）
     * @param publicKey 公钥（小写 hex 编码字符串，X.509 格式）
     * @return 密文（小写 hex 编码字符串）
     * @throws IllegalArgumentException 当公钥或明文数据为 null 或空字符串时抛出
     * @throws GeneralSecurityException 当公钥格式无效、RSA 算法不可用或加密引擎内部错误时抛出
     * @throws IOException 当分段加密输出流写入失败时抛出
     */
    public static String encrypt(String infoData, String publicKey) throws GeneralSecurityException, IOException {
        if (StringUtil.isNull(publicKey)) {
            throw new IllegalArgumentException("公钥不能为空");
        }
        if (StringUtil.isNull(infoData)) {
            throw new IllegalArgumentException("原始数据不能为空");
        }
        byte[] keys = StringUtil.hex2byte(publicKey);
        byte[] encrypted = encrypt(infoData.getBytes(StandardCharsets.UTF_8), keys);
        return StringUtil.byte2hex(encrypted);
    }
    
    /**
     * 公钥加密（byte[] 接口）.
     * <p>使用 RSA 公钥对明文字节数组进行加密，支持分段加密处理超长数据。</p>
     *
     * @param infoData  待加密的明文字节数组
     * @param publicKey 公钥字节数组（X.509 编码格式）
     * @return 密文字节数组
     * @throws IllegalArgumentException 当公钥或明文数据为 null 时抛出
     * @throws GeneralSecurityException 当公钥格式无效、RSA 算法不可用或加密引擎内部错误时抛出
     * @throws IOException 当分段加密输出流写入失败时抛出
     */
    public static byte[] encrypt(byte[] infoData, byte[] publicKey) throws GeneralSecurityException, IOException {
        if (publicKey == null || publicKey.length == 0) {
            throw new IllegalArgumentException("公钥不能为空");
        }
        if (infoData == null || infoData.length == 0) {
            throw new IllegalArgumentException("原始数据不能为空");
        }
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKey);
        KeyFactory keyFct = KeyFactory.getInstance(ALGORITHM_ENCRYPT);
        PublicKey  pubKey = keyFct.generatePublic(keySpec);
        
        Cipher cipher = Cipher.getInstance(keyFct.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, pubKey);
        
        return cipherSection(infoData, cipher, MAX_ENCRYPT_BLOCK);
    }

    /**
     * 私钥解密（String 接口）.
     * <p>使用 RSA 私钥对密文数据进行解密，支持分段解密处理超长数据。</p>
     * <p>常与 {@link #encrypt(String, String)} 配合使用。</p>
     *
     * @param rawData  密文数据（小写 hex 编码字符串）
     * @param priveKey 私钥（小写 hex 编码字符串，PKCS#8 格式）
     * @return 解密后的明文（UTF-8 编码字符串）
     * @throws IllegalArgumentException 当私钥或密文数据为 null 或空字符串时抛出
     * @throws GeneralSecurityException 当私钥格式无效、RSA 算法不可用或解密引擎内部错误时抛出
     * @throws IOException 当分段解密输出流写入失败时抛出
     */
    public static String decrypt(String rawData, String priveKey) throws GeneralSecurityException, IOException {
        if (StringUtil.isNull(priveKey)) {
            throw new IllegalArgumentException("私钥不能为空");
        }
        if (StringUtil.isNull(rawData)) {
            throw new IllegalArgumentException("密文数据不能为空");
        }
        byte[] signed = StringUtil.hex2byte(rawData);
        byte[] keys = StringUtil.hex2byte(priveKey);
        byte[] debyt = decrypt(signed, keys);
        return new String(debyt, StandardCharsets.UTF_8);
    }

    /**
     * 私钥解密（byte[] 接口）.
     * <p>使用 RSA 私钥对密文字节数组进行解密，支持分段解密处理超长数据。</p>
     *
     * @param rawData  密文字节数组
     * @param priveKey 私钥字节数组（PKCS#8 编码格式）
     * @return 解密后的明文字节数组
     * @throws IllegalArgumentException 当私钥或密文数据为 null 时抛出
     * @throws GeneralSecurityException 当私钥格式无效、RSA 算法不可用或解密引擎内部错误时抛出
     * @throws IOException 当分段解密输出流写入失败时抛出
     */
    public static byte[] decrypt(byte[] rawData, byte[] priveKey) throws GeneralSecurityException, IOException {
        if (priveKey == null || priveKey.length == 0) {
            throw new IllegalArgumentException("私钥不能为空");
        }
        if (rawData == null || rawData.length == 0) {
            throw new IllegalArgumentException("密文数据不能为空");
        }
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(priveKey);
        KeyFactory keyFct = KeyFactory.getInstance(ALGORITHM_ENCRYPT);
        PrivateKey priKey = keyFct.generatePrivate(keySpec);

        Cipher cipher = Cipher.getInstance(ALGORITHM_ENCRYPT);
        cipher.init(Cipher.DECRYPT_MODE, priKey);

        return cipherSection(rawData, cipher, MAX_DECRYPT_BLOCK);
    }

    /**
     * 私钥加密（String 接口）.
     * <p>使用 RSA 私钥对明文数据进行加密，支持分段加密处理超长数据。</p>
     * <p>常与 {@link #decryptByPublicKey(String, String)} 配合使用。</p>
     *
     * @param infoData 待加密的明文数据（UTF-8 编码字符串）
     * @param priveKey 私钥（小写 hex 编码字符串，PKCS#8 格式）
     * @return 密文（小写 hex 编码字符串）
     * @throws IllegalArgumentException 当私钥或明文数据为 null 或空字符串时抛出
     * @throws GeneralSecurityException 当私钥格式无效、RSA 算法不可用或加密引擎内部错误时抛出
     * @throws IOException 当分段加密输出流写入失败时抛出
     */
    public static String encryptByPrivateKey(String infoData, String priveKey) throws GeneralSecurityException, IOException {
        if (StringUtil.isNull(priveKey)) {
            throw new IllegalArgumentException("私钥不能为空");
        }
        if (StringUtil.isNull(infoData)) {
            throw new IllegalArgumentException("原始数据不能为空");
        }
        byte[] keys = StringUtil.hex2byte(priveKey);
        byte[] encrypted = encryptByPrivateKey(infoData.getBytes(StandardCharsets.UTF_8), keys);
        return StringUtil.byte2hex(encrypted);
    }

    /**
     * 私钥加密（byte[] 接口）.
     * <p>使用 RSA 私钥对明文字节数组进行加密，支持分段加密处理超长数据。</p>
     *
     * @param infoData 待加密的明文字节数组
     * @param priveKey 私钥字节数组（PKCS#8 编码格式）
     * @return 密文字节数组
     * @throws IllegalArgumentException 当私钥或明文数据为 null 时抛出
     * @throws GeneralSecurityException 当私钥格式无效、RSA 算法不可用或加密引擎内部错误时抛出
     * @throws IOException 当分段加密输出流写入失败时抛出
     */
    public static byte[] encryptByPrivateKey(byte[] infoData, byte[] priveKey) throws GeneralSecurityException, IOException {
        if (priveKey == null || priveKey.length == 0) {
            throw new IllegalArgumentException("私钥不能为空");
        }
        if (infoData == null || infoData.length == 0) {
            throw new IllegalArgumentException("原始数据不能为空");
        }
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(priveKey);
        KeyFactory keyFct = KeyFactory.getInstance(ALGORITHM_ENCRYPT);
        PrivateKey priKey = keyFct.generatePrivate(keySpec);

        Cipher cipher = Cipher.getInstance(keyFct.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, priKey);

        return cipherSection(infoData, cipher, MAX_ENCRYPT_BLOCK);
    }
    
    /**
     * 公钥解密（String 接口）.
     * <p>使用 RSA 公钥对密文数据进行解密，支持分段解密处理超长数据。</p>
     * <p>常与 {@link #encryptByPrivateKey(String, String)} 配合使用。</p>
     *
     * @param rawData   密文数据（小写 hex 编码字符串）
     * @param publicKey 公钥（小写 hex 编码字符串，X.509 格式）
     * @return 解密后的明文（UTF-8 编码字符串）
     * @throws IllegalArgumentException 当公钥或密文数据为 null 或空字符串时抛出
     * @throws GeneralSecurityException 当公钥格式无效、RSA 算法不可用或解密引擎内部错误时抛出
     * @throws IOException 当分段解密输出流写入失败时抛出
     */
    public static String decryptByPublicKey(String rawData, String publicKey) throws GeneralSecurityException, IOException {
        if (StringUtil.isNull(publicKey)) {
            throw new IllegalArgumentException("公钥不能为空");
        }
        if (StringUtil.isNull(rawData)) {
            throw new IllegalArgumentException("密文数据不能为空");
        }
        byte[] signed = StringUtil.hex2byte(rawData);
        byte[] keys  = StringUtil.hex2byte(publicKey);
        byte[] debyt = decryptByPublicKey(signed, keys);
        return new String(debyt, StandardCharsets.UTF_8);
    }
    
    /**
     * 公钥解密（byte[] 接口）.
     * <p>使用 RSA 公钥对密文字节数组进行解密，支持分段解密处理超长数据。</p>
     *
     * @param rawData   密文字节数组
     * @param publicKey 公钥字节数组（X.509 编码格式）
     * @return 解密后的明文字节数组
     * @throws IllegalArgumentException 当公钥或密文数据为 null 时抛出
     * @throws GeneralSecurityException 当公钥格式无效、RSA 算法不可用或解密引擎内部错误时抛出
     * @throws IOException 当分段解密输出流写入失败时抛出
     */
    public static byte[] decryptByPublicKey(byte[] rawData, byte[] publicKey) throws GeneralSecurityException, IOException {
        if (publicKey == null || publicKey.length == 0) {
            throw new IllegalArgumentException("公钥不能为空");
        }
        if (rawData == null || rawData.length == 0) {
            throw new IllegalArgumentException("密文数据不能为空");
        }
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKey);
        KeyFactory keyFct = KeyFactory.getInstance(ALGORITHM_ENCRYPT);
        PublicKey  pubKey = keyFct.generatePublic(keySpec);
        
        Cipher cipher = Cipher.getInstance(ALGORITHM_ENCRYPT);
        cipher.init(Cipher.DECRYPT_MODE, pubKey);
        
        return cipherSection(rawData, cipher, MAX_DECRYPT_BLOCK);
    }
    
    /**
     * 加解密分段处理.
     * <p>RSA 加解密有长度限制，此方法将数据按最大块大小分段处理。</p>
     *
     * @param data    待处理的数据字节数组
     * @param cipher  已初始化的 Cipher 实例
     * @param maxSize 每段最大字节数
     * @return 处理后的完整数据字节数组
     * @throws GeneralSecurityException 当 Cipher 执行 doFinal 失败时抛出
     * @throws IOException 当输出流写入失败时抛出
     */
    private static byte[] cipherSection(byte[] data, Cipher cipher, int maxSize) throws GeneralSecurityException, IOException {
        int len = data.length; // 剩余长度
        int idx = 0;           // 索引位置
        try (
                ByteArrayOutputStream out = new ByteArrayOutputStream();
        ) {
            byte[] temp;
            while (len > 0) {
                if (len > maxSize) {
                    temp = cipher.doFinal(data, idx, maxSize);
                } else {
                    temp = cipher.doFinal(data, idx, len);
                }
                out.write(temp, 0, temp.length);
                idx += maxSize;
                len -= maxSize;
            }
            temp = out.toByteArray();
            return temp;
        }
    }
}
