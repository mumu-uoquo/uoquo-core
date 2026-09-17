/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 描述：本地文件操作工具类. <br>
 * 日期：2018-11-15 09:00 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-11-15     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class FileUtil {
    protected static final Logger log = LoggerFactory.getLogger(FileUtil.class);

    /**
     * 内存一次缓存10M的数据（主要用于文件复制，避免频繁IO操作）
     */
    private static final int BUF_SIZE = 1024 * 1024 * 10;

    // MIME类型 到 文件后缀的映射（可根据需求自行扩展）
    private static final Map<String, String> MIME_TO_SUFFIX = new HashMap<>();
    static {
        // 图片类型
        MIME_TO_SUFFIX.put("image/png", "png");
        MIME_TO_SUFFIX.put("image/jpeg", "jpg");
        MIME_TO_SUFFIX.put("image/jpg", "jpg");
        MIME_TO_SUFFIX.put("image/gif", "gif");
        MIME_TO_SUFFIX.put("image/bmp", "bmp");
        MIME_TO_SUFFIX.put("image/webp", "webp");
        MIME_TO_SUFFIX.put("image/svg+xml", "svg");
        // 文档类型
        MIME_TO_SUFFIX.put("application/pdf", "pdf");
        MIME_TO_SUFFIX.put("application/msword", "doc");
        MIME_TO_SUFFIX.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        MIME_TO_SUFFIX.put("application/vnd.ms-excel", "xls");
        MIME_TO_SUFFIX.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
        // 音视频类型
        MIME_TO_SUFFIX.put("audio/mpeg", "mp3");
        MIME_TO_SUFFIX.put("audio/wav", "wav");
        MIME_TO_SUFFIX.put("video/mp4", "mp4");
        // 文本类型
        MIME_TO_SUFFIX.put("text/plain", "txt");
        MIME_TO_SUFFIX.put("text/html", "html");
        MIME_TO_SUFFIX.put("application/json", "json");
    }
    /**
     * 目录复制.<br>
     * 注：如果目标存在，将会被覆盖！！
     * @param srcPath  源文件夹
     * @param destPath 目标文件夹
     */
    public static void copyDir(String srcPath, String destPath) throws IOException {
        copyDir(Path.of(srcPath), Path.of(destPath), true);
    }

    /**
     * 目录复制（兼容 File API）.
     */
    public static void copyDir(File src, File dest, boolean overlay) throws IOException {
        copyDir(src.toPath(), dest.toPath(), overlay);
    }

    /**
     * 目录复制（Path API）.
     * @param src     待复制的目录
     * @param dest    目标目录
     * @param overlay 如果目标文件存在，是否覆盖
     */
    public static void copyDir(Path src, Path dest, boolean overlay) throws IOException {
        if (!Files.exists(src)) {
            throw new IllegalArgumentException(String.format("源目录[%s]不存在！", src.toAbsolutePath()));
        } else if (!Files.isDirectory(src)) {
            throw new IllegalArgumentException(String.format("源目录[%s]不是有效的文件目录！", src.toAbsolutePath()));
        }

        if (Files.exists(dest) && !Files.isDirectory(dest)) {
            throw new IllegalArgumentException(String.format("目标路径[%s]不是有效的目录！", dest.toAbsolutePath()));
        }
        Files.createDirectories(dest);

        try (var stream = Files.list(src)) {
            stream.forEach(item -> {
                Path target = dest.resolve(item.getFileName().toString());
                try {
                    if (Files.isDirectory(item)) {
                        copyDir(item, target, overlay);
                    } else if (Files.isRegularFile(item)) {
                        copyFile(item, target, overlay);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
    
    /**
     * 复制文件到新路径.<br>
     * 注：如果目标存在，将会被覆盖！！
     */
    public static void copyFile(String srcFile, String destFile) throws IOException {
        copyFile(Path.of(srcFile), Path.of(destFile), true);
    }

    /**
     * 复制单个文件（兼容 File API）.
     */
    public static void copyFile(File src, File dest, boolean overlay) throws IOException {
        copyFile(src.toPath(), dest.toPath(), overlay);
    }

    /**
     * 复制单个文件（Path API）.
     */
    public static void copyFile(Path src, Path dest, boolean overlay) throws IOException {
        if (!Files.exists(src)) {
            throw new IllegalArgumentException(String.format("源文件[%s]不存在！", src.toAbsolutePath()));
        } else if (!Files.isRegularFile(src)) {
            throw new IllegalArgumentException(String.format("源文件[%s]不是有效的文件！", src.toAbsolutePath()));
        }

        if (Files.exists(dest) && Files.isDirectory(dest)) {
            dest = dest.resolve(src.getFileName().toString());
        }
        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(dest) && !overlay) {
            throw new IllegalArgumentException(String.format("目标文件[%s]已经存在！", dest.toAbsolutePath()));
        }

        try {
            if (overlay) {
                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(src, dest);
            }
        } catch (IOException e) {
            log.error("复制文件失败：源[{}], 目标[{}]！", src.toAbsolutePath(), dest.toAbsolutePath());
            throw e;
        }
    }
    
    /**
     * 清空文件夹下的内容（保留传入的根目录）.
     * 注：目录支持级联删除
     * @param dir 待清空的目录
     * @return 如果删除成功返回true，否则返回false
     */
    public static boolean clearDir(String dir) {
        return clearDir(Path.of(dir));
    }

    /**
     * 清空目录（兼容 File API）.
     */
    public static boolean clearDir(File dir) {
        return clearDir(dir.toPath());
    }

    /**
     * 清空目录（Path API，保留根目录）.
     */
    public static boolean clearDir(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return false;
        }
        try (var stream = Files.list(dir)) {
            var items = stream.toList();
            for (Path item : items) {
                if (!delete(item)) {
                    log.warn("can't clear dir [{}], delete file [{}] fail.", dir.toAbsolutePath(), item.toAbsolutePath());
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            log.warn("can't list dir [{}].", dir.toAbsolutePath(), e);
            return false;
        }
    }
    
    /**
     * 删除文件（目录）.
     * 注：目录支持级联删除
     * @param file 待删除的文件（目录）
     * @return 如果删除成功返回true，否则返回false
     */
    public static boolean delete(String file) {
        return delete(Path.of(file));
    }

    /**
     * 删除文件或目录（兼容 File API）.
     */
    public static boolean delete(File file) {
        return delete(file.toPath());
    }

    /**
     * 删除文件或目录（Path API）.
     */
    public static boolean delete(Path file) {
        if (!Files.exists(file)) {
            return false;
        }
        try {
            if (Files.isDirectory(file)) {
                try (var stream = Files.list(file)) {
                    for (Path item : stream.toList()) {
                        if (!delete(item)) {
                            log.warn("can't delete dir [{}], delete file [{}] fail.", file.toAbsolutePath(), item.toAbsolutePath());
                            return false;
                        }
                    }
                }
            }
            Files.deleteIfExists(file);
            return true;
        } catch (IOException e) {
            log.warn("can't delete file [{}].", file.toAbsolutePath(), e);
            return false;
        }
    }
    
    /**
     * 文件迁移（改名）.<br>
     * 注：如果目标存在，将会被覆盖！！
     * @param src     待复制的文件
     * @param dest    目标文件
     */
    public static void move(String src, String dest) throws IOException {
        move(Path.of(src), Path.of(dest), true);
    }

    /**
     * 文件迁移（改名）.<br>
     * @param src     待复制的文件
     * @param dest    目标文件
     * @param overlay 如果目标文件存在，是否覆盖
     */
    public static void move(File src, File dest, boolean overlay) throws IOException {
        move(src.toPath(), dest.toPath(), overlay);
    }

    /**
     * 文件迁移（Path API）.
     */
    public static void move(Path src, Path dest, boolean overlay) throws IOException {
        if (!Files.exists(src)) {
            throw new IllegalArgumentException(String.format("源文件[%s]不存在！", src.toAbsolutePath()));
        } else if (!Files.isRegularFile(src)) {
            throw new IllegalArgumentException(String.format("源文件[%s]不是有效的文件！", src.toAbsolutePath()));
        }
        if (Files.exists(dest)) {
            if (!overlay) {
                throw new IllegalArgumentException(String.format("目标文件[%s]已经存在！", dest.toAbsolutePath()));
            }
            delete(dest);
        }
        try {
            if (overlay) {
                Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(src, dest);
            }
        } catch (IOException e) {
            copyFile(src, dest, overlay);
            if (!delete(src)) {
                throw new IOException(String.format("移动文件失败：删除源文件[%s]失败！", src.toAbsolutePath()), e);
            }
        }
    }

    /**
     * 将内容追加写入文件.
     * @param file 文件
     * @param data 内容字串
     */
    public static boolean write(File file, String data) {
        return write(file.toPath(), data);
    }

    /**
     * 将内容追加写入文件（Path API）.
     */
    public static boolean write(Path file, String data) {
        try {
            return write(file, data.getBytes(StandardCharsets.UTF_8), true);
        } catch (Exception e) {
            log.warn("can't write data [{}] to file [{}] as UTF-8.", data, file.toAbsolutePath(), e);
            return write(file, data.getBytes(), true);
        }
    }

    /**
     * 将内容写入文件.
     * @param file   文件
     * @param data   内容
     * @param append 是否追加
     */
    public static boolean write(File file, byte[] data, boolean append) {
        return write(file.toPath(), data, append);
    }

    /**
     * 将内容写入文件（Path API）.
     */
    public static boolean write(Path file, byte[] data, boolean append) {
        Path parent = file.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (append) {
                Files.write(file, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.write(file, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            log.warn("can't write data [{}] to file [{}].", StringUtil.byte2hex(data), file.toAbsolutePath(), e);
            return false;
        }
    }

    /**
     * 读取文件
     * @param file 文件路径
     */
    public static byte[] read(File file) throws IOException {
        return read(file.toPath());
    }

    /**
     * 读取文件（Path API）.
     */
    public static byte[] read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            String message = String.format("读取文件失败：[%s] 不是有效文件", file.toAbsolutePath());
            throw new IOException(message);
        } else if (!Files.isReadable(file)) {
            String message = String.format("读取文件失败：[%s] 不是可读文件", file.toAbsolutePath());
            throw new IOException(message);
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            log.error("读取文件失败：[{}]", file.toAbsolutePath(), e);
            throw e;
        }
    }

    /**
     * 格式化输出文件大小. <br>
     * @param size 文件大小长度
     */
    public static String formatSize(long size) {
        float temp = size / 1024f;
        if (temp < 1) {
            return size + "B";
        }
        float show = temp;
        temp = show / 1024f;
        if (temp < 1) {
            return String.format("%.2f KB", show);
        }
        show = temp;
        temp = show / 1024f;
        if (temp < 1) {
            return String.format("%.2f MB", show);
        }
        show = temp;
        temp = show / 1024f;
        if (temp < 1) {
            return String.format("%.2f GB", show);
        } else {
            return String.format("%.2f TB", temp);
        }
    }

    /**
     * 获取文件扩展名.
     * @param fileName 文件名
     */
    public static String getSuffixByName(String fileName) {
        if (fileName == null) {
            return null;
        }
        int index = fileName.lastIndexOf(".");
        if (index < 0) {
            return null;
        }
        return fileName.substring(index + 1);
    }

    /**
     * 获取文件扩展名.
     * @param base64Str base64文件内容
     */
    public static String getSuffixByBase64(String base64Str) {
        if (base64Str == null) {
            return null;
        }
        // 1. 匹配data:[mime类型];base64, 中的mime类型部分
        Pattern pattern = Pattern.compile("^data:([^;]+);base64,");
        Matcher matcher = pattern.matcher(base64Str);
        if (!matcher.find()) {
            return null; // 格式不匹配，返回null
        }
        String mimeType = matcher.group(1).trim();
        // 2. 从映射表获取对应后缀
        return MIME_TO_SUFFIX.getOrDefault(mimeType, getDefaultSuffix(mimeType));
    }

    /**
     * 处理一些未预先配置的特殊情况：比如未知类型从MIME中推测后缀
     */
    private static String getDefaultSuffix(String mimeType) {
        // 如果mime类型中包含斜杠，直接取斜杠后部分作为后缀（比如image/png -> png）
        if (mimeType.contains("/")) {
            return mimeType.substring(mimeType.lastIndexOf('/') + 1)
                    .replaceAll("[^a-zA-Z0-9]", "");
        }
        return null;
    }

    /**
     * 关闭流.
     * @param obj 文件流等
     */
    public static void close(Closeable obj) {
        if (obj != null) {
            try {
                obj.close();
            } catch (Exception e) {
                log.warn("close [{}] error.", obj, e);
            }
        }
    }
}
