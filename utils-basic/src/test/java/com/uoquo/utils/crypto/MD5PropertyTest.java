/**
 * MD5 属性测试.
 * 使用 jqwik 属性测试框架验证 MD5 哈希摘要的核心正确性属性。
 */
package com.uoquo.utils.crypto;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.Size;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MD5 属性测试类.
 * <p>
 * 覆盖以下属性：
 * <ul>
 *   <li>Property 7: 摘要确定性与固定长度</li>
 *   <li>Property 8: 加盐正确性（由 task 5.9 实现）</li>
 * </ul>
 */
class MD5PropertyTest {

    /**
     * MD5 摘要的固定 hex 长度（128 bit / 4 = 32 字符）.
     */
    private static final int MD5_HEX_LENGTH = 32;

    // ===== Providers =====

    /**
     * 生成 MD5.encrypt(String) 视为有效的非空字符串.
     * <p>MD5 内部使用 StringUtil.isNull 进行校验，会将以下字符串判定为空：
     * <ul>
     *   <li>纯空白字符串（trim 后为空）</li>
     *   <li>字面量 "null"（不区分大小写，trim 后比较）</li>
     * </ul>
     * 因此生成器需要排除这些情况，确保生成的字符串能正常进入 MD5 计算路径。</p>
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

    // ===== Property 7: 摘要确定性与固定长度 =====

    /**
     * Property 7 (byte[] interface): For any valid non-empty input, computing the MD5 digest
     * twice on the same input SHALL produce identical results, and the output SHALL always
     * be exactly 32 lowercase hex characters.
     *
     * <p>**Validates: Requirements 7.1, 7.2**</p>
     *
     * @param data 随机非空字节数组（1~10000 字节）
     */
    @Property(tries = 100)
    void digestDeterminismAndFixedLengthBytes(
            @ForAll @Size(min = 1, max = 10000) byte[] data
    ) {
        String first = MD5.encrypt(data);
        String second = MD5.encrypt(data);

        // 确定性：同一输入两次摘要必须一致
        assertEquals(first, second,
                "MD5 digest must be deterministic: encrypt(data) called twice should return identical results");

        // 固定长度：必须是 32 字符
        assertEquals(MD5_HEX_LENGTH, first.length(),
                "MD5 digest must be exactly 32 hex characters, got length: " + first.length());

        // 仅包含小写 hex 字符
        assertTrue(first.matches("[0-9a-f]{32}"),
                "MD5 digest must contain only lowercase hex characters, got: " + first);
    }

    /**
     * Property 7 (String interface): For any valid non-empty UTF-8 string, computing the MD5
     * digest twice on the same input SHALL produce identical results, and the output SHALL
     * always be exactly 32 lowercase hex characters.
     *
     * <p>**Validates: Requirements 7.1, 7.2**</p>
     *
     * @param src 随机非空字符串
     */
    @Property(tries = 100)
    void digestDeterminismAndFixedLengthString(
            @ForAll("nonEmptyStrings") String src
    ) {
        String first = MD5.encrypt(src);
        String second = MD5.encrypt(src);

        // 确定性：同一输入两次摘要必须一致
        assertEquals(first, second,
                "MD5 digest must be deterministic: encrypt(src) called twice should return identical results");

        // 固定长度：必须是 32 字符
        assertEquals(MD5_HEX_LENGTH, first.length(),
                "MD5 digest must be exactly 32 hex characters, got length: " + first.length());

        // 仅包含小写 hex 字符
        assertTrue(first.matches("[0-9a-f]{32}"),
                "MD5 digest must contain only lowercase hex characters, got: " + first);

        // String 接口与 byte[] 接口一致性：encrypt(src) 应等于 encrypt(src.getBytes(UTF-8))
        String fromBytes = MD5.encrypt(src.getBytes(StandardCharsets.UTF_8));
        assertEquals(first, fromBytes,
                "String interface should produce same result as byte[] interface for UTF-8 encoded data");
    }

    // ===== Property 8: 加盐正确性 =====

    /**
     * Property 8: For any valid non-empty src string and non-empty salt string,
     * encrypt(src, salt) SHALL produce a different result than encrypt(src),
     * AND SHALL produce the same result as encrypt(src + salt).
     *
     * <p>This validates the salt-handling fix: when salt is non-empty, the implementation
     * must compute MD5(src + salt) instead of falling back to MD5(src).</p>
     *
     * <p>**Validates: Requirements 7.1, 7.2**</p>
     *
     * @param src  随机非空字符串
     * @param salt 随机非空盐值字符串
     */
    @Property(tries = 200)
    void saltCorrectness(
            @ForAll("nonEmptyStrings") String src,
            @ForAll("nonEmptyStrings") String salt
    ) {
        // Concatenation must also be a valid input for MD5.encrypt(String)
        // (StringUtil.isNull treats trim()=="null" case-insensitively as null).
        // While src and salt individually pass the filter, their concatenation
        // could theoretically form "null" (e.g., src="N", salt="ull"). Skip such cases.
        String combined = src + salt;
        Assume.that(!combined.trim().isEmpty());
        Assume.that(!"null".equalsIgnoreCase(combined.trim()));

        String saltedDigest = MD5.encrypt(src, salt);
        String unsaltedDigest = MD5.encrypt(src);
        String concatenatedDigest = MD5.encrypt(combined);

        // 1. encrypt(src, salt) != encrypt(src) when salt is non-empty
        assertNotEquals(unsaltedDigest, saltedDigest,
                "encrypt(src, salt) must differ from encrypt(src) when salt is non-empty. "
                        + "src='" + src + "', salt='" + salt + "'");

        // 2. encrypt(src, salt) == encrypt(src + salt) when salt is non-empty
        assertEquals(concatenatedDigest, saltedDigest,
                "encrypt(src, salt) must equal encrypt(src + salt) when salt is non-empty. "
                        + "src='" + src + "', salt='" + salt + "'");

        // 3. Result is exactly 32 lowercase hex chars
        assertEquals(MD5_HEX_LENGTH, saltedDigest.length(),
                "Salted MD5 digest must be exactly 32 hex characters, got length: "
                        + saltedDigest.length());
        assertTrue(saltedDigest.matches("[0-9a-f]{32}"),
                "Salted MD5 digest must contain only lowercase hex characters, got: "
                        + saltedDigest);
    }

    /**
     * Property 8 (null/empty salt): For any valid non-empty src string and salt that is
     * null or empty, encrypt(src, salt) SHALL produce the same result as encrypt(src).
     *
     * <p>This complements Property 8 by verifying the symmetric branch of the salt-handling
     * fix: when salt is null or empty, no salt is appended, and the digest equals encrypt(src).</p>
     *
     * <p>**Validates: Requirements 7.1, 7.2**</p>
     *
     * @param src 随机非空字符串
     */
    @Property(tries = 100)
    void emptyOrNullSaltEqualsUnsalted(@ForAll("nonEmptyStrings") String src) {
        String unsaltedDigest = MD5.encrypt(src);

        // null salt: should produce same result as encrypt(src)
        assertEquals(unsaltedDigest, MD5.encrypt(src, null),
                "encrypt(src, null) must equal encrypt(src). src='" + src + "'");

        // empty salt: should produce same result as encrypt(src)
        assertEquals(unsaltedDigest, MD5.encrypt(src, ""),
                "encrypt(src, \"\") must equal encrypt(src). src='" + src + "'");
    }
}
