/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.http;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import okhttp3.Dns;
import org.jspecify.annotations.NonNull;

/**
 * 描述：自定义DNS解析. <br>
 * 背景：为了防止DNS劫持，可以对于一些域名的DNS信息进行固化保存，不再请求DNS服务器，类似于本地的hosts文件. <br>
 * 优点：
 * <ul>
 *   <li>防止DNS劫持</li>
 *   <li>加速DNS解析，快速获取域名对应的服务器IP</li>
 * </ul>
 * 日期：2019-05-17 10:38 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2019-05-17     Administrator.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class UoquoDns implements Dns {
    private static final Dns SYSTEM = Dns.SYSTEM;

    @NonNull
    @Override
    public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
        /*
        String ip = DNSHelper.getIpByHost(hostname);
        if (ip != null && !ip.equals("")) {
            List<InetAddress> inetAddresses = Arrays.asList(InetAddress.getAllByName(ip));
            return inetAddresses;
        }
        */
        return SYSTEM.lookup(hostname);
    }
}
