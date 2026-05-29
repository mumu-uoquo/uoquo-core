/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.codegen.util;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PkgScanner {
    /**
     * 包名
     */
    private String pkgName;

    /**
     * 包对应的路径名
     */
    private String basePkg;

    /**
     * 包对应的路径名
     */
    private String basePath;

    /**
     * 注解的class对象
     */
    private Class anClazz;

    private ClassLoader cl;


    public PkgScanner(String pkgName) {
        this.pkgName = pkgName;

        int idx = pkgName.indexOf("*");
        String temp = pkgName;
        if (idx > 0) {
            temp = pkgName.substring(0, idx);
            if (temp.endsWith(".")) {
                temp = temp.substring(0, idx - 1);
            } else {
                temp = temp.substring(0, temp.lastIndexOf("."));
            }
        }
        this.basePkg  = temp;
        this.basePath = PkgUtils.packageToPath(temp);

        cl = Thread.currentThread().getContextClassLoader();
    }

    public PkgScanner(String pkgName, Class anClazz) {
        this(pkgName);

        this.anClazz = anClazz;
    }

    /**
     * 执行扫描操作.
     *
     * @return
     * @throws IOException
     */
    public List<String> scan() throws IOException {
        List<String> list = loadResource();

        if (null != this.anClazz) {
            list = filterComponents(list);
        }
        if (!this.basePkg.equals(this.pkgName)) {
            list = filterPackage(list);
        }
        return list;
    }

    private List<String> loadResource() throws IOException {
        List<String> list = new ArrayList<>();

        Enumeration<URL> urls = cl.getResources(this.basePath);
        while (urls.hasMoreElements()) {
            URL u = urls.nextElement();

            ResourceType type = determineType(u);

            switch (type) {
                case JAR:
                    String path = PkgUtils.distillPathFromJarURL(u.getPath());
                    List temp1 = scanJar(path);
                    list.addAll(temp1);
                    break;

                case FILE:
                    List temp2 = scanFile(u.getPath(), this.basePkg);
                    list.addAll(temp2);
                    break;
            }
        }

        return list;
    }

    /**
     * 根据URL判断是JAR包还是文件目录
     * @param url
     * @return
     */
    private ResourceType determineType(URL url) {
        if (url.getProtocol().equals(ResourceType.FILE.getTypeString())) {
            return ResourceType.FILE;
        }

        if (url.getProtocol().equals(ResourceType.JAR.getTypeString())) {
            return ResourceType.JAR;
        }

        throw new IllegalArgumentException("不支持该类型:" + url.getProtocol());
    }

    /**
     * 扫描JAR文件
     * @param path
     * @return
     * @throws IOException
     */
    private List<String> scanJar(String path) throws IOException {
        JarFile jar = new JarFile(path);

        List<String> classNameList = new ArrayList<>();

        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();

            if( (name.startsWith(this.basePath)) && (name.endsWith(ResourceType.CLASS_FILE.getTypeString())) ) {
                name = PkgUtils.trimSuffix(name);
                name = PkgUtils.pathToPackage(name);

                classNameList.add(name);
            }
        }

        return classNameList;
    }

    /**
     * 扫描文件目录下的类
     * @param path
     * @return
     */
    private List<String> scanFile(String path, String basePkg) {
        File f = new File(path);

        List<String> classNameList = new ArrayList<>();

        // 得到目录下所有文件(目录)
        File[] files = f.listFiles();
        if (null != files) {
            int LEN = files.length;

            for (int ix = 0 ; ix < LEN ; ++ix) {
                File file = files[ix];

                // 判断是否还是一个目录
                if (file.isDirectory()) {
                    // 递归遍历目录
                    List<String> list = scanFile(file.getAbsolutePath(), PkgUtils.concat(basePkg, ".", file.getName()));
                    classNameList.addAll(list);

                } else if (file.getName().endsWith(ResourceType.CLASS_FILE.getTypeString())) {
                    // 如果是以.class结尾
                    String className = PkgUtils.trimSuffix(file.getName());
                    // 如果类名中有"$"不计算在内
                    if (-1 != className.lastIndexOf("$")) {
                        continue;
                    }

                    // 命中
                    String result = PkgUtils.concat(basePkg, ".", className);
                    classNameList.add(result);
                }
            }
        }

        return classNameList;
    }

    /**
     * 过虑掉没有指定注解的类
     * @param classList
     * @return
     */
    private List<String> filterComponents(List<String> classList) {
        List<String> newList = new ArrayList<>(classList.size());

        classList.forEach(name -> {
            try {
                Class clazz = Class.forName(name);
                Annotation an = clazz.getAnnotation(this.anClazz);
                if (null != an) {
                    newList.add(name);
                }

            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        });

        return newList;
    }

    /**
     * 过虑掉不匹配的包
     * @param classList
     * @return
     */
    private List<String> filterPackage(List<String> classList) {
        List<String> newList = new ArrayList<>(classList.size());
        String regex = this.pkgName
                .replaceAll("\\.\\*\\*\\.", "\\.ANY_PKG")
                .replaceAll("\\.\\*\\.", "\\.ONE_PKG\\.")
                .replaceAll("\\*", "ANY_NAME")
                .replaceAll("\\.", "\\\\.")
                .replaceAll("ANY_PKG", "([a-z]+[a-zA-Z0-9]*[\\.]*)*")
                .replaceAll("ONE_PKG", "([a-z]+[a-zA-Z0-9]*){1}")
                .replaceAll("ANY_NAME", "[a-zA-Z0-9]*");
        Pattern p = Pattern.compile(regex);

        classList.forEach(name -> {
            Matcher m = p.matcher(name);
            if (m.find()) {
                newList.add(name);
            }
        });

        return newList;
    }
}
