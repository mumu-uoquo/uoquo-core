///**
// * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
// * 注意：本内容仅限于内部传阅，禁止外泄
// */
//package com.uoquo.test.codegen.csharp;
//
//import com.uoquo.test.codegen.util.PkgUtils;
//import com.uoquo.test.codegen.util.PkgScanner;
//import io.swagger.annotations.ApiModel;
//import io.swagger.annotations.ApiModelProperty;
//
//import java.io.File;
//import java.io.FileWriter;
//import java.io.IOException;
//import java.lang.reflect.Field;
//import java.lang.reflect.ParameterizedType;
//import java.lang.reflect.Type;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Map;
//
///**
// * 将DTO和Param转换为C#格式
// */
//public class Pdo2C {
//
//    /**
//     * 转换指定包路径下的对象<br>
//     * 返回对象与命名空间的对应关系
//     */
//    public void convert(String dir, String pkg, Map<String, String> javaMap, Map<String, String> nsMap) {
//        PkgScanner scanner = new PkgScanner(pkg);
//        List<String> list = null;
//        try {
//            list = scanner.scan();
//        } catch (IOException e) {
//            e.printStackTrace();
//            list = new ArrayList<>();
//        }
//        list.forEach( name -> {
//            try {
//                Class clazz = Class.forName(name);
//                String clsName = PkgUtils.firstChartToUpper(clazz.getSimpleName());
//                String namespace = convertBean(dir, clazz);
//                if (namespace != null) {
//                    nsMap.put(clsName, namespace);
//                    javaMap.put(clsName, clazz.getName());
//                }
//            } catch (Exception e) {
//                System.out.println(String.format("transe dto to c# : %s", name));
//                e.printStackTrace();
//            }
//        });
//    }
//
//    /**
//     * 对象转换
//     */
//    private String convertBean(String dir, Class clazz) throws ClassNotFoundException {
//        // 类注释及名称
//        ApiModel clsAn = (ApiModel)clazz.getAnnotation(ApiModel.class);
//        String clsText = (clsAn == null) ? null : clsAn.description();
//        String clsName = PkgUtils.firstChartToUpper(clazz.getSimpleName());
//        String nmspace = PkgUtils.getNamespace(clazz.getPackageName());
//        // 字段注释及名称
//        List<Field> fieldsList = new ArrayList<>();
//        while (clazz != null) {  // 遍历所有父类字节码对象
//            Field[] declaredFields = clazz.getDeclaredFields();
//            fieldsList.addAll(Arrays.asList(declaredFields));
//            clazz = clazz.getSuperclass();  // 获得父类的字节码对象
//        }
//        if (fieldsList.isEmpty()) {
//            return null;
//        }
//
//        // 拼接C#属性
//        StringBuilder fieldSb = new StringBuilder();
//        List<String> usList = new ArrayList<>();
//        for (Field field : fieldsList) {
//            ApiModelProperty fieldAn = (ApiModelProperty)field.getAnnotation(ApiModelProperty.class);
//            if (fieldAn != null) {
//                fieldSb.append("        /// <summary>\n");
//                fieldSb.append("        /// ").append(fieldAn.value()).append("\n");
//                fieldSb.append("        /// </summary>\n");
//            }
//            fieldSb.append("        public ")
//                    .append(transeType(field.getGenericType(), usList))
//                    .append(" ")
//                    .append(PkgUtils.firstChartToUpper(field.getName()))
//                    .append(" { get; set; }\n\n");
//        }
//
//        // 拼接C#对象
//        StringBuilder content = new StringBuilder();
//        usList.forEach(item -> {
//            if (!item.equals(nmspace)) {
//                content.append("using ").append(item).append(";\n");
//            }
//        });
//        content.append("using System;\n");
//        content.append("using System.Collections.Generic;\n");
//        content.append("using System.Linq;\n");
//        content.append("using System.Text;\n");
//        content.append("using System.Threading.Tasks;\n\n");
//        content.append("namespace ").append(nmspace).append("\n");
//        content.append("{\n");
//        if (clsText != null){
//            content.append("    /// <summary>\n");
//            content.append("    /// ").append(clsText).append("\n");
//            content.append("    /// </summary>\n");
//        }
//        content.append("    public class ").append(clsName).append("\n");
//        content.append("    {\n");
//        content.append(fieldSb);
//        content.append("    }\n");
//        content.append("}\n");
//        // 输出
//        String pojoPath = dir + nmspace.replaceAll("\\.", "/")  +"/"+ clsName + ".cs";
//        File file = new File(pojoPath);
//        if (!file.getParentFile().exists()) {
//            file.getParentFile().mkdirs();
//        }
//        try (
//                FileWriter writer = new FileWriter(file);
//        ) {
//            writer.write(content.toString());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        // 返回对象对应的命名空间
//        return nmspace;
//    }
//
//    /**
//     * 转换属性类型
//     */
//    private String transeType(Type type, List<String> usList) {
//        Class<?> rawClz = null;
//        List<Type> argClz = new ArrayList<>();
//        if (type instanceof ParameterizedType) {
//            ParameterizedType parameterizedType = (ParameterizedType) type;
//            rawClz = (Class)parameterizedType.getRawType();
//            argClz.addAll(Arrays.asList(parameterizedType.getActualTypeArguments()));
////        } else if (type instanceof GenericArrayType) {
////            GenericArrayType genericArrayType = (GenericArrayType) type;
////            System.out.println("GenericArrayType type :" + genericArrayType);
////            Type genericComponentType = genericArrayType.getGenericComponentType();
////            System.out.println("genericComponentType:" + genericComponentType);
////        } else if (type instanceof WildcardType) {
////            WildcardType wildcardType = (WildcardType) type;
////            System.out.println("WildcardType type :" + wildcardType);
////        } else if (type instanceof TypeVariable) {
////            TypeVariable typeVariable = (TypeVariable) type;
////            System.out.println("TypeVariable type :" + typeVariable);
//        } else {
//            rawClz = (Class) type;
//        }
//        // 不带泛型参数
//        if (rawClz.isPrimitive()) {
//            return PkgUtils.transeJavaClass2C(type.getTypeName());
//        } else if (argClz.isEmpty()) {
//            if (rawClz.getTypeName().startsWith("com.lepu")) {
//                putUsingList(usList, rawClz.getTypeName());
//            }
//            return PkgUtils.transeJavaClass2C(rawClz);
//        }
//        // 带泛型参数的
//        StringBuilder sb = new StringBuilder();
//        sb.append(PkgUtils.transeJavaClass2C(rawClz));
//        sb.append("<");
//        for (Type item : argClz) {
//            if (item.getTypeName().startsWith("com.lepu")) {
//                putUsingList(usList, item.getTypeName());
//            }
//            if (item instanceof ParameterizedType) {
//                sb.append(transeType(item, usList));
//            } else {
//                sb.append(PkgUtils.transeJavaClass2C((Class)item));
//            }
//            sb.append(", ");
//        }
//        sb.setLength(sb.length() - 2);
//        sb.append(">");
//        return sb.toString();
//    }
//
//    private void putUsingList(List<String> usList, String pkg) {
//        String nm = PkgUtils.getNamespace(pkg);
//        if (!usList.contains(nm)) {
//            usList.add(nm);
//        }
//    }
//
//}
