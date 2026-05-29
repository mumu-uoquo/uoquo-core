/**
 * AES 属性测试.
 * 使用 jqwik 属性测试框架验证 AES 加解密的核心正确性属性。
 */
package com.uoquo.utils.crypto;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AES 属性测试类.
 * <p>
 * 覆盖以下属性：
 * <ul>
 *   <li>Property 1: CBC 默认 IV Round-Trip</li>
 *   <li>Property 2: CBC 自定义 IV Round-Trip</li>
 *   <li>Property 3: ECB Round-Trip</li>
 *   <li>Property 4: 密钥生成格式与唯一性</li>
 *   <li>Property 11: 默认 IV 等价性</li>
 * </ul>
 */
class AESPropertyTest {

    // ===== Providers =====

    @Provide
    Arbitrary<String> nonEmptyStrings() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(200)
                .filter(s -> !s.isEmpty());
    }

    @Provide
    Arbitrary<byte[]> validAesKeys() {
        return Arbitraries.of(16, 24, 32)
                .flatMap(len -> Arbitraries.bytes().array(byte[].class).ofSize(len));
    }

    @Provide
    Arbitrary<String> validAesKeyStrings() {
        // AES String interface expects UTF-8 key of 16/24/32 chars
        // Use ASCII-only characters to ensure 1 byte per char
        return Arbitraries.of(16, 24, 32)
                .flatMap(len -> Arbitraries.strings()
                        .withCharRange('A', 'Z')
                        .ofLength(len));
    }

    @Provide
    Arbitrary<byte[]> validIvBytes() {
        return Arbitraries.bytes().array(byte[].class).ofSize(16);
    }

    @Provide
    Arbitrary<String> validIvStrings() {
        // IV String interface expects a 16-char UTF-8 string (each char = 1 byte)
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .ofLength(16);
    }

    @Provide
    Arbitrary<Integer> validKeyBits() {
        return Arbitraries.of(128, 192, 256);
    }

    // ===== Property 1: CBC 默认 IV Round-Trip =====

    /**
     * Property 1: For any valid UTF-8 plaintext and valid AES key (16/24/32 bytes),
     * AES.encrypt(data, key) → AES.decrypt(cipher, key) == data.
     *
     * Validates: Requirements 2.4, 2.5
     */
    @Property(tries = 100)
    void cbcDefaultIvRoundTrip(
            @ForAll("nonEmptyStrings") String plaintext,
            @ForAll("validAesKeyStrings") String key
    ) throws GeneralSecurityException {
        String cipherHex = AES.encrypt(plaintext, key);
        String decrypted = AES.decrypt(cipherHex, key);
        assertEquals(plaintext, decrypted,
                "CBC default IV round-trip failed: decrypt(encrypt(data)) != data");
    }

    /**
     * Property 1 (byte[] interface): For any non-empty byte[] plaintext and valid AES key,
     * AES.decrypt(AES.encrypt(data, key), key) == data.
     *
     * Validates: Requirements 2.4, 2.5
     */
    @Property(tries = 100)
    void cbcDefaultIvRoundTripBytes(
            @ForAll @Size(min = 1, max = 200) byte[] plaintext,
            @ForAll("validAesKeys") byte[] key
    ) throws GeneralSecurityException {
        byte[] encrypted = AES.encrypt(plaintext, key);
        byte[] decrypted = AES.decrypt(encrypted, key);
        assertArrayEquals(plaintext, decrypted,
                "CBC default IV round-trip (bytes) failed: decrypt(encrypt(data)) != data");
    }

    // ===== Property 2: CBC 自定义 IV Round-Trip =====

    /**
     * Property 2: For any valid UTF-8 plaintext, valid key, and any 16-byte IV,
     * AES.encrypt(data, key, iv) → AES.decrypt(cipher, key, iv) == data.
     *
     * Validates: Requirements 10.1, 10.2, 10.3, 10.4
     */
    @Property(tries = 100)
    void cbcCustomIvRoundTrip(
            @ForAll("nonEmptyStrings") String plaintext,
            @ForAll("validAesKeyStrings") String key,
            @ForAll("validIvStrings") String iv
    ) throws GeneralSecurityException {
        String cipherHex = AES.encrypt(plaintext, key, iv);
        String decrypted = AES.decrypt(cipherHex, key, iv);
        assertEquals(plaintext, decrypted,
                "CBC custom IV round-trip failed: decrypt(encrypt(data, key, iv), key, iv) != data");
    }

    /**
     * Property 2 (byte[] interface): For any non-empty byte[] plaintext, valid key, and valid IV,
     * AES.decrypt(AES.encrypt(data, key, iv), key, iv) == data.
     *
     * Validates: Requirements 10.1, 10.2, 10.3, 10.4
     */
    @Property(tries = 100)
    void cbcCustomIvRoundTripBytes(
            @ForAll @Size(min = 1, max = 200) byte[] plaintext,
            @ForAll("validAesKeys") byte[] key,
            @ForAll("validIvBytes") byte[] iv
    ) throws GeneralSecurityException {
        byte[] encrypted = AES.encrypt(plaintext, key, iv);
        byte[] decrypted = AES.decrypt(encrypted, key, iv);
        assertArrayEquals(plaintext, decrypted,
                "CBC custom IV round-trip (bytes) failed: decrypt(encrypt(data, key, iv), key, iv) != data");
    }

    // ===== Property 3: ECB Round-Trip =====

    /**
     * Property 3: For any valid UTF-8 plaintext and valid AES key,
     * AES.decryptECB(AES.encryptECB(data, key), key) == data.
     *
     * Validates: Requirements 2.4, 2.5
     */
    @Property(tries = 100)
    void ecbRoundTrip(
            @ForAll("nonEmptyStrings") String plaintext,
            @ForAll("validAesKeyStrings") String key
    ) throws GeneralSecurityException {
        String cipherHex = AES.encryptECB(plaintext, key);
        String decrypted = AES.decryptECB(cipherHex, key);
        assertEquals(plaintext, decrypted,
                "ECB round-trip failed: decryptECB(encryptECB(data)) != data");
    }

    /**
     * Property 3 (byte[] interface): For any non-empty byte[] plaintext and valid AES key,
     * AES.decryptECB(AES.encryptECB(data, key), key) == data.
     *
     * Validates: Requirements 2.4, 2.5
     */
    @Property(tries = 100)
    void ecbRoundTripBytes(
            @ForAll @Size(min = 1, max = 200) byte[] plaintext,
            @ForAll("validAesKeys") byte[] key
    ) throws GeneralSecurityException {
        byte[] encrypted = AES.encryptECB(plaintext, key);
        byte[] decrypted = AES.decryptECB(encrypted, key);
        assertArrayEquals(plaintext, decrypted,
                "ECB round-trip (bytes) failed: decryptECB(encryptECB(data)) != data");
    }

    // ===== Property 4: 密钥生成格式与唯一性 =====

    /**
     * Property 4: For generateKey(bits) with bits in {128, 192, 256},
     * result is hex string of length bits/4 with only lowercase hex chars,
     * and 100 keys are all distinct.
     *
     * Validates: Requirements 6.1, 6.2, 6.3, 6.4
     */
    @Property(tries = 10)
    void generateKeyFormatAndUniqueness(@ForAll("validKeyBits") int bits) {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String key = AES.generateKey(bits);

            // Verify length: bits/4 hex chars
            int expectedLength = bits / 4;
            assertEquals(expectedLength, key.length(),
                    "Generated key length should be " + expectedLength + " for " + bits + " bits");

            // Verify only lowercase hex characters
            assertTrue(key.matches("[0-9a-f]+"),
                    "Generated key should contain only lowercase hex chars, got: " + key);

            keys.add(key);
        }

        // Verify all 100 keys are distinct
        assertEquals(100, keys.size(),
                "100 generated keys should all be distinct for " + bits + " bits");
    }

    /**
     * Property 4 (byte[] interface): generateKeyBytes(bits) produces correct length byte array.
     *
     * Validates: Requirements 6.1, 6.2, 6.3, 6.4
     */
    @Property(tries = 10)
    void generateKeyBytesFormat(@ForAll("validKeyBits") int bits) {
        byte[] keyBytes = AES.generateKeyBytes(bits);
        assertEquals(bits / 8, keyBytes.length,
                "Generated key bytes length should be " + (bits / 8) + " for " + bits + " bits");
    }

    // ===== Property 11: 默认 IV 等价性 =====

    /**
     * Property 11: For any valid plaintext and valid AES key,
     * encrypt without IV produces the same ciphertext as encrypt with explicit 16 zero-byte IV.
     *
     * Validates: Requirements 10.6
     */
    @Property(tries = 100)
    void defaultIvEquivalenceBytes(
            @ForAll @Size(min = 1, max = 200) byte[] plaintext,
            @ForAll("validAesKeys") byte[] key
    ) throws GeneralSecurityException {
        byte[] zeroIv = new byte[16]; // 16 zero bytes (default IV)

        byte[] encryptedDefault = AES.encrypt(plaintext, key);
        byte[] encryptedWithZeroIv = AES.encrypt(plaintext, key, zeroIv);

        assertArrayEquals(encryptedDefault, encryptedWithZeroIv,
                "encrypt(data, key) should produce same result as encrypt(data, key, zeroIv)");
    }

    /**
     * Property 11 (String interface): For any valid plaintext and key,
     * encrypt(data, key) should equal encrypt(data, key, zeroIvString) where zeroIvString
     * is a 16-char string of null characters (UTF-8 encoded as 16 zero bytes).
     *
     * Validates: Requirements 10.6
     */
    @Property(tries = 100)
    void defaultIvEquivalenceString(
            @ForAll("nonEmptyStrings") String plaintext,
            @ForAll("validAesKeyStrings") String key
    ) throws GeneralSecurityException {
        // The default IV is new byte[16] (all zeros).
        // For the String interface, we need a 16-char string that encodes to 16 zero bytes in UTF-8.
        // That's 16 null characters ('\0').
        String zeroIvString = new String(new byte[16], StandardCharsets.UTF_8);

        String encryptedDefault = AES.encrypt(plaintext, key);
        String encryptedWithZeroIv = AES.encrypt(plaintext, key, zeroIvString);

        assertEquals(encryptedDefault, encryptedWithZeroIv,
                "encrypt(data, key) should produce same result as encrypt(data, key, zeroIvString)");
    }
}
