/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
        copyDir(new File(srcPath), new File(destPath), true);
    }

    /**
     * 目录复制.<br>
     * @param src     待复制的文件
     * @param dest    目标文件
     * @param overlay 如果目标文件存在，是否覆盖
     */
    public static void copyDir(File src, File dest, boolean overlay) throws IOException {
        // 源目录处理
        if (!src.exists()) {
            throw new IllegalArgumentException(String.format("源目录[%s]不存在！", src.getAbsolutePath()));
        } else if (!src.isDirectory()) {
            throw new IllegalArgumentException(String.format("源目录[%s]不是有效的文件目录！", src.getAbsolutePath()));
        }

        // 目标目录处理
        if (!dest.exists()) {
            dest.mkdirs();
        }
        // 文件复制
        File[] list = src.listFiles();
        if (list == null) {
            log.info("can't copy dir [{}], there has no children.", src.getAbsolutePath());
            return;
        }
        for (File item : list) {
            File temp = new File(dest.getAbsolutePath() + File.separator + item.getName());
            if (item.isDirectory()) {
                copyDir(item, temp, overlay);
            } else if (item.isFile()) {
                copyFile(item, temp, overlay);
            }
        }
    }
    
    /**
     * 复制文件到新路径.<br>
     * 注：如果目标存在，将会被覆盖！！
     */
    public static void copyFile(String srcFile, String destFile) throws IOException {
        copyFile(new File(srcFile), new File(destFile), true);
    }
    
    /**
     * 复制单个文件 （普通）.
     * @param src     待复制的文件
     * @param dest    目标文件
     * @param overlay 如果目标文件存在，是否覆盖
     */
    public static void copyFile(File src, File dest, boolean overlay) throws IOException {
        // 判断源文件是否存在
        if (!src.exists()) {
            throw new IllegalArgumentException(String.format("源文件[%s]不存在！", src.getAbsolutePath()));
        } else if (!src.isFile()) {
            throw new IllegalArgumentException(String.format("源文件[%s]不是有效的文件！", src.getAbsolutePath()));
        }
        // 目标文件不存在（或非文件）的处理
        if (!dest.exists()) {
            // 如果目标文件所在目录不存在，则创建目录
            if (!dest.getParentFile().exists()) {
                // 目标文件所在目录不存在
                if (!dest.getParentFile().mkdirs()) {
                    throw new IOException(String.format("复制文件失败：创建目标文件所在目录[%s]失败！", dest.getParent()));
                }
            }
        } else if (dest.isDirectory()) {
            // 如果目标是文件夹，则自动创建文件
            dest = new File (dest.getAbsolutePath() + File.separator + src.getName());
        }
        // 判断目标文件是否存在
        if (dest.exists()) {
            if (!overlay) {
                throw new IllegalArgumentException(String.format("目标文件[%s]已经存在！", dest.getAbsolutePath()));
            }
            // 如果目标文件存在并允许覆盖
            // 删除已经存在的目标文件，无论目标文件是目录还是单个文件
            delete(dest);
        }
        // 复制文件
        try (
            InputStream in  = new FileInputStream(src);
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dest), BUF_SIZE);
        ) {
            int byteread  = 0; // 读取的字节数
            byte[] buffer = new byte[BUF_SIZE];
            while ((byteread = in.read(buffer)) != -1) {
                out.write(buffer, 0, byteread);
            }
            out.flush();
        } catch (IOException e) {
            log.error("复制文件失败：源[{}], 目标[{}]！", src.getAbsolutePath(), dest.getAbsolutePath());
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
        return clearDir(new File(dir));
    }
    
    /**
     * 清空文件夹下的内容（保留传入的根目录）.
     * 注：目录支持级联删除
     * @param dir 待清空的目录
     * @return 如果删除成功返回true，否则返回false
     */
    public static boolean clearDir(File dir) {
        if (!dir.exists()) {
            // 文件夹不存在时，不处理
            return false;
        } else if (!dir.isDirectory()) {
            // 非目录不处理
            return false;
        }
        // 目录则级联删除
        File[] list = dir.listFiles();
        if (list == null) {
            return true;
        }
        for (File item : list) {
            boolean success = delete(item);
            if (!success) {
                log.warn("can't clear dir [{}], delete file [{}] fail.", dir.getAbsolutePath(), item.getAbsolutePath());
                return false;
            }
        }
        return true;
    }
    
    /**
     * 删除文件（目录）.
     * 注：目录支持级联删除
     * @param file 待删除的文件（目录）
     * @return 如果删除成功返回true，否则返回false
     */
    public static boolean delete(String file) {
        return delete(new File(file));
    }
    
    /**
     * 删除文件（目录，包括自己）.
     * 注：目录支持级联删除
     * @param file 待删除的文件（目录）
     * @return 如果删除成功返回true，否则返回false
     */
    public static boolean delete(File file) {
        if (!file.exists()) {
            // 不存在时，不处理
            return false;
        } else if (!file.isDirectory()) {
            // 非目录直接删除
            return file.delete();
        }
        File[] list = file.listFiles();
        if (list == null) {
            // 无子内容时，直接删除
            return file.delete();
        }
        // 目录则级联删除
        for (File item : list) {
            boolean success = delete(item);
            if (!success) {
                log.warn("can't delete dir [{}], delete file [{}] fail.", file.getAbsolutePath(), item.getAbsolutePath());
                return false;
            }
        }
        return file.delete();
    }
    
    /**
     * 文件迁移（改名）.<br>
     * 注：如果目标存在，将会被覆盖！！
     * @param src     待复制的文件
     * @param dest    目标文件
     */
    public static void move(String src, String dest) throws IOException {
        move(new File(src), new File(dest), true);
    }

    /**
     * 文件迁移（改名）.<br>
     * @param src     待复制的文件
     * @param dest    目标文件
     * @param overlay 如果目标文件存在，是否覆盖
     */
    public static void move(File src, File dest, boolean overlay) throws IOException {
        // 判断源文件是否存在
        if (!src.exists()) {
            throw new IllegalArgumentException(String.format("源文件[%s]不存在！", src.getAbsolutePath()));
        } else if (!src.isFile()) {
            throw new IllegalArgumentException(String.format("源文件[%s]不是有效的文件！", src.getAbsolutePath()));
        }
        // 判断目标文件是否存在
        if (dest.exists()) {
            if (!overlay) {
                throw new IllegalArgumentException(String.format("目标文件[%s]已经存在！", dest.getAbsolutePath()));
            }
            // 如果目标文件存在并允许覆盖
            // 删除已经存在的目标文件，无论目标文件是目录还是单个文件
            delete(dest);
        }
        // 采用java自带改名方法（主要适用于同一磁盘下）
        if (src.renameTo(dest)) {
            return;
        }
        // 如果不成功，则手动复制
        if (src.isDirectory()) {
            copyDir(src, dest, overlay);
        } else {
            copyFile(src, dest, overlay);
        }
        delete(src);
    }

    /**
     * 将内容追加写入文件.
     * @param file 文件
     * @param data 内容字串
     */
    public static boolean write(File file, String data) {
        try {
            return write(file, data.getBytes(StandardCharsets.UTF_8), true);
        } catch (Exception e) {
            log.warn("can't write data [{}] to file [{}] as UTF-8.", data, file.getAbsolutePath(), e);
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
        // 目标路径不存在时，创建
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        // 写入文件
        try (
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file, append), BUF_SIZE);
        ) {
            out.write(data);
            out.flush();
            return true;
        } catch (Exception e) {
            log.warn("can't write data [{}] to file [{}].", StringUtil.byte2hex(data), file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * 读取文件
     * @param file 文件路径
     */
    public static byte[] read(File file) throws IOException {
        if (!file.isFile()) {
            String message = String.format("读取文件失败：[%s] 不是有效文件", file.getAbsolutePath());
            throw new IOException(message);
        } else if (!file.canRead()) {
            String message = String.format("读取文件失败：[%s] 不是可读文件", file.getAbsolutePath());
            throw new IOException(message);
        }
        // 读取文件
        try (
                InputStream in  = new FileInputStream(file);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
        ) {
            int byteread  = 0; // 读取的字节数
            byte[] buffer = new byte[BUF_SIZE];
            while ((byteread = in.read(buffer)) != -1) {
                out.write(buffer, 0, byteread);
            }
            out.flush();
            return out.toByteArray();
        } catch (IOException e) {
            log.error("读取文件失败：[{}]", file.getAbsolutePath(), e);
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
