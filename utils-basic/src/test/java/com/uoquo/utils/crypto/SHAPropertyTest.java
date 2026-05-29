/**
 * SHA 属性测试.
 * 使用 jqwik 属性测试框架验证 SHA 系列哈希摘要的核心正确性属性。
 */
package com.uoquo.utils.crypto;

import com.uoquo.utils.StringUtil;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

import java.util.Arrays;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SHA 属性测试类.
 * <p>
 * 覆盖以下属性：
 * <ul>
 *   <li>Property 9: 摘要确定性与固定长度（task 5.10）</li>
 *   <li>Property 10: HMAC 确定性与密钥敏感性（由 task 5.11 实现）</li>
 * </ul>
 */
class SHAPropertyTest {

    // ===== Providers =====

    /**
     * 生成 SHA.shaXxx(String) 视为有效的非空字符串.
     * <p>SHA 内部使用 StringUtil.isNull 进行校验，会将以下字符串判定为空：
     * <ul>
     *   <li>纯空白字符串（trim 后为空）</li>
     *   <li>字面量 "null"（不区分大小写，trim 后比较）</li>
     * </ul>
     * 因此生成器需要排除这些情况，确保生成的字符串能正常进入摘要计算路径。</p>
     *
     * @return 有效非空字符串生成器
     */
    @Provide
    Arbitrary<String> nonEmptyStrings() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(2000)
                .filter(s -> !s.trim().isEmpty())
                .filter(s -> !"null".equalsIgnoreCase(s.trim()));
    }

    // ===== Property 9: SHA-256 摘要确定性与固定长度 =====

    /**
     * Property 9 (SHA-256, byte[] interface): For any valid non-empty byte array,
     * computing SHA-256 twice on the same input SHALL produce identical results,
     * and the output SHALL always be exactly 32 bytes.
     *
     * <p>**Validates: Requirements 4.1, 4.2, 4.3, 4.4**</p>
     *
     * @param data 随机非空字节数组（1~10000 字节）
     */
    @Property(tries = 200)
    void sha256DeterministicAndFixedLengthBytes(
            @ForAll @Size(min = 1, max = 10000) byte[] data
    ) {
        byte[] digest1 = SHA.sha256(data);
        byte[] digest2 = SHA.sha256(data);

        // 确定性：同一输入两次摘要必须一致
        assertArrayEquals(digest1, digest2,
                "SHA-256 byte[] digest must be deterministic: sha256(data) called twice should return identical results");

        // 固定长度：32 字节
        assertEquals(32, digest1.length,
                "SHA-256 byte[] digest must be exactly 32 bytes long, got: " + digest1.length);
    }

    /**
     * Property 9 (SHA-256, String interface): For any valid non-empty string,
     * computing SHA-256 twice on the same input SHALL produce identical 64-character
     * lowercase hex results.
     *
     * <p>**Validates: Requirements 4.1, 4.2, 4.3, 4.4**</p>
     *
     * @param msg 随机非空字符串
     */
    @Property(tries = 200)
    void sha256DeterministicAndFixedLengthString(
            @ForAll("nonEmptyStrings") String msg
    ) {
        String digest1 = SHA.sha256(msg);
        String digest2 = SHA.sha256(msg);

        // 确定性
        assertEquals(digest1, digest2,
                "SHA-256 string digest must be deterministic: sha256(msg) called twice should return identical results");

        // 固定长度：64 字符
        assertEquals(64, digest1.length(),
                "SHA-256 hex digest must be exactly 64 characters long, got: " + digest1.length());

        // 仅小写 hex 字符
        assertTrue(digest1.matches("[0-9a-f]{64}"),
                "SHA-256 hex digest must contain only lowercase hex characters, got: " + digest1);
    }

    /**
     * Property 9 (SHA-256, String/byte[] consistency): For any valid non-empty string,
     * sha256(str) SHALL equal lowercase hex of sha256(str.getBytes(UTF-8)).
     *
     * <p>**Validates: Requirements 4.1, 4.2, 4.3, 4.4**</p>
     *
     * @param msg 随机非空字符串
     */
    @Property(tries = 200)
    void sha256StringInterfaceConsistentWithBytesInterface(
            @ForAll("nonEmptyStrings") String msg
    ) {
        String hexFromString = SHA.sha256(msg);
        byte[] bytesResult = SHA.sha256(msg.getBytes(StandardCharsets.UTF_8));
        String hexFromBytes = StringUtil.byte2hex(bytesResult);

        assertEquals(hexFromString, hexFromBytes,
                "SHA.sha256(String) must equal byte2hex(SHA.sha256(String.getBytes(UTF-8)))");
    }

    // ===== Property 9: 其他 SHA 变体（覆盖 Requirements 4.1-4.5 全部接口） =====

    /**
     * Property 9 (SHA-1): For any valid non-empty byte array, sha1 byte[] interface
     * SHALL be deterministic and SHALL return exactly 20 bytes.
     *
     * <p>**Validates: Requirements 4.1, 4.2, 4.3, 4.4**</p>
     *
     * @param data 随机非空字节数组
     */
    @Property(tries = 100)
    void sha1DeterministicAndFixedLengthBytes(
            @ForAll @Size(min = 1, max = 10000) byte[] data
    ) {
        byte[] digest1 = SHA.sha1(data);
        byte[] digest2 = SHA.sha1(data);

        assertArrayEquals(digest1, digest2,
                "SHA-1 byte[] digest must be deterministic");
        assertEquals(20, digest1.length,
                "SHA-1 byte[] digest must be exactly 20 bytes long, got: " + digest1.length);
    }

    /**
     * Property 9 (SHA-1, String): hex digest must be exactly 40 chars and consistent
     * with byte[] interface.
     *
     * <p>**Validates: Requirements 4.1, 4.2, 4.3, 4.4**</p>
     *
     * @param msg 随机非空字符串
     */
    @Property(tries = 100)
    void sha1DeterministicAndFixedLengthString(
            @ForAll("nonEmptyStrings") String msg
    ) {
        String digest1 = SHA.sha1(msg);
        String digest2 = SHA.sha1(msg);

        assertEquals(digest1, digest2, "SHA-1 string digest must be deterministic");
        assertEquals(40, digest1.length(),
                "SHA-1 hex digest must be exactly 40 characters long");
        assertTrue(digest1.matches("[0-9a-f]{40}"),
                "SHA-1 hex digest must contain only lowercase hex characters, got: " + digest1);

        String hexFromBytes = StringUtil.byte2hex(SHA.sha1(msg.getBytes(StandardCharsets.UTF_8)));
        assertEquals(digest1, hexFromBytes,
                "SHA.sha1(String) must equal byte2hex(SHA.sha1(String.getBytes(UTF-8)))");
    }

    /**
     * Property 9 (SHA-224): byte[] interface returns exactly 28 bytes,
     * String interface returns exactly 56 hex chars.
     *
     * <p>**Validates: Requirements 4.1, 4.2, 4.3, 4.4**</p>
     *
     * @param data 随机非空字节数组
     */
    @Property(tries = 100)
    void sha224DeterministicAndFixedLengthBytes(
            @ForAll @Size(min = 1, max = 10000) byte[] data
    ) {
        byte[] digest1 = SHA.sha224(data);
        byte[] digest2 = SHA.sha224(data);

        assertArrayEquals(digest1, digest2,
                "SHA-224 byte[] digest must be deterministic");
        assertEquals(28, digest1.length,
                "SHA-224 byte[] digest must be exactly 28 bytes long, got: " + digest1.length);
    }

    /**
     * Property 9 (SHA-224, String): hex digest must be exactly 56 chars and consistent
     * with byte[] interface.
     *
     * <p>**Validates: Requirements 4.1, 4.2, 4.3, 4.4**</p>
     *
     * @param msg 随机非空字符串
     */
    @Property(tries = 100)
    void sha224DeterministicAndFixedLengthString(
            @ForAll("nonEmptyStrings") String msg
    ) {
        String digest1 = SHA.sha224(msg);
        String digest2 = SHA.sha224(msg);

        assertEquals(digest1, digest2, "SHA-224 string digest must be deterministic");
        assertEquals(56, digest1.length(),
                "SHA-224 hex digest must be exactly 56 characters long");
        assertTrue(digest1.matches("[0-9a-f]{56}"),
                "SHA-224 hex digest must contain only lowercase hex characters, got: " + digest1);

        String hexFromBytes = StringUtil.byte2hex(SHA.sha224(msg.getBytes(StandardCharsets.UTF_8)));
        assertEquals(digest1, hexFromBytes,
                "SHA.sha224(String) must equal byte2hex(SHA.sha224(String.getBytes(UTF-8)))");
    }

    /**
     * Property 9 (SHA-384): byte[] interface returns exactly 48 bytes.
     *
     * <p>**Validates: Requirements 4.1, 4.2, 4.3, 4.4**</p>
     *
     * @param data 随机非空字节数组
     */
    @Property(tries = 100)
    void sha384DeterministicAndFixedLengthBytes(
            @ForAll @Size(min = 1, max = 10000) byte[] data
    ) {
        byte[] digest1 = SHA.sha384(data);
        byte[] digest2 = SHA.sha384(data);

        assertArrayEquals(digest1, digest2,
                "SHA-384 byte[] digest must be deterministic");
        assertEquals(48, digest1.length,
                "SHA-384 byte[] digest must be exactly 48 bytes long, got: " + digest1.length);
    }

    /**
     * Property 9 (SHA-384, String): hex digest must be exactly 96 chars and consistent
     * with byte[] interface.
     *
     * <p>**Validates: Requirements 4.1, 4.2, 4.3, 4.4**</p>
     *
     * @param msg 随机非空字符串
     */
    @Property(tries = 100)
    void sha384DeterministicAndFixedLengthString(
            @ForAll("nonEmptyStrings") String msg
    ) {
        String digest1 = SHA.sha384(msg);
        String digest2 = SHA.sha384(msg);

        assertEquals(digest1, digest2, "SHA-384 string digest must be deterministic");
        assertEquals(96, digest1.length(),
                "SHA-384 hex digest must be exactly 96 characters long");
        assertTrue(digest1.matches("[0-9a-f]{96}"),
                "SHA-384 hex digest must contain only lowercase hex characters, got: " + digest1);

        String hexFromBytes = StringUtil.byte2hex(SHA.sha384(msg.getBytes(StandardCharsets.UTF_8)));
        assertEquals(digest1, hexFromBytes,
                "SHA.sha384(String) must equal byte2hex(SHA.sha384(String.getBytes(UTF-8)))");
    }

    /**
     * Property 9 (SHA-512): byte[] interface returns exactly 64 bytes.
     *
     * <p>**Validates: Requirements 4.1, 4.2, 4.3, 4.4**</p>
     *
     * @param data 随机非空字节数组
     */
    @Property(tries = 100)
    void sha512DeterministicAndFixedLengthBytes(
            @ForAll @Size(min = 1, max = 10000) byte[] data
    ) {
        byte[] digest1 = SHA.sha512(data);
        byte[] digest2 = SHA.sha512(data);

        assertArrayEquals(digest1, digest2,
                "SHA-512 byte[] digest must be deterministic");
        assertEquals(64, digest1.length,
                "SHA-512 byte[] digest must be exactly 64 bytes long, got: " + digest1.length);
    }

    /**
     * Property 9 (SHA-512, String): hex digest must be exactly 128 chars and consistent
     * with byte[] interface.
     *
     * <p>**Validates: Requirements 4.1, 4.2, 4.3, 4.4**</p>
     *
     * @param msg 随机非空字符串
     */
    @Property(tries = 100)
    void sha512DeterministicAndFixedLengthString(
            @ForAll("nonEmptyStrings") String msg
    ) {
        String digest1 = SHA.sha512(msg);
        String digest2 = SHA.sha512(msg);

        assertEquals(digest1, digest2, "SHA-512 string digest must be deterministic");
        assertEquals(128, digest1.length(),
                "SHA-512 hex digest must be exactly 128 characters long");
        assertTrue(digest1.matches("[0-9a-f]{128}"),
                "SHA-512 hex digest must contain only lowercase hex characters, got: " + digest1);

        String hexFromBytes = StringUtil.byte2hex(SHA.sha512(msg.getBytes(StandardCharsets.UTF_8)));
        assertEquals(digest1, hexFromBytes,
                "SHA.sha512(String) must equal byte2hex(SHA.sha512(String.getBytes(UTF-8)))");
    }

    // ===== Property 10: HMAC 确定性与密钥敏感性 =====

    /**
     * Property 10 (HMAC-SHA256, byte[] interface, determinism): For any valid non-empty
     * message bytes and non-empty key bytes, computing HMAC-SHA256 twice with the same
     * inputs SHALL produce identical 32-byte results.
     *
     * <p>**Validates: Requirements 5.1, 5.2, 5.3, 5.4**</p>
     *
     * @param data 随机非空字节数组（消息）
     * @param key  随机非空字节数组（密钥）
     */
    @Property(tries = 200)
    void hmacSha256DeterministicBytes(
            @ForAll @Size(min = 1, max = 10000) byte[] data,
            @ForAll @Size(min = 1, max = 256) byte[] key
    ) {
        byte[] mac1 = SHA.hmacSha256(data, key);
        byte[] mac2 = SHA.hmacSha256(data, key);

        // 确定性：相同输入两次计算必须得到相同结果
        assertArrayEquals(mac1, mac2,
                "HMAC-SHA256 byte[] interface must be deterministic: same (data, key) "
                        + "should produce identical results across two invocations");

        // 固定长度：HMAC-SHA256 输出始终为 32 字节
        assertEquals(32, mac1.length,
                "HMAC-SHA256 byte[] result must be exactly 32 bytes long, got: " + mac1.length);
    }

    /**
     * Property 10 (HMAC-SHA256, String interface, determinism): For any valid non-empty
     * message string and non-empty key string, computing HMAC-SHA256 twice with the same
     * inputs SHALL produce identical 64-character lowercase hex results.
     *
     * <p>**Validates: Requirements 5.1, 5.2, 5.3, 5.4**</p>
     *
     * @param msg 随机非空字符串（消息）
     * @param key 随机非空字符串（密钥）
     */
    @Property(tries = 200)
    void hmacSha256DeterministicString(
            @ForAll("nonEmptyStrings") String msg,
            @ForAll("nonEmptyStrings") String key
    ) {
        String mac1 = SHA.hmacSha256(msg, key);
        String mac2 = SHA.hmacSha256(msg, key);

        // 确定性
        assertEquals(mac1, mac2,
                "HMAC-SHA256 String interface must be deterministic: same (msg, key) "
                        + "should produce identical results across two invocations");

        // 固定长度：64 字符
        assertEquals(64, mac1.length(),
                "HMAC-SHA256 hex result must be exactly 64 characters long, got: " + mac1.length());

        // 仅小写 hex 字符
        assertTrue(mac1.matches("[0-9a-f]{64}"),
                "HMAC-SHA256 hex result must contain only lowercase hex characters, got: " + mac1);
    }

    /**
     * Property 10 (HMAC-SHA256, String/byte[] consistency): For any valid non-empty
     * message and key strings, hmacSha256(msg, key) SHALL equal lowercase hex of
     * hmacSha256(msg.getBytes(UTF-8), key.getBytes(UTF-8)).
     *
     * <p>**Validates: Requirements 5.1, 5.2, 5.3, 5.4**</p>
     *
     * @param msg 随机非空字符串
     * @param key 随机非空字符串
     */
    @Property(tries = 200)
    void hmacSha256StringInterfaceConsistentWithBytesInterface(
            @ForAll("nonEmptyStrings") String msg,
            @ForAll("nonEmptyStrings") String key
    ) {
        String hexFromString = SHA.hmacSha256(msg, key);
        byte[] bytesResult = SHA.hmacSha256(
                msg.getBytes(StandardCharsets.UTF_8),
                key.getBytes(StandardCharsets.UTF_8));
        String hexFromBytes = StringUtil.byte2hex(bytesResult);

        assertEquals(hexFromString, hexFromBytes,
                "SHA.hmacSha256(String, String) must equal byte2hex of "
                        + "SHA.hmacSha256(msg.getBytes(UTF-8), key.getBytes(UTF-8))");
    }

    /**
     * Property 10 (HMAC-SHA256 key sensitivity, byte[] interface): For any non-empty
     * message and any two distinct non-empty keys, the HMAC values SHALL be different.
     *
     * <p>**Validates: Requirements 5.1, 5.2, 5.3, 5.4**</p>
     *
     * @param data 随机非空字节数组（消息）
     * @param key1 随机非空字节数组（密钥 1）
     * @param key2 随机非空字节数组（密钥 2）
     */
    @Property(tries = 200)
    void hmacSha256KeySensitivityBytes(
            @ForAll @Size(min = 1, max = 10000) byte[] data,
            @ForAll @Size(min = 1, max = 256) byte[] key1,
            @ForAll @Size(min = 1, max = 256) byte[] key2
    ) {
        // 仅在两个密钥确实不同的情况下断言 HMAC 不同
        Assume.that(!Arrays.equals(key1, key2));

        byte[] mac1 = SHA.hmacSha256(data, key1);
        byte[] mac2 = SHA.hmacSha256(data, key2);

        assertFalse(Arrays.equals(mac1, mac2),
                "HMAC-SHA256 must be key-sensitive: distinct keys for the same message "
                        + "should produce distinct HMAC values");
    }

    /**
     * Property 10 (HMAC-SHA256 key sensitivity, String interface): For any non-empty
     * message and any two distinct non-empty key strings, the HMAC values SHALL be
     * different.
     *
     * <p>**Validates: Requirements 5.1, 5.2, 5.3, 5.4**</p>
     *
     * @param msg  随机非空字符串
     * @param key1 随机非空字符串（密钥 1）
     * @param key2 随机非空字符串（密钥 2）
     */
    @Property(tries = 200)
    void hmacSha256KeySensitivityString(
            @ForAll("nonEmptyStrings") String msg,
            @ForAll("nonEmptyStrings") String key1,
            @ForAll("nonEmptyStrings") String key2
    ) {
        // 两个密钥的 UTF-8 字节表示必须不同（仅当字符串不同且其字节序列不同时才校验敏感性）
        byte[] keyBytes1 = key1.getBytes(StandardCharsets.UTF_8);
        byte[] keyBytes2 = key2.getBytes(StandardCharsets.UTF_8);
        Assume.that(!Arrays.equals(keyBytes1, keyBytes2));

        String mac1 = SHA.hmacSha256(msg, key1);
        String mac2 = SHA.hmacSha256(msg, key2);

        assertFalse(mac1.equals(mac2),
                "HMAC-SHA256 must be key-sensitive: distinct keys for the same message "
                        + "should produce distinct HMAC hex values. msg='" + msg
                        + "', key1='" + key1 + "', key2='" + key2 + "'");
    }
}
