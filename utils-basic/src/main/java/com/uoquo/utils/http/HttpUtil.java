/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.http;

import com.uoquo.utils.Config;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.crypto.MD5;
import com.uoquo.utils.http.listener.EventLogTimeListener;
import com.uoquo.utils.http.listener.ProgressDownloadListener;
import com.uoquo.utils.http.listener.ProgressUploadListener;
import com.uoquo.utils.json.JsonUtil;

import java.io.*;

import java.net.FileNameMap;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.Util;
import okio.Buffer;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;

/**
 * 描述：HTTP请求处理. <br>
 * 日期：2018-01-29 10:04 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-29     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class HttpUtil {
    private static final Logger log = LoggerFactory.getLogger(HttpUtil.class);

    private static final int BUF_SIZE = 1024 * 1024;
    
    private static final MediaType MEDIA_JSON = MediaType.parse("application/json; charset=utf-8");

    private static volatile OkHttpClient clientPool = null;
    /* 
    // 枚举类实现单例
    public enum ClientPool {
        CLIENT;
        
        private OkHttpClient instance;
        
        ClientPool() {
            OkHttpClient.Builder builder = getClientBuilder();
            // 添加连接池（默认会创建maxIdle = 5，keepAlive = 5的连接池）
            int maxIdle   = Config.getInt("app.http.pool.maxIdle",   5);   // 最大空闲连接数（多余的会销毁），默认5个.
            int keepAlive = Config.getInt("app.http.pool.keepAlive", 300); // 连接存活时间（分钟），默认5分钟.
            if ((maxIdle > 0) && (keepAlive > 0)) {
                builder.connectionPool(new ConnectionPool(maxIdle, keepAlive, TimeUnit.SECONDS));
            }
            instance = builder.build();
        }
        
        public OkHttpClient getInstance() {
            return instance;
        }
    }
    */
    
    /**
     * 获取http连接池对象.<br>
     * 备注：主要用于普通get、post、json数据请求，上传、下载、及cookie传输需要每次新建对象
     */
    public static OkHttpClient getClientPool() {
        //return ClientPool.CLIENT.getInstance();
        // DCL生成单例
        if (clientPool == null) {
            OkHttpClient.Builder builder = getClientBuilder();
            // 添加连接池（默认会创建maxIdle = 5，keepAlive = 5的连接池）
            int maxIdle   = Config.getInt("app.http.pool.max-idle",   5); // 最大空闲连接数（多余的会销毁），默认5个.
            int keepAlive = Config.getInt("app.http.pool.keep-alive", 300); // 连接存活时间（分钟），默认5分钟.
            if ((maxIdle > 0) && (keepAlive > 0)) {
                builder.connectionPool(new ConnectionPool(maxIdle, keepAlive, TimeUnit.SECONDS));
            }
            
            synchronized (HttpUtil.class) {
                if (clientPool == null) {
                    clientPool = builder.build();
                }
            }
        }
        return clientPool;
    }
    
    /**
     * 获取新的http对象.<br>
     * 备注：主要用于上传、下载、及有cookie的连接
     * @param cookies  自定义cookie
     */
    public static OkHttpClient getClientOnce(HttpCookies cookies) {
        OkHttpClient.Builder builder = getClientBuilder();
        builder.connectionPool(new ConnectionPool(1, 1, TimeUnit.MINUTES)); // 不设置的话，默认会创建maxIdle = 5，keepAlive = 5的连接池
        // 添加自定义cookie
        if ((cookies != null) && cookies.exist()) {
            builder.cookieJar(new CookieJar() {
                @Override 
                public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
                    // do nothing
                }

                @NonNull
                @Override 
                public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
                    List<Cookie> list = new ArrayList<Cookie>();
                    try {
                        for (String key : cookies.get().keySet()) {
                            String val = cookies.get(key);
                            Cookie cookie = new Cookie.Builder()
                                    .hostOnlyDomain(url.host())
                                    .name(key).value(val)
                                    .build();
                            list.add(cookie);
                        }
                    } catch (Exception e) {
                        // do nothing
                    }
                    return list;
                }
            });
        }
        return builder.build();
    }
    
    /**
     * client builder.
     */
    public static OkHttpClient.Builder getClientBuilder() {
        // LOG
        //HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        //loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        int connectTimeout = Config.getInt("app.http.pool.timeout.connect", 2);  // 建立连接超时时间（秒），默认2秒.
        int readTimeout    = Config.getInt("app.http.pool.timeout.read",    10); // 数据传输超时时间（秒），默认4秒.
        int writeTimeout   = Config.getInt("app.http.pool.timeout.write",   10); // 请求响应超时时间（秒），默认5秒.
        // 基本设置（负数表示不限制，但okhttp不支持负数，因此将其转换为一个比较大的数值）
        connectTimeout = (connectTimeout <= 0) ? 10 : connectTimeout;
        readTimeout    = (readTimeout    <= 0) ? 50 : readTimeout;
        writeTimeout   = (writeTimeout   <= 0) ? 50 : writeTimeout;
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout,       TimeUnit.SECONDS)
                .writeTimeout(writeTimeout,     TimeUnit.SECONDS)
                .eventListenerFactory(EventLogTimeListener.FACTORY) // 请求耗时记录
                .dns(new UoquoDns())            // 自定义DNS解析
                .retryOnConnectionFailure(true)  // 连接建立失败，是否重试？该值需要好好观察，看看是否需要重试
                //.addInterceptor(loggingInterceptor) // 加入Log拦截器
                ;
        boolean trustAll = Config.getBoolean("app.http.trustAll", false);
        if (!trustAll) {
            return builder;
        }

        // 添加SSL处理（信任所有）
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager trustManager)) {
                throw new IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers));
            }

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { trustManager }, null);
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            
            // 添加所有信任至builder对象
            builder.sslSocketFactory(sslSocketFactory, trustManager);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    // 信任所有
                    return true;
                }
            });
        } catch (Exception e) {
            // do nothing
        }
        return builder;
    }
    
    
    /**
     * GET请求.
     * @param url   地址
     * @param parms 参数
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String get(String url, HttpParams parms) throws Exception {
        return get(url, parms, null, null);
    }
    
    /**
     * GET请求.
     * @param url     地址
     * @param parms   参数
     * @param headers 自定义请求头
     * @param cookies 自定义cookie
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String get(String url, HttpParams parms, HttpHeaders headers, HttpCookies cookies) throws Exception {
        // 设置请求数据
        if ((parms != null) && parms.existFormParam()) {
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(url);
            if (url.indexOf("?") > 0) {
                urlBuilder.append("&");
            } else {
                urlBuilder.append("?");
            }
            urlBuilder.append(parms.getURLEncodedParams());
            url = urlBuilder.toString();
        }
        // 请求内容
        Request.Builder request = new Request.Builder();
        request.get().url(url);
        // 执行请求
        OkHttpClient client = (cookies == null) ? getClientPool() : getClientOnce(cookies);
        Response response = execute(client, request, headers, null);
        return parseResponse2String(response);
    }
    
    /**
     * POST请求.
     * @param url   地址
     * @param parms 参数
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String post(String url, HttpParams parms) throws Exception {
        return post(url, parms, null, null);
    }
    
    /**
     * POST请求.
     * @param url     地址
     * @param parms   参数
     * @param headers 自定义请求头
     * @param cookies 自定义cookie
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String post(String url, HttpParams parms, HttpHeaders headers, HttpCookies cookies) throws Exception {
        Request.Builder request = new Request.Builder();
        // 设置请求数据
        if ((parms != null) && parms.existFormParam()) {
            FormBody.Builder formBody = new FormBody.Builder();
            Map<String, String> params = parms.getFormParams();
            for (String key : params.keySet()) {
                formBody.add(key, params.get(key));
            }
            request.post(formBody.build());
        } else {
            // 空请求参数
            request.post(Util.EMPTY_REQUEST);
        }
        request.url(url);
        // 执行请求
        OkHttpClient client = (cookies == null) ? getClientPool() : getClientOnce(cookies);
        Response response = execute(client, request, headers, null);
        return parseResponse2String(response);
    }
    
    /**
     * JSON数据请求（POST）.<br>
     * 将parms转换为json消息体传入
     * @param url   地址
     * @param parms 参数
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String json(String url, HttpParams parms) throws Exception {
        return json(url, null, parms, null, null);
    }
    
    /**
     * JSON数据请求（POST）.<br>
     * 将parms转换为json消息体传入
     * @param url     地址
     * @param parms   参数
     * @param headers 自定义请求头
     * @param cookies 自定义cookie
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String json(String url, HttpParams parms, HttpHeaders headers, HttpCookies cookies) throws Exception {
        return json(url, null, parms, headers, cookies);
    }
    
    /**
     * JSON数据请求（POST）.<br>
     * 将parms转换为json消息体传入
     * @param url   地址
     * @param parms URL参数
     * @param json  消息体参数
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String json(String url, HttpParams parms, Object json) throws Exception {
        return json(url, parms, json, null, null);
    }
    
    /**
     * JSON数据请求（POST）.<br>
     * 将parms转换为json消息体传入
     * @param url     地址
     * @param parms   URL参数
     * @param json    消息体参数
     * @param headers 自定义请求头
     * @param cookies 自定义cookie
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String json(String url, HttpParams parms, Object json, HttpHeaders headers, HttpCookies cookies) throws Exception {
        // 设置请求数据
        if ((parms != null) && parms.existFormParam()) {
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(url);
            if (url.indexOf("?") > 0) {
                urlBuilder.append("&");
            } else {
                urlBuilder.append("?");
            }
            urlBuilder.append(parms.getURLEncodedParams());
            url = urlBuilder.toString();
        }
        // 设置请求数据
        String jsonStr = "";
        if (json != null) {
            if (json instanceof HttpParams temp) {
                if (temp.existFormParam()) {
                    jsonStr = JsonUtil.serialize(temp.getJsonParams());
                }
            } else if (json instanceof String) {
                jsonStr = (String)json;
            } else {
                jsonStr = JsonUtil.serialize(json);
            }
        }
        RequestBody body;
        if (StringUtil.notNull(jsonStr)) {
            try {
                body = createJsonBody(jsonStr.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                body = createJsonBody(jsonStr.getBytes());
            }
        } else {
            body = createJsonBody(Util.EMPTY_BYTE_ARRAY);
        }
        
        Request.Builder request = new Request.Builder();
        request.post(body).url(url);
        
        // 执行请求
        OkHttpClient client = (cookies == null) ? getClientPool() : getClientOnce(cookies);
        Response response = execute(client, request, headers, null);
        return parseResponse2String(response);
    }
    
    /**
     * 构建JSON请求体.<br>
     * 备注：重写toString，便于日志输出
     */
    private static RequestBody createJsonBody(final byte[] content) {
        return new RequestBody() {
            @Override public MediaType contentType() {
                return MEDIA_JSON;
            }

            @Override public long contentLength() {
                return content.length;
            }

            @Override public void writeTo(@NonNull BufferedSink sink) throws IOException {
                sink.write(content, 0, content.length);
            }
            
            @Override public String toString() {
                return new String(content, StandardCharsets.UTF_8);
            }
        };
    }
    
    /**
     * 文件上传. <br>
     * @param url   地址
     * @param parms 参数
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String upload(String url, HttpParams parms) throws Exception {
        return upload(url, parms, null, null, null);
    }
    
    /**
     * 文件上传. <br>
     * @param url      地址
     * @param parms    参数
     * @param progress 上传进度
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String upload(String url, HttpParams parms, ProgressUploadListener progress) throws Exception {
        return upload(url, parms, null, null, progress);
    }
    
    /**
     * 文件上传. <br>
     * @param url     地址
     * @param parms   参数
     * @param headers 自定义请求头
     * @param cookies 自定义cookie
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String upload(String url, HttpParams parms, HttpHeaders headers, HttpCookies cookies) throws Exception {
        return upload(url, parms, headers, cookies, null);
    }
    
    /**
     * 文件上传. <br>
     * @param url     地址
     * @param parms   参数
     * @param headers 自定义请求头
     * @param cookies 自定义cookie
     * @param progress 上传进度
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String upload(String url, HttpParams parms, HttpHeaders headers, HttpCookies cookies, final ProgressUploadListener progress) throws Exception {
        Request.Builder request = new Request.Builder();
        boolean flag = true;
        if (parms != null) {
            MultipartBody.Builder body = new MultipartBody.Builder().setType(MultipartBody.FORM);
            // 拼装文件
            Map<String, List<File>> fileParam = parms.getFileParams();
            for (String key : fileParam.keySet()) {
                flag = false;
                List<File> list = fileParam.get(key);
                for (final File file : list) {
                    final String fileName = file.getName();
                    body.addFormDataPart(key, fileName, new RequestBody() {
                        @Override public MediaType contentType() {
                            return MediaType.parse(guessMimeType(fileName));
                        }
                        
                        @Override public long contentLength() {
                            return file.length();
                        }
                        
                        @Override public void writeTo(@NonNull BufferedSink sink) throws IOException {
                            Source source = null;
                            try {
                                source = Okio.source(file);
                                /* 容易内存泄漏，因此也是读取一段发送一段
                                // 没有回调时，全部放入
                                if (progress == null) {
                                    sink.writeAll(source);
                                    return;
                                }
                                */
                                // 有回调时，按字节读入
                                Buffer buf = new Buffer();
                                long total = contentLength(); // 文件总长度
                                long write = 0;               // 已上传长度
                                for (long ch; (ch = source.read(buf, BUF_SIZE)) != -1; ) { // 每次读取1M的内容
                                    sink.write(buf, ch);
                                    sink.flush();
                                    // 进度回调
                                    write += ch;
                                    if (progress != null) {
                                        progress.onProgress(fileName, total, write, ch);
                                    }
                                }
                                // 成功回调
                                if (progress != null) {
                                    progress.onSuccess(fileName, total);
                                }
                            } catch (Exception e) {
                                // 失败回调
                                if (progress != null) {
                                    progress.onFailed(fileName, e);
                                } else {
                                    throw new IOException(e);
                                }
                            } finally {
                                if (source != null) {
                                    Util.closeQuietly(source);
                                }
                            }
                        }
                    });
                    /*
                    body.addPart(Headers.of("Content-Disposition", "form-data; name=\"" + key + "\"; filename=\"" + fileName + "\""),
                            RequestBody.create(MediaType.parse(guessMimeType(fileName)), file));
                    */
                }
            }
            // 拼装其他参数
            Map<String, String> formParam = parms.getFormParams();
            for (String key : formParam.keySet()) {
                flag = false;
                body.addFormDataPart(key, formParam.get(key));
                /*
                body.addPart(Headers.of("Content-Disposition", "form-data; name=\"" + key + "\""),
                        RequestBody.create(null, formParam.get(key)));
                */
            }
            request.post(body.build());
        }
        // 没有内容时，添加空参数体
        if (flag) {
            request.post(Util.EMPTY_REQUEST);
        }
        request.url(url);
        // 执行请求（暂时不复用client）
        //OkHttpClient client = (cookies == null) ? getClientPool() : getClientOnce(cookies);
        OkHttpClient client = getClientOnce(cookies);
        Response response = execute(client, request, headers, null);
        return parseResponse2String(response);
    }
    
    /**
     * 字节流上传. <br>
     * 备注：后端用request.getInputStream()获取流，需要传输的参数封装到headers中<br>
     * @param url     地址
     * @param data    需要上传的字节数组
     * @param headers 自定义请求头
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String upload(String url, byte[] data, HttpHeaders headers) throws Exception {
        ByteArrayInputStream in = null;
        if ((data != null) && (data.length > 0)) {
            in = new ByteArrayInputStream(data);
        }
        return upload(url, in, headers, null, null);
    }
    
    /**
     * 字节流上传. <br>
     * 备注：后端用request.getInputStream()获取流，需要传输的参数封装到headers中<br>
     * @param url     地址
     * @param in      需要上传的输入流
     * @param headers 自定义请求头
     * @param cookies 自定义cookie
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String upload(String url, InputStream in, HttpHeaders headers, HttpCookies cookies, final ProgressUploadListener progress) throws Exception {
        Request.Builder request = new Request.Builder();
        if ((in == null) || (in.available() == 0)) {
            request.post(Util.EMPTY_REQUEST); // 没有内容时，添加空参数体
        } else {
            int total = in.available(); // 文件总长度
            RequestBody body = new RequestBody() {
                @Override public MediaType contentType() {
                    return MediaType.parse("application/octet-stream");
                }
                
                @Override public long contentLength() {
                    return total;
                }
                
                @Override public void writeTo(@NonNull BufferedSink sink) throws IOException {
                    try {
                        byte[] buf = new byte[BUF_SIZE]; // 一次读取1M
                        long write = 0; // 已上传长度
                        for (int ch; (ch = in.read(buf)) > 0; ) {
                            sink.write(buf, 0, ch);
                            sink.flush();
                            // 进度回调
                            write += ch;
                            if (progress != null) {
                                progress.onProgress(null, total, write, ch);
                            }
                        }
                        // 成功回调
                        if (progress != null) {
                            progress.onSuccess(null, total);
                        }
                    } catch (Exception e) {
                        // 失败回调
                        if (progress != null) {
                            progress.onFailed(null, e);
                        } else {
                            throw new IOException(e);
                        }
                    }
                }
            };
            request.post(body);
        }
        request.url(url);
        // 执行请求（暂时不复用client）
        //OkHttpClient client = (cookies == null) ? getClientPool() : getClientOnce(cookies);
        OkHttpClient client = getClientOnce(cookies);
        Response response = execute(client, request, headers, null);
        return parseResponse2String(response);
    }

    /**
     * 文件断点上传. <br>
     * @param url   地址
     * @param parms 参数
     * @param file    待上传文件
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String uploadByRange(String url, HttpParams parms, File file) throws Exception {
        return uploadByRange(url, parms, file, null, null, null);
    }

    /**
     * 文件断点上传. <br>
     * @param url      地址
     * @param parms    参数
     * @param file    待上传文件
     * @param progress 上传进度
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String uploadByRange(String url, HttpParams parms, File file, ProgressUploadListener progress) throws Exception {
        return uploadByRange(url, parms, file, null, null, progress);
    }

    /**
     * 文件断点上传. <br>
     * 单线程模式，文件分割为5M大小的块进行上传，最大总上传次数是分块数的两倍（即上传失败的块会重发，防止网络抖动导致的上传失败）
     * <ul>
     *     <li>请求头
     *         <ul>
     *             <li>file-md5   ：文件MD5，用于校验最终上传的文件准确性</li>
     *             <li>file-length：文件总长度</li>
     *             <li>part-length：分块的块长度</li>
     *             <li>part-index ：当前上传数据的分块序号</li>
     *         </ul>
     *     </li>
     *     <li>响应头
     *         <ul>
     *             <li>part-unfinish：逗号分割的未完成块序号（为空时表示上传完成）</li>
     *         </ul>
     *     </li>
     * </ul>
     * @param url     地址
     * @param parms   参数（忽略文件参数）
     * @param file    待上传文件
     * @param headers 自定义请求头
     * @param cookies 自定义cookie
     * @param progress 上传进度
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    public static String uploadByRange(String url, HttpParams parms, File file, HttpHeaders headers, HttpCookies cookies, final ProgressUploadListener progress) throws Exception {
        // 1. 参数校验
        // 1.1 文件有效性判断
        if (file == null) {
            throw new IllegalArgumentException("文件参数必须传入");
        }
        if (!file.exists()) {
            throw new FileNotFoundException(String.format("文件[%s]不存在", file.getAbsolutePath()));
        }
        if (!file.isFile()) {
            throw new FileNotFoundException(String.format("[%s]不是一个有效的文件", file.getAbsolutePath()));
        }
        if (!file.canRead()) {
            throw new IllegalArgumentException(String.format("[%s]无法读取", file.getAbsolutePath()));
        }
        if (file.length() == 0) {
            throw new IllegalArgumentException(String.format("[%s]为空文件，不可以采用分片上传", file.getAbsolutePath()));
        }
        // 1.2 参数文件不需要
        if ((parms != null) && !parms.getFileParams().isEmpty()) {
            throw new IllegalArgumentException("分段上传不支持HttpParams中的file类型参数");
        }

        // 2. 分块上传
        long fileLen = file.length();
        int  partLen = BUF_SIZE * 5; // 分块长度（每次上传5M）
        int  partIdx = 0; // 正在上传的块序号
        int  partNum = 0; // 已上传的块数量
        int  sendNum = 0; // 已上传多少次，用来控制跳出循环，防止死循环（理论上不应该超过partTotal）
        int  partTotal = (int)Math.ceil((double)fileLen / partLen); // 总分块个数
        // 请求头信息（添加分块信息）
        if (headers == null) {
            headers = new HttpHeaders();
        }
        String fileMd5 = MD5.encryptFile(file.getAbsolutePath(), null);
        headers.add("file-md5",    fileMd5);
        headers.add("file-length", fileLen);
        headers.add("part-length", partLen);
        OkHttpClient client = getClientOnce(cookies);
        // 按块上传
        while (partIdx >= 0) {
            sendNum++; // 发送次数
            // 2.1 按块读取文
            long len = fileLen - (long) partIdx * partLen;
            if (len > partLen) {
                len = partLen;
            }
            byte[] data = new byte[(int)len];
            try (
                RandomAccessFile raf = new RandomAccessFile(file, "r");
            ) {
                raf.seek((long) partIdx * partLen);
                raf.read(data);
            }
            // 2.2 上传当前块
            headers.remove("part-index");
            headers.add("part-index", partIdx);
            try {
                Response response = uploadByRange(url, parms, data, file.getName(), fileLen, (long) partNum * partLen, client, headers, progress);
                if (response.code() == 200) {
                    // 成功响应：获取下一个未上传分块，如果上传完，则返回结果
                    String unfinish = response.header("part-unfinish"); // 未完成的分块序号
                    // 所有块都上传完毕，则返回响应结果
                    if (StringUtil.isNull(unfinish)) {
                        partIdx = -1;
                        return parseResponse2String(response);
                    }
                    // 未上传完毕，则获取一个未完成的分块号
                    String[] ps = unfinish.split(",");
                    partIdx = Integer.parseInt(ps[0]);
                    partNum = partTotal - ps.length; // 成功上传的块数量
                } else {
                    // 失败响应：继续重新上传当前块（如果超过指定次数，则认为上传不成功）
                    if (sendNum > partTotal * 2) {
                        return parseResponse2String(response);
                    }
                }
            } catch (Exception e) {
                // 如果出错，继续发送当前包（如果超过指定次数，则认为上传不成功）
                if (sendNum > partTotal * 2) {
                    throw e;
                }
                // 随机休眠100ms以内，避开之前因为网络抖动发送不成功的情况
                try {
                    Thread.sleep(new Random().nextInt(100));
                } catch (Exception e2) {
                    // do nothing;
                }
            }
        }
        // 理论上不会进入该代码
        throw new RuntimeException("分块上传失败");
    }

    /**
     * 上传指定块的数据. <br>
     * @param url     地址
     * @param parms   参数（忽略文件参数）
     * @param data    待上传的数据
     * @param fileName    文件名称
     * @param finishSize  已上传数据大小
     * @param client  client对象
     * @param headers 自定义请求头
     * @param progress 上传进度
     * @return String 响应消息
     * @throws Exception 错误信息
     */
    private static Response uploadByRange(String url, HttpParams parms, byte[] data, String fileName, long total, long finishSize,
          OkHttpClient client, HttpHeaders headers, final ProgressUploadListener progress) throws Exception {
        // 1. 拼装其他参数
        MultipartBody.Builder body = new MultipartBody.Builder().setType(MultipartBody.FORM);
        if (parms != null) {
            Map<String, String> formParam = parms.getFormParams();
            for (String key : formParam.keySet()) {
                body.addFormDataPart(key, formParam.get(key));
            }
        }
        // 2. 拼接文件到请求体
        ByteArrayInputStream din = new ByteArrayInputStream(data);
        body.addFormDataPart("file", fileName, new RequestBody() {
            @Override public MediaType contentType() {
                return MediaType.parse(guessMimeType(fileName));
            }

            @Override public long contentLength() {
                return data.length;
            }

            @Override public void writeTo(@NonNull BufferedSink sink) throws IOException {
                try {
                    byte[] buf = new byte[BUF_SIZE]; // 一次读取1M
                    long write = finishSize; // 已上传长度
                    for (int ch; (ch = din.read(buf)) > 0; ) {
                        sink.write(buf, 0, ch);
                        sink.flush();
                        // 进度回调
                        write += ch;
                        if (progress != null) {
                            progress.onProgress(fileName, total, write, ch);
                        }
                    }
                    // 成功回调
                    if ((progress != null) && (total == write)) {
                        progress.onSuccess(fileName, total);
                    }
                } catch (Exception e) {
                    // 失败回调
                    if (progress != null) {
                        progress.onFailed(fileName, e);
                    } else {
                        throw new IOException(e);
                    }
                }
            }
        });
        // 3. 发起请求
        Request.Builder request = new Request.Builder();
        request.post(body.build());
        request.url(url);
        return execute(client, request, headers, null);
    }

    /**
     * 文件下载（保存到内存，适用于小文件）.
     * @param url   地址
     * @param parms 参数
     * @throws Exception 错误信息
     */
    public static byte[] download(String url, HttpParams parms) throws Exception {
        return download(url, parms, null, null, null);
    }
    
    /**
     * 文件下载（保存到内存，适用于小文件）.
     * @param url   地址
     * @param parms 参数
     * @param progress 下载进度
     * @throws Exception 错误信息
     */
    public static byte[] download(String url, HttpParams parms, ProgressDownloadListener progress) throws Exception {
        return download(url, parms, null, null, progress);
    }
    
    /**
     * 文件下载（保存到内存，适用于小文件）.
     * @param url   地址
     * @param parms 参数
     * @param headers  自定义请求头
     * @param cookies  自定义cookie
     * @param progress 下载进度
     * @throws Exception 错误信息
     */
    public static byte[] download(String url, HttpParams parms, HttpHeaders headers, HttpCookies cookies, ProgressDownloadListener progress) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            download(url, parms, headers, cookies, progress, out);
            return out.toByteArray();
        } finally {
            out.close();
        }
    }
    
    /**
     * 文件下载（保存到本地，适用于大文件）.
     * @param url   地址
     * @param parms 参数
     * @param filePath 文件下载后的保存地址
     * @throws Exception 错误信息
     */
    public static void download(String url, HttpParams parms, String filePath) throws Exception {
        download(url, parms, null, null, null, filePath);
    }
    
    /**
     * 文件下载（保存到本地，适用于大文件）.
     * @param url   地址
     * @param parms 参数
     * @param progress 下载进度
     * @param filePath 文件下载后的保存地址
     * @throws Exception 错误信息
     */
    public static void download(String url, HttpParams parms, ProgressDownloadListener progress, String filePath) throws Exception {
        download(url, parms, null, null, progress, filePath);
    }
    
    /**
     * 文件下载（保存到本地，适用于大文件）.
     * @param url   地址
     * @param parms 参数
     * @param headers  自定义请求头
     * @param cookies  自定义cookie
     * @param progress 下载进度
     * @param filePath 文件下载后的保存地址
     * @throws Exception 错误信息
     */
    public static void download(String url, HttpParams parms, HttpHeaders headers, HttpCookies cookies, ProgressDownloadListener progress, String filePath) throws Exception {
        // 创建对应的文件夹
        /*
        int idx = filePath.lastIndexOf("/");
        idx = (idx > 0) ? idx : filePath.lastIndexOf("\\");
        if (idx == -1) {
            throw new Exception("文件[" + filePath + "]路径格式不正确.");
        }
        String path = filePath.substring(0, idx);
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }
        */
        // 判断要下载的文件是否存在
        File file = new File(filePath);
        if (file.exists()) {
            if (file.isFile()) {
                // 如果是已经存在的文件，则返回错误
                throw new Exception("文件[" + filePath + "]已经存在，请重新指定下载保存地址.");
            } else {
                // 如果是已经存在的文件夹，则拼接URL中的文件名
                filePath = file.getAbsolutePath() + File.separator + url.substring(url.lastIndexOf("/"));
            }
        } else if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs(); // 父目录不存在时，创建父级目录
        }
        // 临时文件
        File cache = new File(filePath + ".temp");
        if (cache.exists()) {
            cache.delete();
        }
        cache.createNewFile();
        FileOutputStream out = new FileOutputStream(cache);
        try {
            download(url, parms, headers, cookies, progress, out);
            out.close();
            cache.renameTo(file); // 移动文件，必须先关闭cache的文件流
        } catch (Exception e) {
            try {
                out.close();
                cache.delete(); // 删除临时文件
            } catch (Exception e2) {
                // do nothing
            }
            throw e;
        }
    }
    
    /**
     * 文件下载（默认GET）.
     * @param url   地址
     * @param parms 参数
     * @param headers  自定义请求头
     * @param cookies  自定义cookie
     * @param progress 下载进度
     * @param out      下载文件的输出流
     * @throws Exception 错误信息
     */
    public static void download(String url, HttpParams parms, HttpHeaders headers, HttpCookies cookies, ProgressDownloadListener progress, OutputStream out) throws Exception {
        // 设置请求数据
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(url);
        if ((parms != null) && parms.existFormParam()) {
            if (url.indexOf("?") > 0) {
                urlBuilder.append("&");
            } else {
                urlBuilder.append("?");
            }
            urlBuilder.append(parms.getURLEncodedParams());
        }
        // 请求内容
        Request.Builder request = new Request.Builder();
        request.get().url(urlBuilder.toString());
        try {
            download(request, headers, cookies, progress, out);
        } catch (Exception e) {
            // 如果是405异常，说明服务器不允许该请求模式(get)，此时需要重新请求（post）
            if ("405".equals(e.getMessage())) {
                downloadPost(url, parms, headers, cookies, progress, out);
                return;
            }
            // 失败回调
            if (progress != null) {
                progress.onFailed(e);
            } else {
                throw e;
            }
        }
    }
    
    /**
     * 文件下载.
     * @param request  请求对象
     * @param headers  自定义请求头
     * @param cookies  自定义cookie
     * @param progress 下载进度
     * @param out      下载文件的输出流
     * @throws Exception 错误信息
     */
    private static void download(Request.Builder request, HttpHeaders headers, HttpCookies cookies, ProgressDownloadListener progress, OutputStream out) throws Exception {
        // 执行下载
        InputStream in = null;
        Response response = null;
        try {
            // 返回结果如果不是200，则说明有错误
            // 执行请求（暂时不复用client）
            //OkHttpClient client = (cookies == null) ? getClientPool() : getClientOnce(cookies);
            OkHttpClient client = getClientOnce(cookies);
            response = execute(client, request, headers, null);
            if ((response == null) || (response.code() != 200)) {
                throw new Exception(response == null ? "" : String.valueOf(response.code()));
            }
            // 只针对返回200的数据进行下载处理
            ResponseBody body = response.body();
            long total = body.contentLength(); // 文件总长度
            long write = 0;                    // 已下载长度
            in = body.byteStream();
            byte[] buf = new byte[BUF_SIZE];
            int ch = -1;
            while ((ch = in.read(buf)) != -1) {
                out.write(buf, 0, ch);
                // 进度回调
                if (progress != null) {
                    write += ch;
                    progress.onProgress(total, write, ch);
                }
            }
            out.flush();
            // 成功回调
            if (progress != null) {
                progress.onSuccess(total);
            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (response != null) {
                response.close();
            }
        }
    }
    
    /**
     * 文件下载（Post方式下载）.
     * @param url   地址
     * @param parms 参数
     * @param headers  自定义请求头
     * @param cookies  自定义cookie
     * @param progress 下载进度
     * @param out      下载文件的输出流
     * @throws Exception 错误信息
     */
    public static void downloadPost(String url, HttpParams parms, HttpHeaders headers, HttpCookies cookies, ProgressDownloadListener progress, OutputStream out) throws Exception {
        Request.Builder request = new Request.Builder();
        // 设置请求数据
        if ((parms != null) && parms.existFormParam()) {
            FormBody.Builder formBody = new FormBody.Builder();
            Map<String, String> params = parms.getFormParams();
            for (String key : params.keySet()) {
                formBody.add(key, params.get(key));
            }
            request.post(formBody.build());
        } else {
            // 空请求参数
            request.post(Util.EMPTY_REQUEST);
        }
        request.url(url);
        try {
            download(request, headers, cookies, progress, out);
        } catch (Exception e) {
            // 失败回调
            if (progress != null) {
                progress.onFailed(e);
            } else {
                throw e;
            }
        }
    }
    
    /**
     * 执行请求.
     * @param client   http对象
     * @param request  请求对象
     * @param headers  自定义请求头
     * @param callback 请求回调（异步执行时使用）
     * @return Response  响应对象
     * @throws Exception 错误信息
     */
    private static Response execute(OkHttpClient client, Request.Builder request, HttpHeaders headers, @Nullable Callback callback) throws Exception {
        // 添加自定义请求头信息
        if ((headers != null) && headers.exist()) {
            try {
                for (String key : headers.get().keySet()) {
                    String val = headers.get(key);
                    request.addHeader(key, val);
                }
            } catch (Exception e) {
                // do nothing
            }
        }
        
        // 执行请
        Request req = request.build();
        try {
            Call call = client.newCall(req);
            if (callback != null) {
                call.enqueue(callback);
                return null;
            } else {
                return call.execute();
            }
        } catch (Exception e) {
            if (log.isWarnEnabled()) {
                Map<String, List<String>> headerMap = req.headers().toMultimap();
                String uri = req.url().toString();
                if (uri.length() > 500) {
                    uri = uri.substring(0, 500) + "...";
                }
                RequestBody body = req.body();
                if (body == null) {
                    log.warn("request error. request={}, method={}, header={}, tag={}", uri, req.method(), headerMap, req.tag(), e);
                } else if (body instanceof FormBody temp) {
                    List<Map<String, String>> params = new ArrayList<>();
                    for (int i = 0; i < temp.size(); i++) {
                        Map<String, String> map = new HashMap<>();
                        String val = temp.value(i);
                        if (val.length() > 300) {
                            val = val.substring(0, 300) + "...";
                        }
                        map.put(temp.name(i), val);
                        params.add(map);
                    }
                    log.warn("request post error. request={}, method={}, header={}, tag={}, params={}", uri, req.method(), headerMap, req.tag(), params, e);
                } else if (Objects.equals(body.contentType(), MEDIA_JSON)) {
                    log.warn("request json error. request={}, method={}, header={}, tag={}, body={}", uri, req.method(), headerMap, req.tag(), req.body().toString(), e);
                } else {
                    log.warn("request error. request={}, method={}, header={}, tag={}", uri, req.method(), headerMap, req.tag(), e);
                }
            }
            throw e;
        } finally {
            // 销毁请求对象
//            client.dispatcher().executorService().shutdown();
//            client.connectionPool().evictAll();
        }
    }

    /**
     * 转换响应内容为String字符串 . <br>
     * @param response 响应内容
     */
    private static String parseResponse2String(Response response) throws Exception {
        if (response == null) {
            return null;
        }
        try (response) {
            ResponseBody body = response.body();
            if (body != null) {
                String temp = body.string();
                if (StringUtil.notNull(temp)) {
                    return temp;
                } else if (response.code() == 200) {
                    return null;
                }
            }
            throw new Exception(String.valueOf(response.code()));
        }
    }
    
    /**
     * 获取上传文件的类型. <br>
     * @param path 文件名称
     * @return 文件类型
     */
    private static String guessMimeType(String path) {
        FileNameMap fileNameMap = URLConnection.getFileNameMap();
        String contentTypeFor = fileNameMap.getContentTypeFor(path);
        if (contentTypeFor == null) {
            contentTypeFor = "application/octet-stream";
        }
        return contentTypeFor;
    }
}
