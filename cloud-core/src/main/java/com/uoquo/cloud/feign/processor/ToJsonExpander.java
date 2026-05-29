/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.cloud.feign.processor;

import com.uoquo.web.base.BaseEntity;
import com.uoquo.utils.json.JsonUtil;

import feign.Param.Expander;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * 描述：将数据转换为JSON格式. <br>
 * 日期：2018-06-28 08:39 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-06-28     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class ToJsonExpander implements Expander {
    
    @Override
    public String expand(Object value) {
        // 注：进入expand的参数都不会为null
        if (value == null) {
            return null;
        }
        
        try {
            String temp = JsonUtil.serialize(value);
            // 这几种类型的数据对应的body模版不带双引号，可以直接替换拼接
            if ((value instanceof BaseEntity) || (value instanceof Map) || (value instanceof Collection) || (value instanceof Array)) {
                return temp;
            }
            // 其他格式的数据对应的body模版已经含有双引号，所以需要去除首尾的双引号再拼接
            if (temp.startsWith("\"")) {
                temp = temp.substring(1);
            }
            if (temp.endsWith("\"")) {
                temp = temp.substring(0, temp.length() - 1);
            }
            return temp;
        } catch (Exception e) {
            return value.toString();
            /*
            String temp = value.toString();
            if (!temp.startsWith("\"")) {
                temp = "\"" + temp;
            }
            if (!temp.endsWith("\"")) {
                temp += "\"";
            }
            return temp;
            */
        }
    }
}
