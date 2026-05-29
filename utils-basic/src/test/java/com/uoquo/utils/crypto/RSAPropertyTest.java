/**
 * RSA 属性测试.
 * 使用 jqwik 属性测试框架验证 RSA 加解密和签名/验签的核心正确性属性。
 */
package com.uoquo.utils.crypto;

import com.uoquo.utils.StringUtil;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeContainer;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RSA 属性测试类.
 * <p>
 * 覆盖以下属性：
 * <ul>
 *   <li>Property 5: 公钥加密/私钥解密 Round-Trip</li>
 *   <li>Property 6: 签名/验签一致性</li>
 * </ul>
 * <p>
 * 由于 RSA 密钥对生成开销较大，使用 @BeforeContainer 生成一次共享密钥对。
 */
class RSAPropertyTest {

    /**
     * 公钥（hex 编码）.
     */
    private static String publicKeyHex;

    /**
     * 私钥（hex 编码）.
     */
    private static String privateKeyHex;

    /**
     * RSA 2048-bit 密钥最大加密明文块大小（字节）.
     * MAX_ENCRYPT_BLOCK = 2048/8 - 11 = 245
     */
    private static final int MAX_ENCRYPT_BLOCK = 245;

    @BeforeContainer
    static void setup() throws GeneralSecurityException {
        KeyPair keyPair = RSA.generateKeyPair();
        publicKeyHex = StringUtil.byte2hex(keyPair.getPublic().getEncoded());
        privateKeyHex = StringUtil.byte2hex(keyPair.getPrivate().getEncoded());
    }

    // ===== Providers =====

    /**
     * 生成长度不超过 MAX_ENCRYPT_BLOCK (245 bytes) 的 ASCII 字符串.
     * 使用 ASCII 字符确保每个字符 UTF-8 编码为 1 字节，方便控制字节长度。
     */
    @Provide
    Arbitrary<String> plaintextForEncrypt() {
        return Arbitraries.strings()
                .withCharRange(' ', '~')  // printable ASCII
                .ofMinLength(1)
                .ofMaxLength(MAX_ENCRYPT_BLOCK);
    }

    /**
     * 生成任意非空字符串（用于签名测试，无长度限制）.
     */
    @Provide
    Arbitrary<String> dataForSign() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(500);
    }

    // ===== Property 5: 公钥加密/私钥解密 Round-Trip =====

    /**
     * Property 5: For any valid UTF-8 plaintext string (length ≤ MAX_ENCRYPT_BLOCK = 245 bytes
     * for 2048-bit key) and any valid RSA key pair, encrypting with the public key and then
     * decrypting with the private key SHALL produce output identical to the original plaintext.
     *
     * **Validates: Requirements 9.1, 9.2**
     */
    @Property(tries = 50)
    void publicKeyEncryptPrivateKeyDecryptRoundTrip(
            @ForAll("plaintextForEncrypt") String plaintext
    ) throws GeneralSecurityException, IOException {
        String cipherHex = RSA.encrypt(plaintext, publicKeyHex);
        String decrypted = RSA.decrypt(cipherHex, privateKeyHex);
        assertEquals(plaintext, decrypted,
                "RSA public-key encrypt / private-key decrypt round-trip failed: " +
                        "decrypt(encrypt(data)) != data");
    }

    // ===== Property 6: 签名/验签一致性 =====

    /**
     * Property 6: For any valid UTF-8 data string and any valid RSA key pair, signing the data
     * with the private key and then verifying with the corresponding public key SHALL return true.
     *
     * **Validates: Requirements 1.1, 1.2, 8.1, 8.2**
     */
    @Property(tries = 100)
    void signAndVerifyConsistency(
            @ForAll("dataForSign") String data
    ) throws GeneralSecurityException {
        String signHex = RSA.sign(data, privateKeyHex);
        boolean verified = RSA.verify(data, signHex, publicKeyHex);
        assertTrue(verified,
                "RSA sign/verify consistency failed: verify(publicKey, data, sign(privateKey, data)) should be true");
    }
}
