/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.http;

import com.uoquo.utils.DateUtil;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 描述：构建请求自定义cookie. <br>
 * 日期：2018-01-29 09:48 <br>
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
public class HttpCookies {
    
    /**
     * 参数编码.
     */
    private final Charset charset;
    
    /**
     * 请求参数.
     */
    private final Map<String, String> param  = new LinkedHashMap<String, String>();
    
    /**
     * 构造函数.
     */
    public HttpCookies() {
        this(StandardCharsets.UTF_8);
    }
    
    /**
     * 构造函数.
     * @param charset 请求参数的编码格式
     */
    public HttpCookies(Charset charset) {
        this.charset = charset;
    }
    
    /**
     * 增加请求参数. <br>
     * 注意：将覆盖已有值
     * @param key   请求参数key
     * @param value 请求参数值（String）
     */
    public void add(String key, String value) {
        if (key == null) {
            return;
        }
        // 删除原有值
        remove(key);
        // 添加新值
        value = (value == null) ? "" : value;
        try {
            param.put(key, URLEncoder.encode(value, charset));
        } catch (Exception e) {
            param.put(key, value);
        }
    }
    
    /**
     * 增加请求参数. <br>
     * 注意：将覆盖已有值
     * @param key   请求参数key
     * @param value 请求参数值（Number）
     */
    public void add(String key, Number value) {
        if (value == null) {
            add(key, "");
        } else {
            add(key, value.toString());
        }
    }
    
    /**
     * 增加请求参数. <br>
     * 注意：将覆盖已有值
     * @param key   请求参数key
     * @param value 请求参数值（Date）
     */
    public void add(String key, Date value) {
        if (value == null) {
            add(key, "");
        } else {
            add(key, DateUtil.toString(value, DateUtil.FORMAT_TIMESTAMP));
        }
    }
    
    /**
     * 删除请求参数.
     * @param key 请求参数key
     */
    public void remove(String key) {
        if (key == null) {
            return;
        }
        param.remove(key);
    }
    
    /**
     * 获取表单请求参数列表.
     * @return 请求参数列表
     */
    public Map<String, String> get() {
        return param;
    }
    
    /**
     * 获取表单参数值.
     * @param key 请求参数key
     * @return 参数对应的值
     */
    public String get(String key) {
        if (key == null) {
            return null;
        }
        String value = param.get(key);
        try {
            return URLDecoder.decode(value, charset);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 键值是否存在.
     * @param key 请求参数key
     * @return 执行结果（true：存在，false：不存在）
     */
    public boolean exist(String key) {
        if (key == null) {
            return false;
        }
        return param.containsKey(key);
    }

    /**
     * 是否空.
     * @return true：空，false：非空
     */
    public boolean empty() {
        return param.isEmpty();
    }
    
}
