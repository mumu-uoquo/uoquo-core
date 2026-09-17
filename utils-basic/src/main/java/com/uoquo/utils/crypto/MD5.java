/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.crypto;

import com.uoquo.utils.StringUtil;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MD5 哈希摘要工具类.
 * <p>提供字符串、字节数组和文件的 MD5 计算功能。</p>
 * <p>备注：MD5 已不推荐用于安全场景，仅适用于数据完整性校验。</p>
 *
 * @author uoquo team
 * @version 2.0
 * @since 1.0
 */
public class MD5 {
    protected static final Logger log = LoggerFactory.getLogger(MD5.class);

    /** 分段读取缓冲区大小. */
    private static final int BUFFER_SIZE = 8192;

    private MD5() {}

    /**
     * MD5 哈希摘要（加盐）.
     * <p>当 salt 不为 null 且不为空时，计算 MD5(src + salt)。</p>
     */
    public static String encrypt(String src, String salt) {
        if (StringUtil.isNull(src)) {
            throw new IllegalArgumentException("原始数据不能为空");
        }
        if (StringUtil.notNull(salt)) {
            src += salt;
        }
        return encrypt(src);
    }

    /**
     * 对字符串计算 MD5，字符串使用 UTF-8 编码。
     */
    public static String encrypt(String src) {
        if (StringUtil.isNull(src)) {
            throw new IllegalArgumentException("原始数据不能为空");
        }
        return encrypt(src.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 对字节数组计算 MD5。
     */
    public static String encrypt(byte[] mess) {
        if (mess == null || mess.length == 0) {
            throw new IllegalArgumentException("数据内容不能为空");
        }
        try {
            MessageDigest digest = createDigest();
            byte[] hash = digest.digest(mess);
            return StringUtil.byte2hex(hash);
        } catch (Exception e) {
            log.error("[{}]的MD5计算出错.", mess, e);
            throw new IllegalStateException("MD5摘要计算失败", e);
        }
    }

    /**
     * 对文件完整内容计算标准 MD5。
     * <p>空文件也允许计算，其结果与标准 MD5 工具一致。</p>
     *
     * @param file 文件路径
     * @return 文件完整内容的 MD5
     */
    public static String encryptFile(Path file) {
        return encryptFile(file, null);
    }

    /**
     * 对文件完整内容计算标准 MD5，并在文件内容之后追加 UTF-8 salt。
     * <p>计算内容为：{@code file bytes + salt bytes}。</p>
     *
     * @param file 文件路径
     * @param salt 盐值，可为 null 或空字符串
     * @return 文件内容（加盐后的）MD5
     */
    public static String encryptFile(Path file, String salt) {
        validateFile(file);
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = createDigest();
            long fileLength = Files.size(file);
            updateDigest(digest, input);
            updateSalt(digest, salt);
            if (Files.size(file) != fileLength) {
                throw new IOException("文件在计算 MD5 期间发生变化：" + file.toAbsolutePath());
            }
            return StringUtil.byte2hex(digest.digest());
        } catch (IOException e) {
            throw readFailure(file, e);
        }
    }

    /**
     * 对文件进行分段采样计算快速摘要。
     * <p>计算内容为：</p>
     * <pre>8 字节文件长度（大端序） + salt UTF-8 字节 + 分段采样数据</pre>
     * <p>文件被划分为 {@code part} 个分段，从前往后读取每个分段开头的
     * {@code len} 字节；如果当前分段剩余内容不足 {@code len}，则全部读取。</p>
     *
     * @param file 文件路径
     * @param salt 盐值，可为 null 或空字符串
     * @param part 分段数量，小于等于 0 时使用 1000
     * @param len 每个分段的采样长度，小于等于 0 时使用 100
     * @return 自定义分段采样摘要，不是标准的完整文件 MD5
     */
    public static String encryptFile(Path file, String salt, int part, long len) {
        validateFile(file);
        part = (part <= 0) ? 1000 : part;
        len = (len <= 0) ? 100 : len;

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long fileLength = channel.size();
            MessageDigest digest = createDigest();

            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(fileLength).array());
            updateSalt(digest, salt);

            long segmentLength = ceilDivide(fileLength, part);
            for (int index = 0; index < part && segmentLength > 0; index++) {
                long position = Math.multiplyExact(segmentLength, index);
                if (position >= fileLength) {
                    break;
                }
                long sampleLength = Math.min(len, Math.min(segmentLength, fileLength - position));
                updateDigest(digest, channel, position, sampleLength);
            }

            // 文件长度变化时，当前摘要无法代表同一个稳定文件。
            if (channel.size() != fileLength) {
                throw new IOException("文件在计算 MD5 期间发生变化：" + file.toAbsolutePath());
            }
            return StringUtil.byte2hex(digest.digest());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("文件分段参数或文件长度超出支持范围", e);
        } catch (IOException e) {
            throw readFailure(file, e);
        }
    }

    private static MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5算法不可用", e);
        }
    }

    private static void validateFile(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("文件参数不能为空");
        }
        if (!Files.exists(file)) {
            throw new IllegalArgumentException(String.format("[%s]不存在", file.toAbsolutePath()));
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(String.format("[%s]不是有效文件，无法计算MD5", file.toAbsolutePath()));
        }
        if (!Files.isReadable(file)) {
            throw new IllegalArgumentException(String.format("[%s]不是可读文件，无法计算MD5", file.toAbsolutePath()));
        }
    }

    private static void updateDigest(MessageDigest digest, InputStream input) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int count;
        while ((count = input.read(buffer)) != -1) {
            digest.update(buffer, 0, count);
        }
    }

    private static void updateDigest(MessageDigest digest, FileChannel channel,
                                     long position, long length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        long remaining = length;
        while (remaining > 0) {
            buffer.clear();
            int chunkSize = (int) Math.min(buffer.capacity(), remaining);
            buffer.limit(chunkSize);

            int count = channel.read(buffer, position);
            if (count < 0) {
                throw new EOFException("文件在读取期间提前结束，位置：" + position);
            }
            if (count == 0) {
                throw new IOException("文件读取返回 0 字节，位置：" + position);
            }
            digest.update(buffer.array(), 0, count);
            position += count;
            remaining -= count;
        }
    }

    private static void updateSalt(MessageDigest digest, String salt) {
        if (StringUtil.notNull(salt)) {
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static long ceilDivide(long value, long divisor) {
        if (value == 0) {
            return 0;
        }
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    private static IllegalArgumentException readFailure(Path file, IOException cause) {
        log.error("文件[{}]MD5计算：读取文件出错.", file.toAbsolutePath(), cause);
        return new IllegalArgumentException(
                String.format("[%s]计算MD5出错", file.toAbsolutePath()), cause);
    }
}
