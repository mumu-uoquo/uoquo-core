/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils;

import com.uoquo.utils.ecg.DataUtil;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Map.Entry;

import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 描述：压缩工具类. <br>
 * 日期：2018-02-24 16:32 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-02-24     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class CompressUtil {
    private static final int BUF_SIZE = 1024 * 1024;
    // private static final byte[] ZIP_HEADER_1 = new byte[] { 80, 75, 3, 4 };
    // private static final byte[] ZIP_HEADER_2 = new byte[] { 80, 75, 5, 6 };

    /**
     * 是否gzip文件.
     */
    public static boolean isGzip(File file) throws IOException {
        if ((file == null) || file.isDirectory()) {
            return false;
        }
        final byte[] header = new byte[10];
        try (FileInputStream fis = new FileInputStream(file)) {
            if (fis.read(header) != header.length) {
                return false;
            }
        }
        return isGzip(header);
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
        return c == 8; // Unsupported compression method
    }

    /**
     * gzip压缩数据.
     * @param data 带压缩的byte数组
     * @return 压缩后的byte数组
     * @throws IOException 异常信息
     */
    public static byte[] gzip(byte[] data) throws IOException {
        if ((data == null) || (data.length == 0)) {
            return null;
        }
        ByteArrayInputStream  bis = null;
        ByteArrayOutputStream bos = null;
        try {
            bis = new ByteArrayInputStream(data);
            bos = new ByteArrayOutputStream(BUF_SIZE);
            gzip(bis, bos);
            return bos.toByteArray();
        } finally {
            close(bis);
            close(bos);
        }
    }

    /**
     * gzip压缩文件（目录）.<br>
     * @param gzFile 目标文件
     * @param files  待压缩文件
     */
    public static void gzip(File gzFile, File... files) throws IOException {
        gzip(gzFile, Arrays.asList(files));
    }

    /**
     * gzip压缩文件（目录）.<br>
     * @param gzFile 目标文件
     * @param files  待压缩文件
     */
    public static void gzip(File gzFile, List<File> files) throws IOException {
        // 目标文件处理
        if (gzFile == null) {
            if (files.size() != 1) {
                throw new IllegalArgumentException("请传入目标文件");
            }
            gzFile = new File(files.getFirst().getAbsolutePath() + ".gz");
        }
        if (gzFile.exists()) {
            throw new IllegalArgumentException(String.format("压缩后的文件[%s]已经存在", gzFile.getAbsolutePath()));
        } else if (!gzFile.getParentFile().exists()) {
            gzFile.getParentFile().mkdirs();
        }
        // 如果是多份文件，或者文件夹，则先进行tar打包，再压缩
        File tarFile = null;
        File srcFile = null;
        if ((files.size() > 1) || files.getFirst().isDirectory()) {
            String gzPath = gzFile.getAbsolutePath();
            if (gzPath.endsWith(".gz")) {
                tarFile = new File(gzPath.substring(0, gzPath.length() - 3) + ".tar");
            } else {
                tarFile = new File(gzPath + ".tar");
            }
            tar(tarFile, files);
            srcFile = tarFile;
            gzFile  = new File(tarFile.getAbsolutePath() + ".gz");
        } else {
            srcFile = files.getFirst();
        }
        // 单文件压缩
        try (FileInputStream  fis = new FileInputStream(srcFile);
             FileOutputStream fos = new FileOutputStream(gzFile);){
            gzip(fis, fos);
        }
        // 如果是先打的tar包，则压缩后将其删除
        if (tarFile != null) {
            tarFile.delete();
        }
    }

    /**
     * gzip压缩目录.<br>
     * 备注：也支持单个文件，单个文件时，filter无效
     * @param gzFile 目标文件
     * @param dir    待压缩目录
     * @param filter 文件名过滤器
     */
    public static void gzip(File gzFile, File dir, FilenameFilter filter) throws IOException {
        // 目标文件处理
        if (gzFile == null) {
            gzFile = new File(dir.getAbsolutePath() + ".gz");
        }
        if (gzFile.exists()) {
            throw new IllegalArgumentException(String.format("压缩后的文件[%s]已经存在", gzFile.getAbsolutePath()));
        } else if (!gzFile.getParentFile().exists()) {
            gzFile.getParentFile().mkdirs();
        }
        // 文件夹压缩，先用apache打tar包，再将tar压缩位tar.gz
        File tarFile = null;
        File srcFile = null;
        if (dir.isDirectory()) {
            String gzPath = gzFile.getAbsolutePath();
            if (gzPath.endsWith(".gz")) {
                tarFile = new File(gzPath.substring(0, gzPath.length() - 3) + ".tar");
            } else {
                tarFile = new File(gzPath + ".tar");
            }
            tar(tarFile, dir, filter);
            srcFile = tarFile;
            gzFile  = new File(tarFile.getAbsolutePath() + ".gz");
        } else if (dir.isFile()) {
            srcFile = dir;
        } else {
            throw new IllegalArgumentException(String.format("待压缩文件[%s]不是文件或者目录，请核对后再处理.", dir.getAbsolutePath()));
        }
        // 单文件压缩
        try (FileInputStream  fis = new FileInputStream(srcFile);
             FileOutputStream fos = new FileOutputStream(gzFile);){
            gzip(fis, fos);
        }
        // 如果是先打的tar包，则压缩后将其删除
        if (tarFile != null) {
            tarFile.delete();
        }
    }

    /**
     * 执行gzip压缩.
     */
    private static void gzip(InputStream in, OutputStream out) throws IOException {
        GZIPOutputStream gzip = null;
        try {
            gzip = new GZIPOutputStream(out);
            byte[] buffer = new byte[BUF_SIZE];
            int len = -1;
            while ((len = in.read(buffer, 0, BUF_SIZE)) != -1) {
                gzip.write(buffer, 0, len);
            }
            gzip.flush();
        } finally {
            close(gzip);
        }
    }

    /**
     * gzip解压.
     * @param data 待解压的byte数组
     * @return 解压后的byte数组
     * @throws IOException 异常信息
     */
    public static byte[] unGzip(byte[] data) throws IOException {
        try (
                ByteArrayOutputStream bos = new ByteArrayOutputStream(BUF_SIZE);
                ByteArrayInputStream  bis = new ByteArrayInputStream(data);
        ) {
            GZIPInputStream gzip  = new GZIPInputStream(bis);
            byte[] buffer = new byte[BUF_SIZE];
            int num = -1;
            while ((num = gzip.read(buffer, 0, BUF_SIZE)) != -1) {
                bos.write(buffer, 0, num);
            }
            gzip.close();
            bos.flush();
            return bos.toByteArray();
        }
    }

    /**
     * gzip解压.
     * @param gzFile 待解压文件（gz文件）
     * @param target 目标目录
     */
    public static void unGzip(File gzFile, File target) throws IOException {
        // 源文件处理
        if (gzFile == null) {
            throw new IllegalArgumentException("必须传入待解压资源");
        } else if (!gzFile.exists()) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不存在", gzFile.getAbsolutePath()));
        } else if (!gzFile.isFile()) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不是一个有效的文件", gzFile.getAbsolutePath()));
        } else if (!gzFile.canRead()) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不可读", gzFile.getAbsolutePath()));
        } else if (gzFile.length() <= 0) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]内容为空", gzFile.getAbsolutePath()));
        } else if (!isGzip(gzFile)) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不是有效的gzip文件", gzFile.getAbsolutePath()));
        }
        // 目标路径处理
        String tarPath = null; // tar包的解压路径（仅tar适用）
        String gzName  = gzFile.getName();
        gzName = gzName.substring(0, gzName.lastIndexOf(".")); // 去除.gz后缀
        if (target == null) {
            target  = new File(gzFile.getParent()); // 解压到当前目录
            tarPath = gzFile.getParent() + File.separator + gzName.substring(0, gzName.lastIndexOf(".")); // 去除.tar后缀
        } else {
            tarPath = target.getAbsolutePath() + File.separator;
        }
        if (target.exists()) {
            if (!target.isDirectory()) {
                throw new IllegalArgumentException(String.format("目标路径[%s]已经存在，且不是文件夹", target.getAbsolutePath()));
            }
        } else {
            target.mkdirs();
        }
        // gzip解压
        String unPath = target.getAbsolutePath() + File.separator;
        target = new File(unPath + gzName + ".tmp");
        try (
                FileInputStream fis = new FileInputStream(gzFile);
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(target), BUF_SIZE);
        ) {
            GZIPInputStream gzip = new GZIPInputStream(fis);
            byte[] buffer = new byte[BUF_SIZE];
            int num = -1;
            while ((num = gzip.read(buffer, 0, BUF_SIZE)) != -1) {
                bos.write(buffer, 0, num);
            }
            bos.flush();
            gzip.close();
        }
        // 判断是否tar包
        if (isTar(target)) {
            unTar(target, new File(tarPath));
            target.delete();
        } else {
            target.renameTo(new File(unPath + gzName));
        }
    }

    /**
     * 是否是tar文件.
     */
    public static boolean isTar(File file) throws IOException {
        if ((file == null) || file.isDirectory()) {
            return false;
        }
        // http://en.wikipedia.org/wiki/Tar_(computing)#File_header
        final byte[] header = new byte[512];
        try (FileInputStream fis = new FileInputStream(file)) {
            if (fis.read(header) != header.length) {
                return false;
            }
        }
        return isTar(header);
    }

    /**
     * 是否是tar包数据.
     * 参考 {@link org.eclipse.che.commons.lang.TarUtils#isTarFile(File)}
     */
    public static boolean isTar(byte[] data) throws IOException {
        return TarArchiveInputStream.matches(data, data.length);
    }

    /**
     * tar打包文件.<br>
     * @param tar   目标文件
     * @param files 待打包文件
     */
    public static void tar(File tar, File... files) throws IOException {
        tar(tar, Arrays.asList(files));
    }

    /**
     * tar打包文件.<br>
     * @param tar   目标文件
     * @param files 待打包文件
     */
    public static void tar(File tar, List<File> files) throws IOException {
        // 目标文件处理
        if (tar == null) {
            if (files.size() != 1) {
                throw new IllegalArgumentException("请传入目标文件");
            }
            tar = new File(files.getFirst().getAbsolutePath() + ".tar");
        }
        if (tar.exists()) {
            throw new IllegalArgumentException(String.format("压缩后的文件[%s]已经存在", tar.getAbsolutePath()));
        } else if (!tar.getParentFile().exists()) {
            tar.getParentFile().mkdirs();
        }
        // 文件压缩
        try (TarArchiveOutputStream tarOut =
                     new TarArchiveOutputStream(new BufferedOutputStream(new FileOutputStream(tar)))) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (File file : files) {
                if (file.isDirectory()) {
                    tarAddDirEntry(tarOut, file.getName(), file);
                    // 递归添加文件夹内容
                    final String parentPath = file.getParentFile().getAbsolutePath();
                    tarAddDir(tarOut, parentPath, file, null);
                } else if (file.isFile()) {
                    tarAddFile(tarOut, file.getName(), file);
                }
            }
        }
    }

    /**
     * tar打包文件夹.<br>
     * @param tar    目标文件
     * @param dir    待打包文件夹
     * @param filter 文件名过滤器
     */
    public static void tar(File tar, File dir, FilenameFilter filter) throws IOException {
        // 目标文件处理
        if (tar == null) {
            tar = new File(dir.getAbsolutePath() + ".tar");
        }
        if (tar.exists()) {
            throw new IllegalArgumentException(String.format("压缩后的文件[%s]已经存在", tar.getAbsolutePath()));
        } else if (!tar.getParentFile().exists()) {
            tar.getParentFile().mkdirs();
        }
        // 文件压缩
        if (dir.isFile()) {
            tar(tar, dir); // 如果是文件，则按文件的方式压缩
        } else if (dir.isDirectory()) {
            try (TarArchiveOutputStream tarOut =
                         new TarArchiveOutputStream(new BufferedOutputStream(new FileOutputStream(tar)))) {
                tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                // 递归添加文件夹内容
                final String parentPath = dir.getAbsolutePath();
                tarAddDir(tarOut, parentPath, dir, filter);
            }
        } else {
            throw new IllegalArgumentException(String.format("待压缩文件[%s]不是文件或者目录，请核对后再处理.", dir.getAbsolutePath()));
        }
    }

    /**
     * 在tar包中添加文件夹标识
     * @param tarOut tar输出流
     * @param name   tar中的名称
     * @param dir    待打包目录
     */
    private static void tarAddDirEntry(TarArchiveOutputStream tarOut, String name, File dir) throws IOException {
        final TarArchiveEntry tarEntry = new TarArchiveEntry(dir, name);
        //tarEntry.setModTime(modTime);
        tarOut.putArchiveEntry(tarEntry);
        tarOut.closeArchiveEntry();
    }

    /**
     * 将文件夹内容添加tar包（递归处理）
     * @param tarOut   tar输出流
     * @param basePath 基础路径
     * @param dir      待打包目录
     * @param filter   文件名过滤器
     */
    private static void tarAddDir(TarArchiveOutputStream tarOut, String basePath, File dir, FilenameFilter filter) throws IOException {
        final int basePathLength = basePath.length() + 1;
        final LinkedList<File> dirQueue = new LinkedList<>(); // 待处理文件夹栈
        dirQueue.add(dir);
        while (!dirQueue.isEmpty()) {
            final File current = dirQueue.pop();
            final File[] list  = current.listFiles();
            if (list == null) {
                continue;
            }
            for (File file : list) {
                if ((filter == null) || filter.accept(current, file.getName())) {
                    final String entryName = file.getAbsolutePath().substring(basePathLength).replace('\\', '/');
                    if (file.isDirectory()) {
                        tarAddDirEntry(tarOut, entryName, file);
                        dirQueue.push(file); // 入栈，继续处理（递归调用）
                    } else if (file.isFile()) {
                        tarAddFile(tarOut, entryName, file);
                    }
                }
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
        final TarArchiveEntry tarEntry = new TarArchiveEntry(file, name);
        //tarEntry.setModTime(modTime);
        tarOut.putArchiveEntry(tarEntry);
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            final byte[] buf = new byte[BUF_SIZE];
            int len = -1;
            while ((len = in.read(buf)) != -1) {
                tarOut.write(buf, 0, len);
            }
        }
        tarOut.closeArchiveEntry();
    }

    /**
     * tar解压
     * @param tarFile 待解压文件（tar文件）
     * @param target  目标目录
     */
    public static void unTar(File tarFile, File target) throws IOException {
        // 源文件处理
        if (tarFile == null) {
            throw new IllegalArgumentException("必须传入待解压资源");
        } else if (!tarFile.exists()) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不存在", tarFile.getAbsolutePath()));
        } else if (!tarFile.isFile()) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不是一个有效的文件", tarFile.getAbsolutePath()));
        } else if (!tarFile.canRead()) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不可读", tarFile.getAbsolutePath()));
        } else if (tarFile.length() <= 0) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]内容为空", tarFile.getAbsolutePath()));
        } else if (!isTar(tarFile)) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不是有效的tar文件", tarFile.getAbsolutePath()));
        }
        // 目标路径处理
        if (target == null) {
            String tarName = tarFile.getName();
            target = new File(tarFile.getParent() + tarName.substring(tarName.indexOf(".")));
        }
        if (target.exists()) {
            if (!target.isDirectory()) {
                throw new IllegalArgumentException(String.format("目标路径[%s]已经存在，且不是文件夹", target.getAbsolutePath()));
            }
        } else {
            target.mkdirs();
        }
        // 解压
        try(TarArchiveInputStream tarIn = new TarArchiveInputStream(new FileInputStream(tarFile));) {
            byte[] byts = new byte[BUF_SIZE];
            TarArchiveEntry tarEntry = null;
            while ((tarEntry = tarIn.getNextEntry()) != null) {
                final File file = new File(target, tarEntry.getName());
                // 文件夹处理
                if (tarEntry.isDirectory()) {
                    if (!file.mkdirs()) {
                        throw new IOException("Unable to create folder " + file.getAbsolutePath());
                    }
                    continue;
                }
                // 解压文件
                final File parent = file.getParentFile();
                if (!parent.exists()) {
                    if (!parent.mkdirs()) {
                        throw new IOException("Unable to create folder " + parent.getAbsolutePath());
                    }
                }
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    int len = -1;
                    while ((len = tarIn.read(byts)) != -1) {
                        fos.write(byts, 0, len);
                    }
                }
            }
        }
    }

    /**
     * 是否zip文件.
     * 参考 <a href="http://en.wikipedia.org/wiki/Zip_(file_format)#File_headers">headers description</a>
     */
    public static boolean isZip(File file) throws IOException {
        if (file.isDirectory()) {
            return false;
        }
        // NOTE: little-indian bytes order!
        final byte[] bytes = new byte[4];
        try (FileInputStream fIn = new FileInputStream(file)) {
            if (fIn.read(bytes) != bytes.length) {
                return false;
            }
        }
        return isZip(bytes);
    }

    /**
     * 是否zip压缩数据. <br>
     * 参考 {@link org.eclipse.che.commons.lang.ZipUtils#isZipFile(File)}
     * @param data 待校验数据（不少于4字节）
     */
    public static boolean isZip(byte[] data) {
        if ((data == null) || (data.length < 4)) {
            return false;
        }
        ByteBuffer header = ByteBuffer.wrap(data);
        header.order(ByteOrder.LITTLE_ENDIAN);
        //return 0x04034b50 == header.getInt();
        return (0x04034b50 == header.getInt()) || (0x06054b50 == header.getInt());
        /* 原网上逻辑，与上述一致
        byte[] head = new byte[4];
        System.arraycopy(data, 0, head, 0, head.length);
        return Arrays.equals(ZIP_HEADER_1, head) || Arrays.equals(ZIP_HEADER_2, head);
         */
    }

    /**
     * zip压缩指定字节流.
     * @param data     待压缩的字节流
     * @param fileName 压缩文件中的文件名
     * @return 压缩后的数据流
     * @throws IOException io异常
     */
    public static byte[] zip(byte[] data, String fileName) throws IOException {
        if (data == null) {
            return null;
        }
        if (StringUtil.isNull(fileName)) {
            fileName = "undefined.dat";
        }
        Map<String, byte[]> map = new HashMap<>();
        map.put(fileName, data);
        return zip(map);
    }

    /**
     * zip压缩指定字节流.
     * @param data     待压缩的字节流
     * @param fileName 压缩文件中的文件名
     * @param zipPath  目标zip文件（为空，则默认为unknown.dat）
     */
    public static void zip(byte[] data, String fileName, String zipPath) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("没有需要压缩的数据");
        }
        // 目标路径处理
        if (StringUtil.isNull(zipPath)) {
            throw new IllegalArgumentException("请指定压缩后的文件存储路径");
        } else if (!zipPath.endsWith(".zip")) {
            throw new IllegalArgumentException(String.format("压缩后的文件[%s]只能是.zip结尾的文件", zipPath));
        }
        File zipFile = new File(zipPath);
        if (zipFile.exists()) {
            throw new IllegalArgumentException(String.format("压缩后的文件[%s]已经存在", zipPath));
        } else if (!zipFile.getParentFile().exists()) {
            zipFile.getParentFile().mkdirs();
        }
        // 压缩文件名称处理
        if (StringUtil.isNull(fileName)) {
            fileName = "unknown.dat";
        }
        // 压缩
        ZipOutputStream out = null;
        try {
            out = new ZipOutputStream(new FileOutputStream(zipFile));
            out.putNextEntry(new ZipEntry(fileName));
            out.write(data);
        } finally {
            close(out);
        }
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
        ByteArrayOutputStream bos = new ByteArrayOutputStream(BUF_SIZE);
        ZipOutputStream zipOut = new ZipOutputStream(bos);
        for (Entry<String, byte[]> item : map.entrySet()) {
            ZipEntry zipEntry = new ZipEntry(item.getKey());
            zipOut.putNextEntry(zipEntry);
            zipOut.write(item.getValue());
            zipOut.closeEntry();
        }
        zipOut.close();
        return bos.toByteArray();
    }

    /**
     * zip压缩文件（目录）.<br>
     * @param zipFile  目标文件
     * @param basePath 跟目录（绝对地址，可以为空）
     * @param files    待压缩文件
     */
    public static void zip(File zipFile, String basePath, File... files) throws IOException {
        zip(zipFile, basePath, Arrays.asList(files));
    }

    /**
     * zip压缩文件（目录）.<br>
     * @param zipFile  目标文件
     * @param basePath 跟目录（绝对地址，可以为空）
     * @param files    待压缩文件
     */
    public static void zip(File zipFile, String basePath, List<File> files) throws IOException {
        // 目标文件处理
        if (zipFile == null) {
            if (files.size() != 1) {
                throw new IllegalArgumentException("请传入目标文件");
            }
            zipFile = new File(files.getFirst().getAbsolutePath() + ".gz");
        }
        if (zipFile.exists()) {
            throw new IllegalArgumentException(String.format("压缩后的文件[%s]已经存在", zipFile.getAbsolutePath()));
        } else if (!zipFile.getParentFile().exists()) {
            zipFile.getParentFile().mkdirs();
        }
        // 批量压缩
        ZipOutputStream zipOut = null;
        try {
            HashSet<String> names = new HashSet<>();
            zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)), Charset.forName("UTF-8"));
            for (File file : files) {
                String base = "";
                if (StringUtil.notNull(basePath)) {
                    int idx = file.getAbsolutePath().indexOf(basePath);
                    if (idx > -1) {
                        idx += basePath.length();
                        String temp = file.getAbsolutePath().substring(idx);
                        List<String> dirs = StringUtil.split(temp, "/\\"); // 按目录分隔符切割字符串
                        for (String item : dirs) {
                            if (StringUtil.isNull(item)) {
                                continue;
                            }
                            base += item + "/";
                            if (! names.add(base)) {
                                zipOut.putNextEntry(new ZipEntry(base));
                            }
                        }
                        base += file.getName();
                    }
                }
                zip(file, zipOut, base, null);
            }
        } finally {
            close(zipOut);
        }
    }

    /**
     * zip压缩目录.<br>
     * 备注：也支持单个文件，单个文件时，filter无效
     * @param zipFile 目标文件
     * @param dir     待压缩目录
     * @param filter  文件名过滤器
     */
    public static void zip(File zipFile, File dir, FilenameFilter filter) throws IOException {
        ZipOutputStream zipOut = null;
        if (zipFile.exists()) {
            throw new IllegalArgumentException(String.format("压缩后的文件[%s]已经存在", zipFile.getAbsolutePath()));
        } else {
            if (!zipFile.getParentFile().exists()) {
                zipFile.getParentFile().mkdirs();
            }
            zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)), Charset.forName("UTF-8"));
        }
        // 2. 压缩
        try {
            zip(dir, zipOut, "", filter);
        } finally {
            close(zipOut);
        }
    }

    /**
     * 递归压缩文件.<br>
     */
    private static void zip(File current, ZipOutputStream out, String base, FilenameFilter filter) throws IOException {
        base = (base == null) ? "" : base;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        // 文件夹，递归处理
        if (current.isDirectory()) {
            File[] subs = current.listFiles();
            // 在压缩文件中添加文件夹
            if (!base.isEmpty()) {
                base = base + "/";
                out.putNextEntry(new ZipEntry(base));
            }
            // 递归处理文件夹
            if (subs != null) {
                for (File item : subs) {
                    if ((filter == null) || filter.accept(current, item.getName())) {
                        zip(item, out, base + item.getName(), filter);
                    }
                }
            }
            return;
        }
        // 添加文件头
        if (!base.isEmpty()) {
            out.putNextEntry(new ZipEntry(base));
        } else {
            out.putNextEntry(new ZipEntry(current.getName()));
        }
        // 压缩文件
        BufferedInputStream buffIn = null;
        try {
            buffIn = new BufferedInputStream(new FileInputStream(current), BUF_SIZE);
            zipWrite(buffIn, out);
            out.closeEntry();
        } finally {
            close(buffIn);
        }
    }
    
    /**
     * 写入压缩文件.
     */
    private static void zipWrite(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[BUF_SIZE];
        int length = -1;
        while ((length = in.read(buf)) != -1) {
            out.write(buf, 0, length);
        }
        out.flush(); // 刷新输出流（输出流不关闭，后续可能还需要写入，在最外层关闭输出流）
        in.close();  // 关闭输入流
    }
    
    /**
     * zip解压
     * @param zipFile 待解压文件（tar文件）
     * @param target  目标目录（为空，则解压到zip文件所在目录）
     */
    public static void unZip(File zipFile, File target) throws IOException {
        // 源文件处理
        if (zipFile == null) {
            throw new IllegalArgumentException("必须传入待解压资源");
        } else if (!zipFile.exists()) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不存在", zipFile.getAbsolutePath()));
        } else if (!zipFile.isFile()) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不是一个有效的文件", zipFile.getAbsolutePath()));
        } else if (!zipFile.canRead()) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不可读", zipFile.getAbsolutePath()));
        } else if (zipFile.length() <= 0) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]内容为空", zipFile.getAbsolutePath()));
        } else if (!isZip(zipFile)) {
            throw new IllegalArgumentException(String.format("待解压资源[%s]不是有效的zip文件", zipFile.getAbsolutePath()));
        }
        // 目标路径处理
        if (target == null) {
            target = new File(zipFile.getParent());
        }
        if (!target.exists()) {
            target.mkdirs();
        } else if (!target.isDirectory()) {
            throw new IllegalArgumentException(String.format("目标路径[%s]不是一个有效的目录", target.getAbsolutePath()));
        } else if (!target.canWrite()) {
            throw new IllegalArgumentException(String.format("目标路径[%s]不可写", target.getAbsolutePath()));
        }
        // 开始解压
        ZipFile zip = new ZipFile(zipFile);
        Enumeration<? extends ZipEntry> et = zip.entries();
        while (et.hasMoreElements()) {
            ZipEntry item = et.nextElement();
            String tempPath = target.getAbsolutePath() + File.separator + item.getName();
            // 如果是文件夹，则创建对应的文件夹
            if (item.isDirectory()) {
                File dir = new File(tempPath);
                dir.mkdirs();
                continue;
            }
            File temp = new File(tempPath);
            if (temp.exists()) {
                if (temp.length() == item.getSize()) {
                    continue; // 文件已经存在，且大小相等，则跳过
                }
            } else if (!temp.getParentFile().exists()) {
                temp.getParentFile().mkdirs(); // 父级文件夹不存在，则创建文件夹
            }
            // 读写文件
            try (
                BufferedInputStream  bis = new BufferedInputStream(zip.getInputStream(item));
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tempPath));
            ) {
                byte[] buf = new byte[BUF_SIZE];
                int ch = -1;
                while ((ch = bis.read(buf)) != -1) {
                    bos.write(buf, 0, ch);
                }
                bos.flush();
            }
        }
        zip.close();
    }
    
    // 关闭输出流
    private static void close(Closeable obj) {
        try {
            if (obj != null) {
                obj.close();
            }
        } catch (Exception e) {
            // do nothing
        }
    }
}
