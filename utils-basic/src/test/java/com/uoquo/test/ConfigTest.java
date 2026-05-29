/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test;

import com.uoquo.utils.Config;
import com.uoquo.utils.StringUtil;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigTest {

    @Test
    public void testConfig() {
        String appType = Config.getString("app.type");
        System.out.println(appType);
        String dirTemp = Config.getString("app.temp-dir");
        System.out.println(dirTemp);
    }

    @Test
    public void testParse(){
        String str0 = "${DIR_TEMP}";
//        System.out.println(str0 + " end '}' index: " + this.endIndex(str0));
        String val0 = parsePropertieValue(str0);
        System.out.println(str0 + " ==> " + val0);
//
//        String str1 = "${DIR_TEMP:./temp}";
////        System.out.println(str1 + " end '}' index: " + this.endIndex(str1));
//        String val1 = parsePropertieValue(str1);
//        System.out.println(str1 + " ==> " + val1);
//
//        String str2 = "${DIR_TEMP:${app.dir-temp:./temp}}";
////        System.out.println(str2 + " end '}' index: " + this.endIndex(str2));
//        String val2 = parsePropertieValue(str2);
//        System.out.println(str2 + " ==> " + val2);

        String str3 = "jdbc:mysql://${DB_HOST:dev.xuziu.com}:${DB_PORT:3306}/uoquo_health?serverTimezone=${DB_TZ:${TZ:Asia/Shanghai}}&allowMultiQueries=true${DB_EXTENSION:&characterEncoding=utf8&useSSL=true}";
//        System.out.println(str3 + " end '}' index: " + this.endIndex(str3));
        String val3 = parsePropertieValue(str3);
        System.out.println(str3 + " ==> " + val3);
    }


    Pattern p = Pattern.compile("\\$\\{[a-zA-Z0-9_\\-\\.]+[:\\S]*\\}"); // 将从最里层开始往外递归处理
    private String parsePropertieValue(String val) {
        Matcher m = p.matcher(val);
        while (m.find()) {
            String tkey = m.group();
            int index = endIndex(tkey);
            String tval = tkey.substring(2, index);
            String append = (index < tkey.length() - 1) ? tkey.substring(index + 1) : "";
            String[] strs = tval.split(":",2); // spring以冒号分割，前一个为变量，后一个为默认值
            String val2 = this.getBySystem(strs[0]);

            if (val2 != null) {
                val = val.replace(tkey, val2) + append;
            } else if (strs.length > 1) {
                val = val.replace(tkey, strs[1]) + append;
            } else {
                val = val.replace(tkey, "") + append;
            }
        }
        // 如果还有没替换的，则继续替换
        m = p.matcher(val);
        if (m.find()) {
            return parsePropertieValue(val);
        } else {
            return val;
        }
    }

    private String getBySystem(String key) {
        String val = System.getProperty(key);
        if (StringUtil.isNull(val)) {
            val = System.getenv(key);
        }
        return val;
    }

    private int endIndex(String str) {
        int bgnIdx = str.indexOf("${");
        int endIdx = str.indexOf("}");

        while (true) {
            int nextBgnIdx = str.indexOf("${", bgnIdx + 1);
            if (nextBgnIdx == -1 || nextBgnIdx > endIdx) {
                break;
            }
            bgnIdx = nextBgnIdx;
            endIdx = str.indexOf("}", endIdx + 1);
         }

        return endIdx;
    }

}
