/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.http;

import com.uoquo.utils.Config;

import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import javax.net.ssl.*;

import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;

/**
 * HTTP 客户端构建器，提供统一的客户端配置管理
 *
 * <p>将 HttpUtil 中的客户端配置逻辑提取出来，提供更灵活的配置方式</p>
 *
 * @author uoquo team
 */
public class HttpClientBuilder {
    
    /**
     * 默认连接超时（秒）
     */
    public static final int DEFAULT_CONNECT_TIMEOUT = 2;
    
    /**
     * 默认读取超时（秒）
     */
    public static final int DEFAULT_READ_TIMEOUT = 10;
    
    /**
     * 默认写入超时（秒）
     */
    public static final int DEFAULT_WRITE_TIMEOUT = 10;
    
    /**
     * 默认最大空闲连接数
     */
    public static final int DEFAULT_MAX_IDLE_CONNECTIONS = 5;
    
    /**
     * 默认连接保活时间（秒）
     */
    public static final int DEFAULT_KEEP_ALIVE_DURATION = 300;
    
    /**
     * 构建通用 HTTP 客户端
     */
    public static OkHttpClient build() {
        return build(null, null);
    }
    
    /**
     * 构建带 Cookie 支持的 HTTP 客户端
     */
    public static OkHttpClient buildWithCookies(HttpCookies cookies) {
        return build(null, cookies);
    }
    
    /**
     * 构建自定义 HTTP 客户端
     * 
     * @param dns 自定义 DNS 解析器（null 时使用默认）
     * @param cookies 自定义 Cookie（null 时不启用 Cookie）
     */
    public static OkHttpClient build(Dns dns, HttpCookies cookies) {
        OkHttpClient.Builder builder = createBaseBuilder();
        
        // 设置 DNS
        if (dns != null) {
            builder.dns(dns);
        } else {
            builder.dns(new UoquoDns());
        }
        
        // 设置 Cookie
        if (cookies != null && cookies.exist()) {
            builder.cookieJar(createCookieJar(cookies));
        }
        
        // 设置连接池（单独的客户端使用较小的连接池）
        builder.connectionPool(new ConnectionPool(1, 1, TimeUnit.MINUTES));
        
        return builder.build();
    }
    
    /**
     * 构建连接池 HTTP 客户端（用于高频请求）
     */
    public static OkHttpClient buildPooled() {
        OkHttpClient.Builder builder = createBaseBuilder();
        
        // 设置 DNS
        builder.dns(new UoquoDns());
        
        // 设置连接池
        int maxIdle = Config.getInt("app.http.pool.max-idle", DEFAULT_MAX_IDLE_CONNECTIONS);
        int keepAlive = Config.getInt("app.http.pool.keep-alive", DEFAULT_KEEP_ALIVE_DURATION);
        if (maxIdle > 0 && keepAlive > 0) {
            builder.connectionPool(new ConnectionPool(maxIdle, keepAlive, TimeUnit.SECONDS));
        }
        
        return builder.build();
    }
    
    /**
     * 创建基础构建器（包含超时设置、DNS、事件监听器和 SSL 配置）
     */
    private static OkHttpClient.Builder createBaseBuilder() {
        // 读取配置
        int connectTimeout = Config.getInt("app.http.pool.timeout.connect", DEFAULT_CONNECT_TIMEOUT);
        int readTimeout = Config.getInt("app.http.pool.timeout.read", DEFAULT_READ_TIMEOUT);
        int writeTimeout = Config.getInt("app.http.pool.timeout.write", DEFAULT_WRITE_TIMEOUT);
        
        // 参数校验
        connectTimeout = (connectTimeout <= 0) ? 10 : connectTimeout;
        readTimeout = (readTimeout <= 0) ? 50 : readTimeout;
        writeTimeout = (writeTimeout <= 0) ? 50 : writeTimeout;
        
        // 创建基础构建器
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true);
        
        // 添加事件监听器（如果可用）
        addEventListenerIfAvailable(builder);
        
        // 添加自定义DNS
        addCustomDns(builder);
        
        // SSL 配置
        boolean trustAll = Config.getBoolean("app.http.trustAll", false);
        if (trustAll) {
            configureTrustAllSSL(builder);
        }
        
        return builder;
    }
    
    /**
     * 添加事件监听器（如果类可用）
     */
    private static void addEventListenerIfAvailable(OkHttpClient.Builder builder) {
        try {
            // 尝试加载 EventLogTimeListener
            Class<?> listenerClass = Class.forName("com.uoquo.utils.http.listener.EventLogTimeListener");
            Object factory = listenerClass.getField("FACTORY").get(null);
            builder.eventListenerFactory((okhttp3.EventListener.Factory) factory);
        } catch (Exception e) {
            // 监听器不可用，忽略
        }
    }
    
    /**
     * 添加自定义DNS（如果类可用）
     */
    private static void addCustomDns(OkHttpClient.Builder builder) {
        try {
            // 尝试加载 UoquoDns
            Class<?> dnsClass = Class.forName("com.uoquo.utils.http.UoquoDns");
            Object dnsInstance = dnsClass.getDeclaredConstructor().newInstance();
            builder.dns((okhttp3.Dns) dnsInstance);
        } catch (Exception e) {
            // UoquoDns 不可用，使用默认DNS
        }
    }
    
    /**
     * 创建 CookieJar
     */
    private static CookieJar createCookieJar(HttpCookies cookies) {
        return new CookieJar() {
            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> responseCookies) {
                // 不保存响应中的 Cookie
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                List<Cookie> list = new java.util.ArrayList<>();
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
                    // 忽略异常
                }
                return list;
            }
        };
    }
    
    /**
     * 配置信任所有 SSL 证书
     */
    private static void configureTrustAllSSL(OkHttpClient.Builder builder) {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager trustManager)) {
                throw new IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers));
            }

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { trustManager }, null);
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            
            builder.sslSocketFactory(sslSocketFactory, trustManager);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true; // 信任所有主机名
                }
            });
        } catch (Exception e) {
            // 忽略 SSL 配置异常
        }
    }
}