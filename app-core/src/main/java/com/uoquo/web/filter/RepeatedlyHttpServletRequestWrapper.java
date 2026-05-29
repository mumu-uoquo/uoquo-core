/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
    public ServletInputStream getInputStream() throws IOException {
        // 无请求流，返回空
        if (cachedBytes == null) {
            cacheInputStream();
        }
        // 有请求流时，重新包装
        return new CachedServletInputStream();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(this.getInputStream()));
    }
    
    /**
     * 缓存请求流.
     */
    private void cacheInputStream() throws IOException {
        cachedBytes = new ByteArrayOutputStream();
        // 字节复制
        byte[] bytes = new byte[1024 * 10];
        int n;
        while ((n = super.getInputStream().read(bytes)) != -1) {
            cachedBytes.write(bytes, 0, n);
        }
        // 如果是gzip压缩传输的数据，先解压再缓存
        HttpServletRequest request = (HttpServletRequest)super.getRequest();
        String contentEncoding = request.getHeader("Content-Encoding");
        if ((contentEncoding != null) && contentEncoding.contains("gzip")) {
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(cachedBytes.toByteArray()))) {
                bytes = gis.readAllBytes();
                cachedBytes.reset();
                cachedBytes.writeBytes(bytes);
            } catch (Exception e) {
                log.warn("解压请求参数失败.", e);
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