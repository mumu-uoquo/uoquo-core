/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.http.listener;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 描述：请求耗时记录. <br>
 * 日期：2018-01-29 16:37 <br>
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
public class EventLogTimeListener extends EventListener {
    /**
     * 日志对象.
     */
    private static final Logger log = LoggerFactory.getLogger(EventLogTimeListener.class);
    
    
    public static final Factory FACTORY = new Factory() {
        final AtomicLong nextCallId = new AtomicLong(1L);

        @NonNull
        @Override
        public EventListener create(@NonNull Call call) {
            long callId = nextCallId.getAndIncrement();
            if (log.isDebugEnabled()) {
                String mesg = String.format("%04d %s", callId, call.request().url());
                log.debug(mesg);
            }
            return new EventLogTimeListener(callId, System.nanoTime());
        }
    };

    final long callId;
    final long callStartNanos;
    long lastEventNanos;

    EventLogTimeListener(long callId, long callStartNanos) {
        this.callId = callId;
        this.callStartNanos = callStartNanos;
        this.lastEventNanos = callStartNanos;
    }

    private void printEvent(String name) {
        long nanoTime = System.nanoTime();
        if (log.isDebugEnabled()) {
            String mesg = String.format("%04d %.3fs %.3fs %s", callId, (nanoTime - callStartNanos) / 1000000000d, (nanoTime - lastEventNanos) / 1000000000d, name);
            log.debug(mesg);
        }
        lastEventNanos = nanoTime;
    }

    @Override public void callStart(@NonNull Call call) {
        printEvent("callStart");
    }

    @Override public void dnsStart(@NonNull Call call, @NonNull String domainName) {
        printEvent("dnsStart");
    }

    @Override public void dnsEnd(@NonNull Call call, @NonNull String domainName, @NonNull List<InetAddress> inetAddressList) {
        printEvent("dnsEnd");
    }

    @Override public void connectStart(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull Proxy proxy) {
        printEvent("connectStart");
    }

    @Override public void secureConnectStart(@NonNull Call call) {
        printEvent("secureConnectStart");
    }

    @Override public void secureConnectEnd(@NonNull Call call, Handshake handshake) {
        printEvent("secureConnectEnd");
    }

    @Override public void connectEnd(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull Proxy proxy, Protocol protocol) {
        printEvent("connectEnd");
    }

    @Override public void connectFailed(@NonNull Call call, @NonNull InetSocketAddress inetSocketAddress, @NonNull Proxy proxy,
        Protocol protocol, @NonNull IOException ioe) {
        printEvent("connectFailed");
    }

    @Override public void connectionAcquired(@NonNull Call call, @NonNull Connection connection) {
        printEvent("connectionAcquired");
    }

    @Override public void connectionReleased(@NonNull Call call, @NonNull Connection connection) {
        printEvent("connectionReleased");
    }

    @Override public void requestHeadersStart(@NonNull Call call) {
        printEvent("requestHeadersStart");
    }

    @Override public void requestHeadersEnd(@NonNull Call call, @NonNull Request request) {
        printEvent("requestHeadersEnd");
    }

    @Override public void requestBodyStart(@NonNull Call call) {
        printEvent("requestBodyStart");
    }

    @Override public void requestBodyEnd(@NonNull Call call, long byteCount) {
        printEvent("requestBodyEnd");
    }

    @Override public void responseHeadersStart(@NonNull Call call) {
        printEvent("responseHeadersStart");
    }

    @Override public void responseHeadersEnd(@NonNull Call call, @NonNull Response response) {
        printEvent("responseHeadersEnd");
    }

    @Override public void responseBodyStart(@NonNull Call call) {
        printEvent("responseBodyStart");
    }

    @Override public void responseBodyEnd(@NonNull Call call, long byteCount) {
        printEvent("responseBodyEnd");
    }

    @Override public void callEnd(@NonNull Call call) {
        printEvent("callEnd");
    }

    @Override public void callFailed(@NonNull Call call, @NonNull IOException ioe) {
        printEvent("callFailed");
    }
}
