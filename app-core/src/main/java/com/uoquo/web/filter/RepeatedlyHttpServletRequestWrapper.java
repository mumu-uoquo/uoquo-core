/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.zip.GZIPInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * 描述：请求流复用. <br>
 * 背景：当在拦截器中读取请求流，校验参数及记录日志后，在controller中还能继续使用. <br>
 * 原理：缓存请求流，每次getInputStream时，重新从缓存流中复制. <br>
 * 日期：2019-06-06 18:14 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2019-06-06     Administrator.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class RepeatedlyHttpServletRequestWrapper extends HttpServletRequestWrapper {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private ByteArrayOutputStream cachedBytes = null;

    /**
     * 构造方法.
     */
    public RepeatedlyHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(this.getInputStream()));
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        // 无请求流，返回空
        if (cachedBytes == null) {
            cacheInputStream();
        }
        // 【新增】缓存失败时返回空流，避免后续 NPE
        if (cachedBytes == null) {
            return new CachedServletInputStream(new byte[0]);
        }
        // 有请求流时，重新包装
        return new CachedServletInputStream();
    }
    
    /**
     * 缓存请求流.
     */
    private void cacheInputStream() throws IOException {
        /* Cache the inputstream in order to read it multiple times. For
         * convenience, I use apache.commons IOUtils
         */
        cachedBytes = new ByteArrayOutputStream();
        // 字节复制
        byte[] bytes = new byte[1024 * 10];
        int n;
        try {
            InputStream originalStream = super.getInputStream();
            while ((n = originalStream.read(bytes)) != -1) {
                cachedBytes.write(bytes, 0, n);
            }
        } catch (IOException e) {
            // 【关键修复】原始流不可读时，标记缓存为空并安全退出
            log.warn("读取原始请求流失败，请求体缓存不可用: {}", e.getMessage());
            cachedBytes = null;  // 置空，防止后续再次进入 cacheInputStream
            return;  // 直接返回，不执行后续逻辑
        }

        // gzip解压逻辑（仅在成功读取后执行）
        HttpServletRequest request = (HttpServletRequest) super.getRequest();
        String contentEncoding = request.getHeader("Content-Encoding");
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(cachedBytes.toByteArray()))) {
                bytes = gis.readAllBytes();
                cachedBytes.reset();
                cachedBytes.writeBytes(bytes);
            } catch (Exception e) {
                log.warn("请求体解压失败.", e);
            }
        }
    }


    /* An inputstream which reads the cached request body */
    public class CachedServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream input;

        public CachedServletInputStream() {
            /* create a new input stream from the cached request body */
            input = new ByteArrayInputStream(cachedBytes.toByteArray());
        }

        // 【新增】支持空缓存的构造方法
        public CachedServletInputStream(byte[] empty) {
            input = new ByteArrayInputStream(empty);
        }

        @Override
        public int read() throws IOException {
            return input.read();
        }

        @Override
        public boolean isFinished() {
            return false;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener arg0) {
            // do nothing
        }
    }

}