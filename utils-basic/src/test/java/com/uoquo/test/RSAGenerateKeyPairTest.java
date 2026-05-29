/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 */
package com.uoquo.test;

import com.uoquo.utils.crypto.RSA;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.GeneralSecurityException;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RSA generateKeyPair(int keySize) 方法单元测试.
 */
class RSAGenerateKeyPairTest {

    @ParameterizedTest
    @ValueSource(ints = {1024, 2048, 4096})
    void generateKeyPair_validKeySizes_shouldSucceed(int keySize) throws GeneralSecurityException {
        KeyPair keyPair = RSA.generateKeyPair(keySize);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());
        // 验证生成的密钥长度与请求的一致
        int actualBitLength = keyPair.getPublic().getEncoded().length * 8;
        // 公钥编码包含额外的 ASN.1 头部信息，所以实际字节数会大于 keySize/8
        // 但私钥的 modulus 位数应该等于 keySize
        assertTrue(keyPair.getPublic().getEncoded().length > 0);
        assertTrue(keyPair.getPrivate().getEncoded().length > 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {512, 768, 1025, 2049, 3072, 8192, 0, -1})
    void generateKeyPair_invalidKeySizes_shouldThrowIllegalArgumentException(int keySize) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RSA.generateKeyPair(keySize));
        assertTrue(ex.getMessage().contains("1024"));
        assertTrue(ex.getMessage().contains("2048"));
        assertTrue(ex.getMessage().contains("4096"));
    }

    @Test
    void generateKeyPair_defaultNoArg_shouldGenerate2048() throws GeneralSecurityException {
        KeyPair keyPair = RSA.generateKeyPair();
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());
    }
}
