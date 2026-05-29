/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.codegen.util;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PkgUtils {
    private PkgUtils() {}

    /**
     * 把路径字符串转换为包名.
     * a/b/c/d -> a.b.c.d
     *
     * @param path
     * @return
     */
    public static String pathToPackage(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        return path.replaceAll("/", ".");
    }

    /**
     * 包名转换为路径名
     * @param pkg
     * @return
     */
    public static String packageToPath(String pkg) {
//        return pkg.replaceAll("\\.", Matcher.quoteReplacement(File.separator));
        return pkg.replaceAll("\\.", "/");
    }

    /**
     * 将多个对象转换成字符串并连接起来
     * @param objs
     * @return
     */
    public static String concat(Object... objs) {
        StringBuilder sb = new StringBuilder();
        for (int ix = 0 ; ix < objs.length ; ++ix) {
            sb.append(objs[ix]);
        }

        return sb.toString();
    }

    /**
     * 去掉文件的后缀名
     * @param name
     * @return
     */
    public static String trimSuffix(String name) {
        int dotIndex = name.indexOf('.');
        if (-1 == dotIndex) {
            return name;
        }

        return name.substring(0, dotIndex);
    }

    public static String distillPathFromJarURL(String url) {
        int startPos = url.indexOf(':');
        int endPos = url.lastIndexOf('!');

        return url.substring(startPos + 1, endPos);
    }

    /**
     * 首字母大写
     */
    public static String firstChartToUpper(String str) {
        StringBuilder sb = new StringBuilder();
        sb.append(str.toCharArray());
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        return sb.toString();
    }

    /**
     * 转换C#数据类型<br>
     * 备注：基本类型允许为空
     */
    public static String transeJavaClass2C(Class<?> clz) {
        //
        if (String.class.isAssignableFrom(clz)) {
            return "string";
        } else if (Boolean.class.isAssignableFrom(clz)) {
            return "bool?";
        } else if (Byte.class.isAssignableFrom(clz)) {
            return "sbyte?";
        } else if (Character.class.isAssignableFrom(clz)) {
            return "char?";
        } else if (Short.class.isAssignableFrom(clz)) {
            return "short?";
        } else if (Integer.class.isAssignableFrom(clz)) {
            return "int?";
        } else if (Long.class.isAssignableFrom(clz)) {
            return "long?";
        } else if (Float.class.isAssignableFrom(clz)) {
            return "float?";
        } else if (Double.class.isAssignableFrom(clz)) {
            return "double?";
        } else if (Number.class.isAssignableFrom(clz)) {
            return "decimal?";
        } else if (Date.class.isAssignableFrom(clz)) {
            return "DateTime?";
        } else if (Map.class.isAssignableFrom(clz)) {
            return "Dictionary";
        } else if (List.class.isAssignableFrom(clz)) {
            return "List";
        } else if (Set.class.isAssignableFrom(clz)) {
            return "List";
        } else if (clz.isAssignableFrom(Object.class)) {
            return "object";
        }
        // 其他
        return transeJavaClass2C(clz.getSimpleName());
    }

    public static String transeJavaClass2C(String typeName) {
        switch (typeName) {
            case "boolean": return "bool";
            default:
                return typeName;
        }
    }

    /**
     * 转换命名空间
     */
    public static String getServiceNamespace(String basePath) {
        StringBuilder sb = new StringBuilder();
        // 前缀
        sb.append("CardIoT.Remoting");
        if (basePath.startsWith("/api/auth")) {
            sb.append(".Auth.");
        } else if (basePath.startsWith("/api/abpm")) {
            sb.append(".ABPM.");
        } else if (basePath.startsWith("/api/bpm")) {
            sb.append(".Bpm.");
        } else if (basePath.startsWith("/api/dfs")) {
            sb.append(".DFS.");
        } else if (basePath.startsWith("/api/holter")) {
            sb.append(".HolterEcg.");
        } else if (basePath.startsWith("/api/mpmonitor")) {
            sb.append(".MPMonitor.");
//        } else if (basePath.startsWith("/api/patient")) {
//            sb.append(".Patient.");
        } else if (basePath.startsWith("/api/platform")) {
            sb.append(".Platform.");
        } else if (basePath.startsWith("/api/resting")) {
            sb.append(".Resting.");
        }
        sb.append("Service");
        return sb.toString();
    }

    /**
     * 转换命名空间
     */
    public static String getNamespace(String pkg) {
        StringBuilder sb = new StringBuilder();
        // 前缀
        sb.append("CardIoT.Remoting");
        // 包
        if (pkg.indexOf(".auth.") > 0) {
            sb.append(".Auth");
        } else if (pkg.indexOf(".abpm.") > 0) {
            sb.append(".ABPM");
        } else if (pkg.indexOf(".bpm.") > 0) {
            sb.append(".Bpm");
        } else if (pkg.indexOf(".dfs.") > 0) {
            sb.append(".DFS");
        } else if (pkg.indexOf(".common.") > 0) {
            sb.append(".Common");
        } else if (pkg.indexOf(".holterecg.") > 0) {
            sb.append(".HolterEcg");
        } else if (pkg.indexOf(".mpmonitor.") > 0) {
            sb.append(".MPMonitor");
//        } else if (pkg.indexOf(".patient.") > 0) {
//            sb.append(".Patient");
        } else if (pkg.indexOf(".platform.") > 0) {
            sb.append(".Platform");
        } else if (pkg.indexOf(".restingecg.") > 0) {
            sb.append(".Resting");
        }
        else if (pkg.indexOf(".holterecg.") > 0) {
            sb.append(".Holter");
        }
        // 后缀
        if (pkg.indexOf(".dto") > 0) {
            sb.append(".DTO");
        } else if (pkg.indexOf(".param") > 0) {
            sb.append(".Param");
        }
        return sb.toString();
    }
}
