/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 描述：压缩工具类. <br>
 * 日期：2018-02-24 16:32 <br>
 * 约定：
 * <ul>
 *   <li>压缩：目标文件已存在时抛出{@link IllegalArgumentException}（不覆盖）；压缩失败时自动删除未完成的目标文件.
 *       目标文件可以是相对路径.</li>
 *   <li>解压：目标目录下的同名文件会被覆盖；压缩包内的符号链接、设备文件等不会被解压；
 *       条目路径逃逸目标目录（../、绝对路径）时抛出{@link IOException}.</li>
 *   <li>解压的默认目标（target为null）：tar、zip解压到压缩包所在目录下、与压缩包同名（去掉扩展名）的子目录；
 *       gzip解压出的单个文件放在gz文件所在目录，若是tar包则同tar的规则.</li>
 *   <li>解压体积限制：所有解压方法都限制解压后的总字节数（防止压缩炸弹），超出时抛出{@link SizeLimitExceededException}.
 *       未指定限制时，内存解压（byte[]）默认{@value #DEFAULT_MAX_MEMORY_SIZE}字节，文件解压默认{@value #DEFAULT_MAX_EXTRACT_SIZE}字节.</li>
 *   <li>压缩目录时不跟随符号链接目录（避免循环引用）.</li>
 * </ul>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-02-24     xuhz.           创建
 * 1.1          2026-11-10     uoquo team       修复isZip异常、zip指定根目录时条目错误、zip默认后缀、unZip跳过同大小文件、
 *                                              unGzip/unTar文件名与默认目录、相对路径NPE、关闭流时吞掉异常等问题；
 *                                              gzip/unGzip的null统一抛出IllegalArgumentException；增加解压体积限制；
 *                                              解压gzip不再落地临时文件；增加unZip字符集重载
 * </pre>
 * @since   JDK 1.8
 * @version 1.1
 * @author  uoquo team
 */
public class CompressUtil {
    /** 缓冲区大小. */
    private static final int BUF_SIZE = 32 * 1024;
    /** tar块大小（用于判断tar格式）. */
    private static final int TAR_BLOCK_SIZE = 512;
    /** 内存解压（byte[]）的默认体积限制：100MB. */
    public static final long DEFAULT_MAX_MEMORY_SIZE = 100L * 1024 * 1024;
    /** 文件解压的默认体积限制：8GB. */
    public static final long DEFAULT_MAX_EXTRACT_SIZE = 8L * 1024 * 1024 * 1024;

    /**
     * 解压后的数据体积超过限制.
     */
    public static class SizeLimitExceededException extends IOException {
        private static final long serialVersionUID = 1L;
        private final long maxSize;

        public SizeLimitExceededException(long maxSize) {
            super("解压后的数据体积超过限制[" + maxSize + "]字节");
            this.maxSize = maxSize;
        }

        /** 体积限制（字节）. */
        public long getMaxSize() {
            return maxSize;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // gzip
    // ------------------------------------------------------------------------------------------------

    /**
     * 是否gzip文件.
     */
    public static boolean isGzip(File file) throws IOException {
        if ((file == null) || file.isDirectory()) {
            return false;
        }
        try (InputStream fis = Files.newInputStream(file.toPath())) {
            return isGzip(fis.readNBytes(10));
        }
    }

    /**
     * 是否gzip压缩数据. <br>
     * 参考：{@link GZIPInputStream#readHeader(InputStream)} <br>
     * 说明：如果是gzip数据，前10字节则为gzip头信息
     * @param data 待校验数据（不少于10字节）
     */
    public static boolean isGzip(byte[] data) {
        if ((data == null) || (data.length < 10)) { // GZIP头长度10字节
            return false;
        }
        // Check header magic
        short a = DataUtil.getUnsignedByte(data[0]);
        short b = DataUtil.getUnsignedByte(data[1]);
        int temp = (b << 8) | a;
        if (temp != GZIPInputStream.GZIP_MAGIC) {
            return false; // Not in GZIP format
        }
        // Check compression method
        short c = DataUtil.getUnsignedByte(data[2]);
        return c == 8; // 仅支持deflate(8)
    }

    /**
     * gzip压缩数据.
     * @param data 待压缩的byte数组（空数组会得到一个有效的、内容为空的gzip数据）
     * @return 压缩后的byte数组
     * @throws IllegalArgumentException data为null
     * @throws IOException 异常信息
     */
    public static byte[] gzip(byte[] data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("待压缩数据不能为null");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(64, data.length / 2));
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos, BUF_SIZE)) {
            gzip.write(data);
        }
        return bos.toByteArray();
    }

    /**
     * gzip压缩文件（目录）.<br>
     * @param gzFile 目标文件（为空时，仅一个待压缩文件才可以，文件为"原文件名.gz"，目录为"目录名.tar.gz"）
     * @param files  待压缩文件
     */
    public static void gzip(File gzFile, File... files) throws IOException {
        gzip(gzFile, files == null ? null : Arrays.asList(files));
    }

    /**
     * gzip压缩文件（目录）.<br>
     * 多个文件，或者包含文件夹时，先在流中打成tar包再压缩（不产生临时文件），目标文件名为调用方指定的名称.
     * @param gzFile 目标文件（为空时，仅一个待压缩文件才可以，文件为"原文件名.gz"，目录为"目录名.tar.gz"）
     * @param files  待压缩文件
     */
    public static void gzip(File gzFile, List<File> files) throws IOException {
        checkSources(files);
        boolean needTar = (files.size() > 1) || files.get(0).isDirectory();
        if (gzFile == null) {
            if (files.size() != 1) {
                throw new IllegalArgumentException("请传入目标文件");
            }
            gzFile = new File(files.get(0).getAbsolutePath() + (needTar ? ".tar.gz" : ".gz"));
        }
        File target = prepareOutputFile(gzFile);
        if (needTar) {
            writeNewFile(target, out -> {
                try (TarArchiveOutputStream tarOut = newTarOut(new GZIPOutputStream(out, BUF_SIZE))) {
                    tarFiles(tarOut, files);
                }
            });
        } else {
            gzipFile(files.get(0), target);
        }
    }

    /**
     * gzip压缩目录.<br>
     * 备注：也支持单个文件，单个文件时，filter无效.<br>
     * 压缩目录时，压缩包内不含目录自身这一层（即目录下的内容直接位于根）.
     * @param gzFile 目标文件（为空时，文件为"原文件名.gz"，目录为"目录名.tar.gz"）
     * @param dir    待压缩目录
     * @param filter 文件名过滤器
     */
    public static void gzip(File gzFile, File dir, FilenameFilter filter) throws IOException {
        checkSource(dir, "待压缩文件");
        boolean isDir = dir.isDirectory();
        if (!isDir && !dir.isFile()) {
            throw new IllegalArgumentException(String.format("待压缩文件[%s]不是文件或者目录，请核对后再处理.", dir.getAbsolutePath()));
        }
        if (gzFile == null) {
            gzFile = new File(dir.getAbsolutePath() + (isDir ? ".tar.gz" : ".gz"));
        }
        File target = prepareOutputFile(gzFile);
        if (isDir) {
            writeNewFile(target, out -> {
                try (TarArchiveOutputStream tarOut = newTarOut(new GZIPOutputStream(out, BUF_SIZE))) {
                    tarAddChildren(tarOut, "", dir, filter);
                }
            });
        } else {
            gzipFile(dir, target);
        }
    }

    /**
     * 单文件gzip压缩.
     */
    private static void gzipFile(File src, File target) throws IOException {
        writeNewFile(target, out -> {
            try (GZIPOutputStream gzip = new GZIPOutputStream(out, BUF_SIZE)) {
                Files.copy(src.toPath(), gzip);
            }
        });
    }

    /**
     * gzip解压（内存）.<br>
     * 解压后的数据体积不能超过{@link #DEFAULT_MAX_MEMORY_SIZE}.
     * @param data 待解压的byte数组
     * @return 解压后的byte数组
     * @throws IllegalArgumentException data为null或者为空
     * @throws SizeLimitExceededException 解压后的数据体积超过限制
     * @throws IOException 异常信息（数据不是有效的gzip数据时）
     */
    public static byte[] unGzip(byte[] data) throws IOException {
        return unGzip(data, DEFAULT_MAX_MEMORY_SIZE);
    }

    /**
     * gzip解压（内存）.
     * @param data    待解压的byte数组
     * @param maxSize 解压后数据的最大字节数（必须大于0）
     * @return 解压后的byte数组
     * @throws IllegalArgumentException data为null或者为空，或者maxSize不大于0
     * @throws SizeLimitExceededException 解压后的数据体积超过maxSize
     * @throws IOException 异常信息（数据不是有效的gzip数据时）
     */
    public static byte[] unGzip(byte[] data, long maxSize) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("待解压数据不能为null");
        } else if (data.length == 0) {
            throw new IllegalArgumentException("待解压数据不能为空");
        }
        checkMaxSize(maxSize);
        int initial = (int) Math.min(Math.min(maxSize, 1024 * 1024), Math.max(data.length * 4L, 1024));
        ByteArrayOutputStream bos = new ByteArrayOutputStream(initial);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data), BUF_SIZE)) {
            byte[] buffer = new byte[BUF_SIZE];
            long total = 0;
            int num;
            while ((num = gzip.read(buffer, 0, BUF_SIZE)) != -1) {
                total += num;
                if (total > maxSize) {
                    throw new SizeLimitExceededException(maxSize);
                }
                bos.write(buffer, 0, num);
            }
        }
        return bos.toByteArray();
    }

    /**
     * gzip解压（文件）.<br>
     * 解压后的总体积不能超过{@link #DEFAULT_MAX_EXTRACT_SIZE}.
     * @param gzFile 待解压文件（gz文件）
     * @param target 目标目录（为空时见类说明）
     */
    public static void unGzip(File gzFile, File target) throws IOException {
        unGzip(gzFile, target, DEFAULT_MAX_EXTRACT_SIZE);
    }

    /**
     * gzip解压（文件）.<br>
     * gz内容是tar包时，解压tar包的内容；否则解压为单个文件，文件名为gz文件名去掉.gz（或.tgz）后缀，
     * 没有这两个后缀时在文件名后追加.out. 全程流式处理，不产生临时的完整文件.
     * @param gzFile  待解压文件（gz文件）
     * @param target  目标目录（为空时见类说明）
     * @param maxSize 解压后的最大总字节数（必须大于0）
     * @throws SizeLimitExceededException 解压后的数据体积超过maxSize
     */
    public static void unGzip(File gzFile, File target, long maxSize) throws IOException {
        checkMaxSize(maxSize);
        Path archive = checkArchive(gzFile);
        if (!isGzip(gzFile)) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不是有效的gzip文件", gzFile.getAbsolutePath()));
        }
        File parent = gzFile.getAbsoluteFile().getParentFile();
        String base = defaultName(gzFile.getName(), ".gz", ".tgz");
        try (InputStream raw = Files.newInputStream(archive);
            InputStream gz  = new GZIPInputStream(raw, BUF_SIZE);
            BufferedInputStream in = new BufferedInputStream(new LimitedInputStream(gz, new ByteBudget(maxSize)), BUF_SIZE)) {
            // 读取前512字节判断是否tar包，再回退
            in.mark(TAR_BLOCK_SIZE);
            byte[] head = in.readNBytes(TAR_BLOCK_SIZE);
            in.reset();
            if (isTar(head)) {
                File dir = (target == null) ? new File(parent, stripExtension(base, ".tar")) : target;
                extractTar(in, prepareTargetDir(dir));
            } else {
                Path dir  = prepareTargetDir(target == null ? parent : target);
                Path dest = dir.resolve(base);
                Path tmp  = Files.createTempFile(dir, base + ".", ".tmp");
                try {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // tar
    // ------------------------------------------------------------------------------------------------

    /**
     * 是否是tar文件.
     */
    public static boolean isTar(File file) throws IOException {
        if ((file == null) || file.isDirectory()) {
            return false;
        }
        // http://en.wikipedia.org/wiki/Tar_(computing)#File_header
        try (InputStream fis = Files.newInputStream(file.toPath())) {
            return isTar(fis.readNBytes(TAR_BLOCK_SIZE));
        }
    }

    /**
     * 是否是tar包数据.
     * 参考 {@link org.eclipse.che.commons.lang.TarUtils#isTarFile(File)}
     */
    public static boolean isTar(byte[] data) {
        return (data != null) && TarArchiveInputStream.matches(data, data.length);
    }

    /**
     * tar打包文件.<br>
     * @param tar   目标文件（为空时，仅一个待打包文件才可以，为"原文件名.tar"）
     * @param files 待打包文件
     */
    public static void tar(File tar, File... files) throws IOException {
        tar(tar, files == null ? null : Arrays.asList(files));
    }

    /**
     * tar打包文件.<br>
     * 文件夹会连同其自身这一层一起打包.
     * @param tar   目标文件（为空时，仅一个待打包文件才可以，为"原文件名.tar"）
     * @param files 待打包文件
     */
    public static void tar(File tar, List<File> files) throws IOException {
        checkSources(files);
        if (tar == null) {
            if (files.size() != 1) {
                throw new IllegalArgumentException("请传入目标文件");
            }
            tar = new File(files.get(0).getAbsolutePath() + ".tar");
        }
        File target = prepareOutputFile(tar);
        writeNewFile(target, out -> {
            try (TarArchiveOutputStream tarOut = newTarOut(out)) {
                tarFiles(tarOut, files);
            }
        });
    }

    /**
     * tar打包文件夹.<br>
     * 备注：也支持单个文件. 打包文件夹时，包内不含文件夹自身这一层（即文件夹下的内容直接位于根）.
     * @param tar    目标文件（为空时，为"原文件名.tar"）
     * @param dir    待打包文件夹
     * @param filter 文件名过滤器
     */
    public static void tar(File tar, File dir, FilenameFilter filter) throws IOException {
        checkSource(dir, "待压缩文件");
        if (dir.isFile()) {
            tar(tar, dir); // 如果是文件，则按文件的方式压缩
            return;
        } else if (!dir.isDirectory()) {
            throw new IllegalArgumentException(String.format("待压缩文件[%s]不是文件或者目录，请核对后再处理.", dir.getAbsolutePath()));
        }
        if (tar == null) {
            tar = new File(dir.getAbsolutePath() + ".tar");
        }
        File target = prepareOutputFile(tar);
        writeNewFile(target, out -> {
            try (TarArchiveOutputStream tarOut = newTarOut(out)) {
                tarAddChildren(tarOut, "", dir, filter);
            }
        });
    }

    /**
     * 创建tar输出流（支持长文件名、超大文件、非ASCII文件名）.
     */
    private static TarArchiveOutputStream newTarOut(OutputStream out) {
        TarArchiveOutputStream tarOut = new TarArchiveOutputStream(out, "UTF-8");
        tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
        tarOut.setAddPaxHeadersForNonAsciiNames(true);
        return tarOut;
    }

    /**
     * 将文件（目录）按其自身名称添加到tar包.
     */
    private static void tarFiles(TarArchiveOutputStream tarOut, List<File> files) throws IOException {
        for (File file : files) {
            String name = baseName(file);
            if (file.isDirectory()) {
                tarAddDirEntry(tarOut, name, file);
                tarAddChildren(tarOut, name + "/", file, null);
            } else {
                tarAddFile(tarOut, name, file);
            }
        }
    }

    /**
     * 在tar包中添加文件夹标识
     * @param tarOut tar输出流
     * @param name   tar中的名称
     * @param dir    待打包目录
     */
    private static void tarAddDirEntry(TarArchiveOutputStream tarOut, String name, File dir) throws IOException {
        tarOut.putArchiveEntry(new TarArchiveEntry(dir, name));
        tarOut.closeArchiveEntry();
    }

    /**
     * 将文件夹内容添加tar包（递归处理，不跟随符号链接目录）
     * @param tarOut tar输出流
     * @param prefix tar中的名称前缀（空串或以"/"结尾）
     * @param dir    待打包目录
     * @param filter 文件名过滤器
     */
    private static void tarAddChildren(TarArchiveOutputStream tarOut, String prefix, File dir, FilenameFilter filter) throws IOException {
        for (File child : listSorted(dir)) {
            if ((filter != null) && !filter.accept(dir, child.getName())) {
                continue;
            }
            String name = prefix + child.getName();
            if (child.isDirectory()) {
                if (Files.isSymbolicLink(child.toPath())) {
                    continue;
                }
                tarAddDirEntry(tarOut, name, child);
                tarAddChildren(tarOut, name + "/", child, filter);
            } else if (child.isFile()) {
                tarAddFile(tarOut, name, child);
            }
        }
    }

    /**
     * 将文件添加tar包
     * @param tarOut tar输出流
     * @param name   tar中的名称
     * @param file   待打包文件
     */
    private static void tarAddFile(TarArchiveOutputStream tarOut, String name, File file) throws IOException {
        tarOut.putArchiveEntry(new TarArchiveEntry(file, name));
        Files.copy(file.toPath(), tarOut);
        tarOut.closeArchiveEntry();
    }

    /**
     * tar解压<br>
     * 解压后的总体积不能超过{@link #DEFAULT_MAX_EXTRACT_SIZE}.
     * @param tarFile 待解压文件（tar文件）
     * @param target  目标目录（为空时见类说明）
     */
    public static void unTar(File tarFile, File target) throws IOException {
        unTar(tarFile, target, DEFAULT_MAX_EXTRACT_SIZE);
    }

    /**
     * tar解压
     * @param tarFile 待解压文件（tar文件）
     * @param target  目标目录（为空时见类说明）
     * @param maxSize 解压后的最大总字节数（必须大于0）
     * @throws SizeLimitExceededException 解压后的数据体积超过maxSize
     */
    public static void unTar(File tarFile, File target, long maxSize) throws IOException {
        checkMaxSize(maxSize);
        Path archive = checkArchive(tarFile);
        if (!isTar(tarFile)) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不是有效的tar文件", archive.toAbsolutePath()));
        }
        File dir = (target == null)
                ? new File(tarFile.getAbsoluteFile().getParentFile(), defaultName(tarFile.getName(), ".tar"))
                : target;
        Path root = prepareTargetDir(dir);
        try (InputStream in = new BufferedInputStream(
                new LimitedInputStream(Files.newInputStream(archive), new ByteBudget(maxSize)), BUF_SIZE)) {
            extractTar(in, root);
        }
    }

    /**
     * 从流中解压tar. 符号链接、设备文件等不会被解压.
     */
    private static void extractTar(InputStream in, Path targetRoot) throws IOException {
        try (TarArchiveInputStream tarIn = new TarArchiveInputStream(in, "UTF-8")) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                Path output = resolveArchiveEntry(targetRoot, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }
                if (entry.isSymbolicLink() || entry.isLink() || entry.isCharacterDevice()
                        || entry.isBlockDevice() || entry.isFIFO() || !entry.isFile()) {
                    continue;
                }
                createParent(output);
                Files.copy(tarIn, output, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // zip
    // ------------------------------------------------------------------------------------------------

    /**
     * 是否zip文件.
     * 参考 <a href="http://en.wikipedia.org/wiki/Zip_(file_format)#File_headers">headers description</a>
     */
    public static boolean isZip(File file) throws IOException {
        if ((file == null) || file.isDirectory()) {
            return false;
        }
        try (InputStream fIn = Files.newInputStream(file.toPath())) {
            return isZip(fIn.readNBytes(4));
        }
    }

    /**
     * 是否zip压缩数据（含空zip）. <br>
     * 参考 {@link org.eclipse.che.commons.lang.ZipUtils#isZipFile(File)}
     * @param data 待校验数据（不少于4字节）
     */
    public static boolean isZip(byte[] data) {
        if ((data == null) || (data.length < 4)) {
            return false;
        }
        // NOTE: little-indian bytes order!
        int signature = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return (signature == 0x04034b50) || (signature == 0x06054b50);
    }

    /**
     * zip压缩指定字节流.
     * @param data     待压缩的字节流
     * @param fileName 压缩文件中的文件名（为空，则默认为unknown.dat）
     * @return 压缩后的数据流
     * @throws IOException io异常
     */
    public static byte[] zip(byte[] data, String fileName) throws IOException {
        if (data == null) {
            return null;
        }
        if (StringUtil.isNull(fileName)) {
            fileName = "unknown.dat";
        }
        return zip(Map.of(fileName, data));
    }

    /**
     * zip压缩指定字节流.
     * @param data     待压缩的字节流
     * @param fileName 压缩文件中的文件名（为空，则默认为unknown.dat）
     * @param zipPath  目标zip文件（必填，以.zip结尾）
     */
    public static void zip(byte[] data, String fileName, String zipPath) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("没有需要压缩的数据");
        }
        // 目标路径处理
        if (StringUtil.isNull(zipPath)) {
            throw new IllegalArgumentException("请指定压缩后的文件存储路径");
        } else if (!zipPath.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException(String.format("压缩后的文件[%s]只能是.zip结尾的文件", zipPath));
        }
        File target = prepareOutputFile(new File(zipPath));
        // 压缩文件名称处理
        final String entryName = StringUtil.isNull(fileName) ? "unknown.dat" : fileName;
        writeNewFile(target, out -> {
            try (ZipOutputStream zipOut = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                zipOut.putNextEntry(new ZipEntry(entryName));
                zipOut.write(data);
                zipOut.closeEntry();
            }
        });
    }

    /**
     * zip压缩指定字节流.
     * @param map 待压缩数据集合（key：文件名，value：数据流）
     * @return 压缩后的byte流
     * @throws IOException IO异常.
     */
    public static byte[] zip(Map<String, byte[]> map) throws IOException {
        if (map == null) {
            return null;
        }
        long total = 0;
        for (byte[] value : map.values()) {
            total += (value == null) ? 0 : value.length;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.min(Math.max(total / 2 + 128, 128), 16 * 1024 * 1024));
        try (ZipOutputStream zipOut = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            for (Entry<String, byte[]> item : map.entrySet()) {
                if (item.getValue() == null) {
                    throw new IllegalArgumentException(String.format("文件[%s]的数据不能为null", item.getKey()));
                }
                zipOut.putNextEntry(new ZipEntry(item.getKey()));
                zipOut.write(item.getValue());
                zipOut.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    /**
     * zip压缩文件（目录）.<br>
     * @param zipFile  目标文件
     * @param basePath 根目录（绝对地址，可以为空）
     * @param files    待压缩文件
     * @see #zip(File, String, List)
     */
    public static void zip(File zipFile, String basePath, File... files) throws IOException {
        zip(zipFile, basePath, files == null ? null : Arrays.asList(files));
    }

    /**
     * zip压缩文件（目录）.<br>
     * 压缩包内的条目名：
     * <ul>
     *   <li>basePath为空：文件（目录）按其自身名称存放在压缩包根，目录会连同其自身这一层一起压缩；</li>
     *   <li>basePath不为空：条目名为文件相对于basePath的路径，并保留其上级目录（上级目录条目只会添加一次）.
     *       文件必须位于basePath之下，否则抛出{@link IllegalArgumentException}.</li>
     * </ul>
     * @param zipFile  目标文件（为空时，仅一个待压缩文件才可以，为"原文件名.zip"）
     * @param basePath 根目录（绝对地址，可以为空）
     * @param files    待压缩文件
     */
    public static void zip(File zipFile, String basePath, List<File> files) throws IOException {
        checkSources(files);
        if (zipFile == null) {
            if (files.size() != 1) {
                throw new IllegalArgumentException("请传入目标文件");
            }
            zipFile = new File(files.get(0).getAbsolutePath() + ".zip");
        }
        final Path base = StringUtil.isNull(basePath) ? null : Paths.get(basePath).toAbsolutePath().normalize();
        // 提前计算并校验条目名，避免压缩到一半才发现错误
        for (File file : files) {
            if (base != null) {
                relativeTo(base, file);
            }
        }
        File target = prepareOutputFile(zipFile);
        writeNewFile(target, out -> {
            try (ZipOutputStream zipOut = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                Set<String> names = new HashSet<>();
                for (File file : files) {
                    String entryName = baseName(file);
                    if (base != null) {
                        Path rel = relativeTo(base, file);
                        // 添加上级目录条目
                        for (int i = 1; i < rel.getNameCount(); i++) {
                            String dirName = toEntryName(rel.subpath(0, i)) + "/";
                            zipAddDirEntry(zipOut, dirName, base.resolve(rel.subpath(0, i)).toFile(), names);
                        }
                        entryName = toEntryName(rel);
                    }
                    zipAdd(zipOut, file, entryName, null, names);
                }
            }
        });
    }

    /**
     * zip压缩目录.<br>
     * 备注：也支持单个文件，单个文件时，filter无效. 压缩目录时，压缩包内不含目录自身这一层（即目录下的内容直接位于根）.
     * @param zipFile 目标文件（为空时，为"原文件名.zip"）
     * @param dir     待压缩目录
     * @param filter  文件名过滤器
     */
    public static void zip(File zipFile, File dir, FilenameFilter filter) throws IOException {
        checkSource(dir, "待压缩文件");
        if (!dir.isDirectory() && !dir.isFile()) {
            throw new IllegalArgumentException(String.format("待压缩文件[%s]不是文件或者目录，请核对后再处理.", dir.getAbsolutePath()));
        }
        if (zipFile == null) {
            zipFile = new File(dir.getAbsolutePath() + ".zip");
        }
        File target = prepareOutputFile(zipFile);
        final String entryName = dir.isDirectory() ? "" : baseName(dir);
        writeNewFile(target, out -> {
            try (ZipOutputStream zipOut = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                zipAdd(zipOut, dir, entryName, filter, new HashSet<>());
            }
        });
    }

    /**
     * 递归压缩文件（不跟随符号链接目录）.<br>
     * @param entryName 压缩包内的条目名（目录时为空串，表示目录内容直接放在根）
     * @param names     已添加的条目名（用于去重）
     */
    private static void zipAdd(ZipOutputStream out, File current, String entryName, FilenameFilter filter,
                               Set<String> names) throws IOException {
        if (current.isDirectory()) {
            String prefix = "";
            if (!entryName.isEmpty()) {
                zipAddDirEntry(out, entryName + "/", current, names);
                prefix = entryName + "/";
            }
            for (File item : listSorted(current)) {
                if ((filter != null) && !filter.accept(current, item.getName())) {
                    continue;
                }
                if (item.isDirectory() && Files.isSymbolicLink(item.toPath())) {
                    continue;
                }
                zipAdd(out, item, prefix + item.getName(), filter, names);
            }
        } else if (current.isFile()) {
            if (!names.add(entryName)) {
                throw new IllegalArgumentException(String.format("压缩包内条目[%s]重复", entryName));
            }
            ZipEntry entry = new ZipEntry(entryName);
            entry.setTime(current.lastModified());
            out.putNextEntry(entry);
            Files.copy(current.toPath(), out);
            out.closeEntry();
        } else {
            throw new IllegalArgumentException(String.format("待压缩文件[%s]不存在", current.getAbsolutePath()));
        }
    }

    /**
     * 添加文件夹条目（已添加过则忽略）.
     */
    private static void zipAddDirEntry(ZipOutputStream out, String name, File dir, Set<String> names) throws IOException {
        if (names.add(name)) {
            ZipEntry entry = new ZipEntry(name);
            entry.setTime(dir.lastModified());
            out.putNextEntry(entry);
            out.closeEntry();
        }
    }

    /**
     * zip解压<br>
     * 目标目录下的同名文件会被覆盖；解压后的总体积不能超过{@link #DEFAULT_MAX_EXTRACT_SIZE}.
     * @param zipFile 待解压文件（zip文件）
     * @param target  目标目录（为空时见类说明）
     */
    public static void unZip(File zipFile, File target) throws IOException {
        unZip(zipFile, target, StandardCharsets.UTF_8, DEFAULT_MAX_EXTRACT_SIZE);
    }

    /**
     * zip解压
     * @param zipFile 待解压文件（zip文件）
     * @param target  目标目录（为空时见类说明）
     * @param maxSize 解压后的最大总字节数（必须大于0）
     * @throws SizeLimitExceededException 解压后的数据体积超过maxSize
     */
    public static void unZip(File zipFile, File target, long maxSize) throws IOException {
        unZip(zipFile, target, StandardCharsets.UTF_8, maxSize);
    }

    /**
     * zip解压
     * @param zipFile 待解压文件（zip文件）
     * @param target  目标目录（为空时见类说明）
     * @param charset 条目名称的字符集（为空则UTF-8；Windows资源管理器压缩的包一般是GBK）
     */
    public static void unZip(File zipFile, File target, Charset charset) throws IOException {
        unZip(zipFile, target, charset, DEFAULT_MAX_EXTRACT_SIZE);
    }

    /**
     * zip解压
     * @param zipFile 待解压文件（zip文件）
     * @param target  目标目录（为空时见类说明）
     * @param charset 条目名称的字符集（为空则UTF-8）
     * @param maxSize 解压后的最大总字节数（必须大于0）
     * @throws SizeLimitExceededException 解压后的数据体积超过maxSize
     */
    public static void unZip(File zipFile, File target, Charset charset, long maxSize) throws IOException {
        checkMaxSize(maxSize);
        Path archive = checkArchive(zipFile);
        if (!isZip(zipFile)) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不是有效的zip文件", archive.toAbsolutePath()));
        }
        File dir = (target == null)
                ? new File(zipFile.getAbsoluteFile().getParentFile(), defaultName(zipFile.getName(), ".zip"))
                : target;
        Path root = prepareTargetDir(dir);
        ByteBudget budget = new ByteBudget(maxSize);
        try (ZipFile zip = new ZipFile(zipFile, charset == null ? StandardCharsets.UTF_8 : charset)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry item = entries.nextElement();
                Path output = resolveArchiveEntry(root, item.getName());
                if (item.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }
                if (item.getSize() > budget.remaining()) { // 声明的大小已超限，提前终止
                    throw new SizeLimitExceededException(maxSize);
                }
                createParent(output);
                try (InputStream input = new LimitedInputStream(zip.getInputStream(item), budget)) {
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // 公共方法
    // ------------------------------------------------------------------------------------------------

    /**
     * 将归档条目解析到目标目录，并阻止 ../ 或绝对路径逃逸。
     */
    private static Path resolveArchiveEntry(Path targetRoot, String entryName) throws IOException {
        Path root = targetRoot.toAbsolutePath().normalize();
        Path output = root.resolve(entryName).normalize();
        if (!output.startsWith(root)) {
            throw new IOException("非法归档路径：" + entryName);
        }
        return output;
    }

    /**
     * 校验待压缩的文件列表.
     */
    private static void checkSources(List<File> files) {
        if ((files == null) || files.isEmpty()) {
            throw new IllegalArgumentException("待压缩文件不能为空");
        }
        for (File file : files) {
            checkSource(file, "待压缩文件");
        }
    }

    /**
     * 校验待压缩的文件（目录）存在.
     */
    private static void checkSource(File file, String desc) {
        if (file == null) {
            throw new IllegalArgumentException("必须传入" + desc);
        } else if (!file.exists()) {
            throw new IllegalArgumentException(String.format("%s[%s]不存在", desc, file.getAbsolutePath()));
        }
    }

    /**
     * 校验待解压的资源是一个存在的、可读的、非空的文件，返回其路径.
     */
    private static Path checkArchive(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("必须传入待解压资源");
        }
        Path path = file.toPath();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不存在", path.toAbsolutePath()));
        } else if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不是一个有效的文件", path.toAbsolutePath()));
        } else if (!Files.isReadable(path)) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不可读", path.toAbsolutePath()));
        } else if (Files.size(path) <= 0) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]内容为空", path.toAbsolutePath()));
        }
        return path;
    }

    private static void checkMaxSize(long maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("解压体积限制必须大于0");
        }
    }

    /**
     * 压缩目标文件处理：不能已存在，并创建其上级目录（支持相对路径）.
     * @return 目标文件的绝对路径
     */
    private static File prepareOutputFile(File file) throws IOException {
        File abs = file.getAbsoluteFile();
        if (abs.exists()) {
            throw new IllegalArgumentException(String.format("压缩后的文件[%s]已经存在", abs.getAbsolutePath()));
        }
        File parent = abs.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        return abs;
    }

    /**
     * 解压目标目录处理：不存在则创建，必须是可写的目录.
     * @return 目标目录的绝对路径
     */
    private static Path prepareTargetDir(File dir) throws IOException {
        Path root = dir.toPath().toAbsolutePath().normalize();
        if (Files.exists(root) && !Files.isDirectory(root)) {
            throw new IllegalArgumentException(String.format("目标路径[%s]已经存在，且不是文件夹", root));
        }
        Files.createDirectories(root);
        if (!Files.isWritable(root)) {
            throw new IllegalArgumentException(String.format("目标路径[%s]不可写", root));
        }
        return root;
    }

    private static void createParent(Path output) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    /**
     * 新建目标文件并写入内容. 写入失败时（包括关闭流时写入尾部失败），删除未完成的目标文件.
     */
    private static void writeNewFile(File target, StreamWriter writer) throws IOException {
        Path path = target.toPath();
        OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW);
        boolean success = false;
        try {
            try (OutputStream out = new BufferedOutputStream(os, BUF_SIZE)) {
                writer.write(out);
            }
            success = true;
        } finally {
            if (!success) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    // 删除失败不影响原始异常
                }
            }
        }
    }

    @FunctionalInterface
    private interface StreamWriter {
        void write(OutputStream out) throws IOException;
    }

    /**
     * 列出目录下的文件（按名称排序，保证压缩结果顺序稳定）.
     */
    private static File[] listSorted(File dir) throws IOException {
        File[] list = dir.listFiles();
        if (list == null) {
            throw new IOException(String.format("无法读取目录[%s]", dir.getAbsolutePath()));
        }
        Arrays.sort(list);
        return list;
    }

    /**
     * 文件（目录）名称（相对路径、"."等也能得到正确的名称）.
     */
    private static String baseName(File file) {
        Path name = file.toPath().toAbsolutePath().normalize().getFileName();
        if (name == null) {
            throw new IllegalArgumentException(String.format("无法压缩根目录[%s]", file.getAbsolutePath()));
        }
        return name.toString();
    }

    /**
     * 文件相对于根目录的路径，文件必须位于根目录之下.
     */
    private static Path relativeTo(Path base, File file) {
        Path abs = file.toPath().toAbsolutePath().normalize();
        if (!abs.startsWith(base) || abs.equals(base)) {
            throw new IllegalArgumentException(String.format("文件[%s]不在根目录[%s]之下", abs, base));
        }
        return base.relativize(abs);
    }

    private static String toEntryName(Path path) {
        return path.toString().replace('\\', '/');
    }

    /**
     * 去掉指定后缀（忽略大小写）；没有匹配的后缀时原样返回.
     */
    private static String stripExtension(String name, String... extensions) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : extensions) {
            if (lower.endsWith(ext) && (name.length() > ext.length())) {
                return name.substring(0, name.length() - ext.length());
            }
        }
        return name;
    }

    /**
     * 默认的解压名称：去掉指定后缀；没有匹配的后缀时追加.out，避免与压缩包同名.
     */
    private static String defaultName(String name, String... extensions) {
        String result = stripExtension(name, extensions);
        return result.equals(name) ? name + ".out" : result;
    }

    /**
     * 解压体积额度（可跨多个流共享，如zip中的多个条目）.
     */
    private static final class ByteBudget {
        private final long max;
        private long used;

        ByteBudget(long max) {
            this.max = max;
        }

        void consume(long count) throws SizeLimitExceededException {
            if (count > 0) {
                used += count;
                if (used > max) {
                    throw new SizeLimitExceededException(max);
                }
            }
        }

        long remaining() {
            return max - used;
        }
    }

    /**
     * 限制读取字节总数的输入流，超出额度时抛出{@link SizeLimitExceededException}.
     */
    private static final class LimitedInputStream extends FilterInputStream {
        private final ByteBudget budget;

        LimitedInputStream(InputStream in, ByteBudget budget) {
            super(in);
            this.budget = budget;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                budget.consume(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int num = super.read(b, off, len);
            budget.consume(num);
            return num;
        }

        @Override
        public long skip(long n) throws IOException {
            long num = super.skip(n);
            budget.consume(num);
            return num;
        }

        @Override
        public boolean markSupported() {
            return false;
        }
    }
}
