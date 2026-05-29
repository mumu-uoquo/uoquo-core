/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test;

import com.uoquo.utils.CompressUtil;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.ecg.DataUtil;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CompressTest {

    @Test
    public void testGzipData() {
        String msg = "hello, 中文";
        try {
            System.out.println(msg);
            byte[] data = CompressUtil.gzip(msg.getBytes());
            String temp = new String(CompressUtil.unGzip(data));
            System.out.println(temp);
            data = CompressUtil.gzip(msg.getBytes("UTF-8"));
            temp = new String(CompressUtil.unGzip(data), "UTF-8");
            System.out.println(temp);
            data = CompressUtil.gzip(msg.getBytes("GBK"));
            temp = new String(CompressUtil.unGzip(data), "GBK");
            System.out.println(temp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testGzipFile() {
        List<File> files = new ArrayList<>();
        files.add(new File("D:/4G.DAT"));
        files.add(new File("D:/2011.DAT"));
        files.add(new File("D:/2015DAT"));

        try {
//            CompressUtil.gzip(null, files.get(0));
            File target = new File("D:/gz-1.gz");
            CompressUtil.gzip(target, files.get(0));
            target = new File("D:/gz-2.gz");
            CompressUtil.gzip(target, files.get(0), files.get(1));
            target = new File("D:/gz-3.gz");
            CompressUtil.gzip(target, files);
            target = new File("D:/gz-4.gz");
            CompressUtil.gzip(target, new File("D:/viwer"), (dir, name)->{
                File file = new File(dir.getAbsolutePath() + File.separator + name);
                if (file.isDirectory()) {
                    return true;
                }
                return name.endsWith("DAT");
            });
            target = new File("D:/gz-5.gz");
            CompressUtil.gzip(target, new File("D:/viwer"), null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testUnGzip() {
        File target = new File("D:/zz/b");
        File gzFile = new File("D:/zz/gz-5.tar.gz");
//        File gzFile = new File("D:/zz/gz-1.Dat.gz");
        try {
            CompressUtil.unGzip(gzFile, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testZipHeader() {
        final byte[] data1 = new byte[] { 80, 75, 3, 4 };
        final byte[] data2 = new byte[] { 80, 75, 5, 6 };

        System.out.println(DataUtil.getInt(data1));
        System.out.println(StringUtil.byte2hex(data1));
        System.out.println(Integer.toHexString(DataUtil.getInt(data1, false)));

        System.out.println(DataUtil.getInt(data2));
        System.out.println(StringUtil.byte2hex(data2));
        System.out.println(Integer.toHexString(DataUtil.getInt(data2, false)));
    }
}
