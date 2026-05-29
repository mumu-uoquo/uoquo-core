/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.http;

import com.uoquo.utils.DateUtil;
import com.uoquo.utils.json.JsonUtil;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 描述：构建请求参数类. <br>
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
public class HttpParams {
    
    /**
     * 参数编码.
     */
    private final Charset charset;
    
    /**
     * 表单请求参数.
     */
    private final Map<String, Object> formParam  = new LinkedHashMap<String, Object>();
    
    /**
     * 文件请求参数.
     */
    private final Map<String, List<File>> fileParam  = new LinkedHashMap<String, List<File>>();
    
    /**
     * 构造函数.
     */
    public HttpParams() {
        this(StandardCharsets.UTF_8);
    }
    
    /**
     * 构造函数.
     * @param charset 请求参数的编码格式
     */
    public HttpParams(Charset charset) {
        this.charset = charset;
    }
    
    /**
     * 增加请求参数. <br>
     * 注意：将覆盖已有值
     * @param key   请求参数key
     * @param value 请求参数值（String）
     */
    public void addParam(String key, String value) {
        if (key == null) {
            return;
        }
        // 删除原有值
        removeParam(key);
        // 添加新值
        value = (value == null) ? "" : value;
        formParam.put(key, value);
    }
    
    /**
     * 增加请求参数. <br>
     * 注意：将覆盖已有值
     * @param key   请求参数key
     * @param value 请求参数值（Number）
     */
    public void addParam(String key, Number value) {
        if (value == null) {
            addParam(key, "");
        } else {
            addParam(key, value.toString());
        }
    }
    
    /**
     * 增加请求参数. <br>
     * 注意：将覆盖已有值
     * @param key   请求参数key
     * @param value 请求参数值（Date）
     */
    public void addParam(String key, Date value) {
        if (value == null) {
            addParam(key, "");
        } else {
            addParam(key, DateUtil.toString(value, DateUtil.FORMAT_TIMESTAMP));
        }
    }
    
    /**
     * 增加请求参数. <br>
     * 注意：
     * <ol>
     *   <li>仅用于JSON传输</li>
     *   <li>将覆盖已有值</li>
     * </ol>
     * @param key   请求参数key
     * @param value 请求参数值（Date）
     */
    public void addParam(String key, Map<String, ?> value) {
        formParam.put(key, value);
    }
    
    /**
     * 增加请求参数. <br>
     * 注意：
     * <ol>
     *   <li>仅用于JSON传输</li>
     *   <li>将覆盖已有值</li>
     * </ol>
     * @param key   请求参数key
     * @param value 请求参数值（Date）
     */
    public void addParam(String key, Collection<?> value) {
        formParam.put(key, value);
    }
    
    /**
     * 增加请求参数. <br>
     * 注意：将覆盖已有值
     * @param key   请求参数key
     * @param value 请求参数值（File）
     */
    public void addParam(String key, File value) {
        if ((key == null) || (value == null)) {
            return;
        }
        
        List<File> list = fileParam.get(key);
        if (list == null) {
            list = new ArrayList<File>();
        }
        list.add(value);
        fileParam.put(key, list);
    }
    
    /**
     * 增加请求参数. <br>
     * 注意：将覆盖已有值
     * @param key   请求参数key
     * @param value 请求参数值（File list）
     */
    public void addParam(String key, List<File> value) {
        if ((key == null) || (value == null) || (value.isEmpty())) {
            return;
        }
        
        List<File> list = fileParam.get(key);
        if (list == null) {
            list = new ArrayList<File>();
        }
        list.addAll(value);
        fileParam.put(key, list);
    }
    
    /**
     * 删除请求参数.
     * @param key 请求参数key
     */
    public void removeParam(String key) {
        if (key == null) {
            return;
        }
        formParam.remove(key);
        fileParam.remove(key);
    }
    
    /**
     * 获取表单参数值.
     * @param key 请求参数key
     * @return 参数对应的值
     */
    public Object getFormParam(String key) {
        if (key == null) {
            return null;
        }
        return formParam.get(key);
    }
    
    /**
     * 获取文件参数值.
     * @param key 请求参数key
     * @return 参数对应的值
     */
    public List<File> getFileParam(String key) {
        if (key == null) {
            return null;
        }
        return fileParam.get(key);
    }
    
    /**
     * 是否存在值.
     * @return true：有，false：无
     */
    public boolean existFormParam() {
        return !formParam.isEmpty();
    }
    
    /**
     * 键值是否存在.
     * @param key 请求参数key
     * @return 执行结果（true：存在，false：不存在）
     */
    public boolean existParam(String key) {
        if (key == null) {
            return false;
        }
        return formParam.containsKey(key) || fileParam.containsKey(key);
    }
    
    /**
     * 获取表单请求参数列表.
     * @return 请求参数列表
     */
    public Map<String, String> getFormParams() {
        Map<String, String> map = new HashMap<String, String>();
        for (String key : formParam.keySet()) {
            Object val = formParam.get(key);
            String value = "";
            if (val instanceof String) {
                value = (String) val;
            } else {
                value = JsonUtil.serialize(val);
            }
            map.put(key, value);
        }
        return map;
    }
    
    /**
     * 获取表单请求参数列表.
     * @return 请求参数列表
     */
    public Map<String, Object> getJsonParams() {
        return formParam;
    }
    
    /**
     * 获取文件请求参数列表.
     * @return 请求参数列表
     */
    public Map<String, List<File>> getFileParams() {
        return fileParam;
    }
    
    /**
     * 获取URL编码后的参数.
     * @return 编码后的参数
     */
    public String getURLEncodedParams() {
        if (!existFormParam()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String key : formParam.keySet()) {
            Object val = formParam.get(key);
            String value = "";
            if (val != null) {
                if (val instanceof String) {
                    value = (String) val;
                } else {
                    value = JsonUtil.serialize(val);
                }
            }
            value = URLEncoder.encode(value, charset);
            sb.append(key).append("=").append(value).append("&");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }
    
}
