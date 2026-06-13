/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.web.utils;

import com.uoquo.utils.CurrentUser;
import com.uoquo.utils.DateUtil;
import com.uoquo.utils.SignParamUtil;
import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 描述：对http request参数处理. <br>
 * 日期：2018-01-18 16:11 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-01-18     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class WebUtil {
    private final static Logger log = LoggerFactory.getLogger(WebUtil.class);

    /** 签名计算时需要跳过的内置参数（已通过请求头单独参与签名） */
    private static final Set<String> SIGN_SKIP_PARAMS = new HashSet<>(Arrays.asList(
            CurrentUser.APPID,
            CurrentUser.TOKEN,
            CurrentUser.NONCE,
            CurrentUser.TIME,
            CurrentUser.DEVICE_ID,
            CurrentUser.USER_LANGUAGE,
            CurrentUser.SIGN_APP
    ));

    /**
     * 请求参数签名计算.<br>
     * 备注：主要用于spring拦截器<br>
     * @param secret  签名密钥
     * @param request 请求体
     * @return boolean
     * @author xuhz
     */
    public static String signParam(String secret, HttpServletRequest request) throws IOException {
        String appid    = getHeader(CurrentUser.APPID, request);
        String token    = getHeader(CurrentUser.TOKEN, request);
        String nonce    = getHeader(CurrentUser.NONCE, request);
        String time     = getHeader(CurrentUser.TIME,  request);
        String deviceId = getHeader(CurrentUser.DEVICE_ID, request);
        String language = getHeader(CurrentUser.USER_LANGUAGE, request);
        // 请求参数
        TreeMap<String, String> param = new TreeMap<>();
        Collection<String> keySet = request.getParameterMap().keySet();
        for (String key : keySet) {
            if (SIGN_SKIP_PARAMS.contains(key)) {
                continue;
            }
            String[] vals = request.getParameterValues(key);
            if (vals == null) {
                param.put(key, null);
            } else if (vals.length == 0) {
                param.put(key, "");
            } else if (vals.length == 1) {
                param.put(key, vals[0]);
            } else {
                param.put(key, JsonUtil.serialize(vals));
            }
        }
        // 如果是文件传输，则必须有文件的MD5
        if (request instanceof MultipartHttpServletRequest) {
            try {
                MultiValueMap<String, MultipartFile> files = ((MultipartHttpServletRequest)request).getMultiFileMap();
                files.values().forEach(list -> {
                    list.forEach(item -> {
                        String md5 = request.getParameter(item.getOriginalFilename());
                        if (StringUtil.isNull(md5)) {
                            throw new IllegalArgumentException(String.format("文件[%s]未传入对应的md5值", item.getOriginalFilename()));
                        }
                    });
                });
            } catch (IllegalStateException e) {
                // 忽略没有文件的情况
            }
        }
        // 消息体
        // 由于spring的处理，当消息体中为form表单、文件内容时，会将其读取到parameterMap和MultiFile中，此处将读取不到消息体，
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String contentType = request.getHeader("Content-Type");
        contentType = (contentType == null) ? "" : contentType.toLowerCase();
        // SSE请求不处理请求体签名（SSE为长连接流式响应，不含请求体）
        if (!isSseRequest(request)
                && !contentType.startsWith("multipart/form-data")
                && !contentType.startsWith("application/octet-stream")) {
            try (
                    ServletInputStream in = request.getInputStream();
            ) {
                byte[] buf = new byte[1024];
                int len = 0;
                while ((len = in.read(buf)) > -1) {
                    out.write(buf, 0, len);
                }
            } finally {
                close(out);
            }
        }

        return SignParamUtil.sign(appid, secret, token, language, nonce, deviceId, time, param, out.toByteArray());
    }

    /**
     * 是否是SSE（Server-Sent Events）请求.<br>
     * 判断依据：请求头 Accept 包含 text/event-stream。
     * @param request HttpServletRequest请求对象
     * @return true 是SSE请求，false 非SSE请求
     */
    public static boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.toLowerCase().contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    /**
     * 是否是Ajax请求.
     * @param request HttpServletRequest请求对象
     * @return true 是ajax，false 非ajax 
     */
    public static boolean isAjaxRequest(HttpServletRequest request) {
        // 同域ajax请求
        String requestType = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(requestType)) {
            return true;
        }
        // 跨域（cross、jsonp）请求（无X-Requested-With属性）
        String host = request.getHeader("Host");
        if (host.indexOf(":") > 0) {
            host = host.substring(0, host.indexOf(":"));
        }
        String origin = request.getHeader("Origin");
        if (StringUtil.isNull(origin)) {
            origin = request.getHeader("Referer");
        }
        if (StringUtil.notNull(origin)) {
            origin = origin.replace("http://", "").replace("https://", "");
            return origin.indexOf(host) != 0;
        }
        return false;
    }
    
    /**
     * 获取请求来源.
     * @param request HttpServletRequest请求对象
     * @return String
     */
    public static String getReferer(HttpServletRequest request) {
        // 来源URL
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return "";
        }
        // 域名与端口
        String host = request.getHeader("Host");
        if (host == null) {
            return referer;
        }
        // 去除host中的端口
        int idx = host.indexOf(":");
        if (idx > 0) {
            host = host.substring(0, idx);
        }
        // 去除host
        idx = referer.indexOf(host);
        if (idx >= 0) {
            referer = referer.substring(idx + host.length());
            // 去除ContextPath
            String ctnt = request.getContextPath();
            if (referer.startsWith(ctnt)) {
                referer = referer.substring(ctnt.length());
            }
        }
        return referer;
    }
    
    /**
     * 获得浏览器信息.
     * @param request HttpServletRequest请求对象
     * @return String
     */
    public static String getClientInfo(HttpServletRequest request) {
        return request.getHeader("user-agent");
    }

    /**
     * 得到客户端IP.
     * @param request HttpServletRequest请求对象
     * @return String
     */
    public static String getClientIp(HttpServletRequest request) {
        // 1. 优先获取请求头中的
        String clientIP = request.getHeader("X-Forwarded-For");
        if (StringUtil.notNull(clientIP) && !"unknown".equalsIgnoreCase(clientIP)) {
            String[] adds = clientIP.split(",");
            clientIP = adds[0].trim();
        }
        if (StringUtil.isNull(clientIP) || "unknown".equalsIgnoreCase(clientIP)) {
            clientIP = request.getHeader("Proxy-Client-IP");
        }
        if (StringUtil.isNull(clientIP) || "unknown".equalsIgnoreCase(clientIP)) {
            clientIP = request.getHeader("WL-Proxy-Client-IP");
        }
        if (StringUtil.isNull(clientIP) || "unknown".equalsIgnoreCase(clientIP)) {
            clientIP = request.getHeader("X-Real-IP");
        }
        // 2. 再获取getRemoteAddr
        if (StringUtil.isNull(clientIP) || "unknown".equalsIgnoreCase(clientIP)) {
            clientIP = request.getRemoteAddr();
        }
        // 返回内容
        if (StringUtil.isNull(clientIP) || "unknown".equalsIgnoreCase(clientIP)) {
            return null;
        } else {
            return clientIP;
        }
    }

    /**
     * 得到客户端IP.
     * @param headers 请求头信息
     * @return String
     */
    public static String getClientIp(HttpHeaders headers) {
        String clientIP = headers.getFirst("X-Forwarded-For");
        if (StringUtil.notNull(clientIP) && !"unknown".equalsIgnoreCase(clientIP)) {
            String[] adds = clientIP.split(",");
            clientIP = adds[0].trim();
        }
        if (StringUtil.isNull(clientIP) || "unknown".equalsIgnoreCase(clientIP)) {
            clientIP = headers.getFirst("Proxy-Client-IP");
        }
        if (StringUtil.isNull(clientIP) || "unknown".equalsIgnoreCase(clientIP)) {
            clientIP = headers.getFirst("WL-Proxy-Client-IP");
        }
        if (StringUtil.isNull(clientIP) || "unknown".equalsIgnoreCase(clientIP)) {
            clientIP = headers.getFirst("X-Real-IP");
        }
        return clientIP;
    }

    /**
     * 获取请求参数（优先获取头，其次从URL传参获取）.
     * @param key     键
     * @param request HttpServletRequest请求对象
     * @return String
     */
    public static String getHeader(String key, HttpServletRequest request) {
        // 从请求头中获取
        String value = request.getHeader(key);
        if (StringUtil.notNull(value)) {
            log.debug("从请求头中获取到参数：{}={}", key, value);
            return value;
        }
        Enumeration<String> values = request.getHeaders(key);
        if (values.hasMoreElements()) {
            value = values.nextElement();
            if (StringUtil.notNull(value)) {
                log.debug("从请求头中获取到多参数的第一个值：{}={}", key, value);
                return value;
            }
        }
        // 从URL参数或 Form-Data 中获取
        // 用于解决某些情况下无法添加Header的情况（如SSE模式）
        value = request.getParameter(key);
        if (StringUtil.notNull(value)) {
            log.debug("从URL或FORM表单中获取到参数：{}={}", key, value);
            return value;
        }
        return null;
    }

    /**
     * 获取请求参数（字符串）.
     * @param request HttpServletRequest请求对象
     * @param key     键
     * @param defval  默认值（前端传入的为空字符串、或者没有入参时）
     * @return String
     */
    public static String getValueString(HttpServletRequest request, String key, String defval) {
        String temp = (String) request.getParameter(key);
        if (StringUtil.isNull(temp)) {
            return defval;
        } else {
            return temp.trim();
        }
    }

    /**
     * 获取请求参数（long）.
     * @param request HttpServletRequest请求对象
     * @param key     键
     * @param defval  默认值（前端传入的为空、或者没有入参时）
     * @return long
     */
    public static long getValueLong(HttpServletRequest request, String key, long defval) {
        try {
            String temp = getValueString(request, key, null);
            return (temp == null) ? defval : Long.parseLong(temp);
        } catch (Exception e) {
            return defval;
        }
    }

    /**
     * 获取请求参数（Long）.
     * @param request HttpServletRequest请求对象
     * @param key     键
     * @param defval  默认值（前端传入的为空、或者没有入参时）
     * @return Long
     */
    public static Long getValueLong(HttpServletRequest request, String key, Long defval) {
        try {
            String temp = getValueString(request, key, null);
            return (temp == null) ? defval : Long.valueOf(temp);
        } catch (Exception e) {
            return defval;
        }
    }

    /**
     * 获取请求参数（int）.
     * @param request HttpServletRequest请求对象
     * @param key     键
     * @param defval  默认值（前端传入的为空、或者没有入参时）
     * @return int
     */
    public static int getValueInt(HttpServletRequest request, String key, int defval) {
        try {
            String temp = getValueString(request, key, null);
            return (temp == null) ? defval : Integer.parseInt(temp);
        } catch (Exception e) {
            return defval;
        }
    }

    /**
     * 获取请求参数（Integer）.
     * @param request HttpServletRequest请求对象
     * @param key     键
     * @param defval  默认值（前端传入的为空、或者没有入参时）
     * @return Integer
     */
    public static Integer getValueInt(HttpServletRequest request, String key, Integer defval) {
        try {
            String temp = getValueString(request, key, null);
            return (temp == null) ? defval : Integer.valueOf(temp);
        } catch (Exception e) {
            return defval;
        }
    }

    /**
     * 获取请求参数（boolean）.
     * @param request HttpServletRequest请求对象
     * @param key     键
     * @param defval  默认值（前端传入的为空、或者没有入参时）
     * @return boolean
     */
    public static boolean getValueBoolean(HttpServletRequest request, String key, boolean defval) {
        Boolean temp = getValueBoolean(request, key, null);
        return (temp == null) ? defval : temp;
        //return (temp == null) ? defval : Boolean.parseBoolean(temp);
    }

    /**
     * 获取请求参数（Boolean）.
     * @param request HttpServletRequest请求对象
     * @param key     键
     * @param defval  默认值（前端传入的为空、或者没有入参时）
     * @return Boolean
     */
    public static Boolean getValueBoolean(HttpServletRequest request, String key, Boolean defval) {
        try {
            String temp = getValueString(request, key, null);
            if (temp == null) {
                return defval;
            } else if ("true".equalsIgnoreCase(temp) || "yes".equalsIgnoreCase(temp) || "on".equalsIgnoreCase(temp) || "1".equalsIgnoreCase(temp)) {
                return Boolean.TRUE;
            } else {
                return Boolean.FALSE;
            }
            //return (temp == null) ? defval : Boolean.valueOf(temp);
        } catch (Exception e) {
            return defval;
        }
    }

    /**
     * 获取请求参数（double）.
     * @param request HttpServletRequest请求对象
     * @param key     键
     * @param defval  默认值（前端传入的为空、或者没有入参时）
     * @return double
     */
    public static double getValueDouble(HttpServletRequest request, String key, double defval) {
        try {
            String temp = getValueString(request, key, null);
            return (temp == null) ? defval : Double.parseDouble(temp);
        } catch (Exception e) {
            return defval;
        }
    }

    /**
     * 获取请求参数（Double）.
     * @param request HttpServletRequest请求对象
     * @param key     键
     * @param defval  默认值（前端传入的为空、或者没有入参时）
     * @return Double
     */
    public static Double getValueDouble(HttpServletRequest request, String key, Double defval) {
        try {
            String temp = getValueString(request, key, null);
            return (temp == null) ? defval : Double.valueOf(temp);
        } catch (Exception e) {
            return defval;
        }
    }
    
    /**
     * 获取请求参数（Date）.<br>
     * 可以解析以下传入类型的时间参数
     * <pre>
     * HH:mm
     * HH:mm:ss
     * yyyy-MM
     * yyyy-MM-dd
     * yyyy-MM-dd HH:mm
     * yyyy-MM-dd HH:mm:ss
     * yyyy-MM-dd HH:mm:ss.S
     * </pre>
     * @param request HttpServletRequest请求对象
     * @param key     键
     * @param defval  默认值（前端传入的为空、或者没有入参时）
     * @return Date
     */
    public static Date getValueDate(HttpServletRequest request, String key, Date defval) {
        try {
            String temp = getValueString(request, key, "");
            Date date = DateUtil.parse(temp);
            return (date == null) ? defval : date;
        } catch (Exception e) {
            return defval;
        }
    }
    
    /**
     * 读取请求体中的内容.<br>
     * 注：如果request不是 ContentCachingRequestWrapper，读取后内容将被清空，不可重复读取消费
     * @param request HttpServletRequest请求对象
     * @return 请求体字符串
     */
    public static String getRequestBody(HttpServletRequest request) {
        // 读取复用的请求体
        if (request instanceof ContentCachingRequestWrapper requestWrapper) {
            byte[] requestBody = requestWrapper.getContentAsByteArray();
            if (requestBody.length > 0) {
                try {
                    return new String(requestBody, request.getCharacterEncoding());
                } catch (UnsupportedEncodingException e) {
                    return new String(requestBody, StandardCharsets.UTF_8);
                }
            }
            return null;
        }
        // 常规读取
        StringBuilder sb   = new StringBuilder();
        String        line = null;
        try (BufferedReader reader = request.getReader()) {
            // 不能直接采用流的形式解析json数据，否则，出错的话，将丢失数据，所以先读取到字符串中
            // 读取ContentType为 text/plain 或者 application/x-www-form-urlencoded 的数据
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            // do nothing
        }
        // do nothing
        return sb.toString();
    }
    
    /**
     * 获取请求的参数.
     * @param request HttpServletRequest请求对象
     * @return 按字典排序后的参数
     */
    public static TreeMap<String, Object> getRequestParams(HttpServletRequest request) {
        TreeMap<String, Object> keyMap = new TreeMap<String, Object>();
        Collection<String> keySet = request.getParameterMap().keySet();
        for (String key : keySet) {
            String val = request.getParameter(key);
            keyMap.put(key, val);
        }
        return keyMap;
    }

    /**
     * 获取下载文件的文件名（文件名编码处理）.
     */
    public static String encodeFileName(HttpServletRequest request, String fileName) {
        String userAgent = request.getHeader("User-Agent");
        try {
            if (userAgent != null) {
                userAgent = userAgent.toLowerCase();
                if (userAgent.contains("msie") || userAgent.contains("trident")) {
                    // IE浏览器
                    return URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                } else if (userAgent.contains("firefox")) {
                    // Firefox
                    return new String(fileName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
                }
            }
            // 现代浏览器（默认处理）
            return "filename*=UTF-8''" +
                    URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        } catch (Exception e) {
            return fileName;
        }
    }

    private static void close(Closeable obj) {
        if (obj != null) {
            try {
                obj.close();
            } catch (Exception e) {
                // do nothing
            }
        }
    }
}
