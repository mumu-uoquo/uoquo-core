/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils.http;

import com.uoquo.utils.crypto.MD5;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

/**
 * 描述：对入参进行签名验证. <br>
 * 背景：将请求的Form表单参数添加签名数据. <br>
 * 注意：
 * <pre>
 *  1. 参数中不可含有“signature”关键字，该参数默认为签名数据
 *  2. 参数中不可含有“timestamp”关键字，该参数默认为签名时间戳
 * </pre>
 * 日期：2018-01-29 12:58 <br>
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
public class HttpParamsUtil {

    /**
     * 生成签名.
     * @param params 欲生成签名的集合
     * @param appid  接口标识
     * @param secret 接口秘钥
     * @return 添加签名信息的参数对象
     */
    public static HttpParams sign(HttpParams params, String appid, String secret) {
        // 拼接加入appid
        //params.addParam("appid", appid);
        // 拼接加入时间戳
        params.addParam("timestamp", (new Date()).getTime());
        // 将参数进行字典排序
        TreeMap<String, Object> keyMap = new TreeMap<String, Object>();
        Map<String, String> formParam = params.getFormParams();
        for (String key : formParam.keySet()) {
            keyMap.put(key, formParam.get(key));
        }
        // 签名认证
        String signature = signData(keyMap, secret);
        params.addParam("signature", signature);
        return params;
    }
    
    /**
     * 数据签名. <br>
     * 算法：
     * <pre>
     *  1. 参数按字典排序
     *  2. 按排序后的字典数据进行“参数1+参数1值+...+参数N+参数N值”的方式进行拼接
     *  3. 在末尾拼接上密钥
     * </pre>
     * @param map    字典排序后的待签名数据
     * @param secret 签名密钥
     */
    public static String signData(TreeMap<String, Object> map, String secret) {
        // 拼接签名字串
        StringBuilder signature = new StringBuilder();
        map.remove("signature"); //移除传入的签名数据
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            signature.append(entry.getKey());
            signature.append(entry.getValue());
        }
        // 签名密钥
        signature.append(secret);
        // 签名认证
        return MD5.encrypt(signature.toString());
    }
}
