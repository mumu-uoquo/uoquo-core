/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Hashtable;

/**
 * Base32
 * 参考：<a href="https://github.com/google/google-authenticator">google-authenticator</a>
 */
public class Base32 {
    // singleton
    private static final Base32 INSTANCE = new Base32("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"); // RFC 4648/3548（标准编码）
    // singleton
//    private static final Base32 INSTANCE = new Base32("0123456789ABCDEFGHJKMNPQRSTVWXYZ"); // Crockford Base32 编码表（ULID使用）

    static Base32 getInstance() {
        return INSTANCE;
    }

    // 32 alpha-numeric characters.
    private String ALPHABET;
    private char[] DIGITS;
    private int MASK;
    private int SHIFT;
    private Hashtable<Character, Integer> CHAR_MAP;

    static final String SEPARATOR = "-";

    public Base32(String alphabet) {
        this.ALPHABET = alphabet;
        DIGITS = ALPHABET.toCharArray();
        MASK = DIGITS.length - 1;
        SHIFT = numberOfTrailingZeros(DIGITS.length);
        CHAR_MAP = new Hashtable<>();
        for (int i = 0; i < DIGITS.length; i++) {
            CHAR_MAP.put(DIGITS[i], i);
        }
    }

    /**
     * Counts the number of 1 bits in the specified integer; this is also
     * referred to as population count.
     *
     * @param i
     *            the integer to examine.
     * @return the number of 1 bits in {@code i}.
     */
    private static int bitCount(int i) {
        i -= ((i >> 1) & 0x55555555);
        i = (i & 0x33333333) + ((i >> 2) & 0x33333333);
        i = (((i >> 4) + i) & 0x0F0F0F0F);
        i += (i >> 8);
        i += (i >> 16);
        return (i & 0x0000003F);
    }

    /**
     * Determines the number of trailing zeros in the specified integer after
     * the {@link #lowestOneBit(int) lowest one bit}.
     *
     * @param i
     *            the integer to examine.
     * @return the number of trailing zeros in {@code i}.
     */
    private static int numberOfTrailingZeros(int i) {
        return bitCount((i & -i) - 1);
    }

    /**
     * 解码
     */
    public static byte[] decode(String encoded) throws DecodingException {
        return getInstance().decodeInternal(encoded);
    }
    public static String decode2String(String encoded) throws DecodingException {
        byte[] dest = decode(encoded);
        return new String(dest, StandardCharsets.UTF_8);
    }

    private static String canonicalize(String str) {
        int length = str.length();
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char c = str.charAt(i);
            if (SEPARATOR.indexOf(c) == -1 && c != ' ') {
                buffer.append(Character.toUpperCase(c));
            }
        }
        return buffer.toString().trim();
    }

    public byte[] decodeInternal(String encoded) throws DecodingException {
        // Remove whitespace and separators
        encoded = canonicalize(encoded);
        // Canonicalize to all upper case
        encoded = encoded.toUpperCase();
        if (encoded.isEmpty()) {
            return new byte[0];
        }
        int encodedLength = encoded.length();
        int outLength = encodedLength * SHIFT / 8;
        byte[] result = new byte[outLength];
        int buffer = 0;
        int next = 0;
        int bitsLeft = 0;
        for (int i = 0, n = encoded.length(); i < n; i++) {
            Character c = encoded.charAt(i);
            if (!CHAR_MAP.containsKey(c)) {
                throw new DecodingException("Illegal character: " + c);
            }
            buffer <<= SHIFT;
            buffer |= CHAR_MAP.get(c) & MASK;
            bitsLeft += SHIFT;
            if (bitsLeft >= 8) {
                result[next++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        // We'll ignore leftover bits for now.
        //
        // if (next != outLength || bitsLeft >= SHIFT) {
        //  throw new DecodingException("Bits left: " + bitsLeft);
        // }
        return result;
    }

    /**
     * 编码
     */
    public static String encode(String data) {
        return encode(data.getBytes(StandardCharsets.UTF_8));
    }
    public static String encode(byte[] data) {
        return getInstance().encodeInternal(data);
    }

    public String encodeInternal(byte[] data) {
        if (data.length == 0) {
            return "";
        }

        // SHIFT is the number of bits per output character, so the length of the
        // output is the length of the input multiplied by 8/SHIFT, rounded up.
        if (data.length >= (1 << 28)) {
            // The computation below will fail, so don't do it.
            throw new IllegalArgumentException();
        }

        int outputLength = (data.length * 8 + SHIFT - 1) / SHIFT;
        return toString(data, outputLength);
    }

    private String toString(byte[] data, int outputLength) {
        StringBuilder result = new StringBuilder(outputLength);

        int buffer = data[0];
        int next = 1;
        int bitsLeft = 8;
        while (bitsLeft > 0 || next < data.length) {
            if (bitsLeft < SHIFT) {
                if (next < data.length) {
                    buffer <<= 8;
                    buffer |= (data[next++] & 0xff);
                    bitsLeft += 8;
                } else {
                    int pad = SHIFT - bitsLeft;
                    buffer <<= pad;
                    bitsLeft += pad;
                }
            }
            int index = MASK & (buffer >> (bitsLeft - SHIFT));
            bitsLeft -= SHIFT;
            result.append(DIGITS[index]);
        }
        return result.toString();
    }

    static class DecodingException extends RuntimeException {
        public DecodingException(String message) {
            super(message);
        }
    }
}
