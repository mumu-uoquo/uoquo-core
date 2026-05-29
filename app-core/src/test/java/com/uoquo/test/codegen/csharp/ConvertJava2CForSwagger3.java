///**
// * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
// * 注意：本内容仅限于内部传阅，禁止外泄
// */
//package com.uoquo.test.codegen.csharp;
//
//import com.alibaba.nacos.api.NacosFactory;
//import com.alibaba.nacos.api.naming.NamingService;
//import com.alibaba.nacos.api.naming.pojo.Instance;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Properties;
//
//public class ConvertJava2CForSwagger3 {
//
//
//    public static void main(String[] args) {
//        String outDir = "D:/tempr/";
//
//        // 转换DTO和Param
//        Map<String, String> dtoNSMap   = new HashMap<>(); // DTO对应的C命名空间
//        Map<String, String> dtoJavaMap = new HashMap<>(); // DTO对应的java对象
//        Map<String, String> prmNSMap   = new HashMap<>(); // Param对应的C命名空间
//        Map<String, String> prmJavaMap = new HashMap<>(); // Param对应的java对象
//        Pdo2C pdo2C = new Pdo2C();
//        pdo2C.convert(outDir, "com.lepu.cardiot.comming.**.dto.**.*DTO", dtoJavaMap, dtoNSMap);
//        pdo2C.convert(outDir, "com.lepu.cardiot.comming.**.param.**.*Param", prmJavaMap, prmNSMap);
//        // 两个特殊的在bean下的对象
////        prmJavaMap.put(PageList.class.getSimpleName(), PageList.class.getTypeName());
////        dtoJavaMap.put(PageList.class.getSimpleName(), PageList.class.getTypeName());
//
//        // 转换Service
//        Properties properties = new Properties();
//        properties.put("serverAddr", "10.10.9.192:8848");
//        properties.put("namespace",  "36eb8707-cfdf-45c5-9ae0-77f12e7bf7e1"); // dev
////        properties.put("serverAddr", "192.168.22.198:8888");
////        properties.put("namespace",  "bb7f6291-3ca3-4fcb-a5f2-b004d06d7be1"); // test
//        try {
//            NamingService namingService = NacosFactory.createNamingService(properties);
//            Service2CForSwagger3 s2c = new Service2CForSwagger3(properties, outDir, dtoNSMap, dtoJavaMap, prmNSMap, prmJavaMap);
////            List<String> list = s2c.getServiceList();
//            List<String> list = namingService.getServicesOfServer(1, 100).getData(); // 2.x
//            list.forEach(sname -> {
//                try {
//                    List<Instance> ins = namingService.selectInstances(sname, true);
//                    if (!ins.isEmpty()) {
//                        s2c.getInstance(ins.get(0));
//                    }
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            });
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//}
