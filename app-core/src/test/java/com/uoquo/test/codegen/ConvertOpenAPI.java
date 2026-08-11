/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.codegen;


import com.uoquo.test.codegen.openapi.ParseOpenAPI;
import com.uoquo.test.codegen.tscript.Convert2TS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * openapi 生成代码
 */
public class ConvertOpenAPI {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private String outDir;
    private ConvertInterface converter;

    public static void main(String[] args) {
        ConvertOpenAPI convert = new ConvertOpenAPI();
        // 生成 TypeScript 接口代码
//        String model = "elmt"; // vue3-element-admin
//        String model = "pure"; // vue-pure-admin
        String model = "uoqu";
        convert.converter = new Convert2TS(model);

        // health
        convert.parseFile("D:/temp/health/platform/" + model, "/health/api/platform", "https://api.uoquo.com/health/api/platform/v3/api-docs/AdminApi");
        convert.parseFile("D:/temp/health/platform/" + model, "/health/api/platform", "https://api.uoquo.com/health/api/platform/v3/api-docs/RestfulApi");
        convert.parseFile("D:/temp/health/operate/"  + model, "/health/api/operate",  "https://api.uoquo.com/health/api/operate/v3/api-docs/RestfulApi");
//        // bimi
//        convert.parseFile("D:/temp/platform", "/api/platform", "https://bimi.uoquo.com/api/platform/v3/api-docs/AdminApi");
//        convert.parseFile("D:/temp/platform", "/api/platform", "https://bimi.uoquo.com/api/platform/v3/api-docs/RestfulApi");
//        convert.parseFile("D:/temp/merchant", "/api/merchant", "https://bimi.uoquo.com/api/merchant/v3/api-docs/RestfulApi");

//        convert.parseNacos();
    }


    public void parseFile(String outDir, String baseUrl, String filePath) {
        this.outDir = outDir;
        ParseOpenAPI parseOpenAPI = new ParseOpenAPI();
        parseOpenAPI.parse(baseUrl, filePath);
        this.converter.convert(baseUrl, outDir, parseOpenAPI.getBeanMap(), parseOpenAPI.getTagsMap());
    }

//    public void parseNacos() {
//        Properties properties = new Properties();
//        properties.put("serverAddr", "192.168.22.205:8848");
//        properties.put("namespace",  "36eb8707-cfdf-45c5-9ae0-77f12e7bf7e1");
//        properties.put("username", "");
//        properties.put("password", "");
//        try {
//            NamingService namingService = NacosFactory.createNamingService(properties);
////            List<String> list = s2c.getServiceList(); // 1.x
//            List<String> list = namingService.getServicesOfServer(1, 100).getData(); // 2.x
//            list.forEach(sname -> {
//                try {
//                    List<Instance> ins = namingService.selectInstances(sname, true);
//                    if (ins.isEmpty()) {
//                        logger.warn("服务[{}]无可用实例", sname);
//                        return;
//                    }
//                    Instance instance = ins.get(0);
//                    String baseUrl = instance.getMetadata().get("basePath");
//                    logger.info("开始转换[{}][http://{}:{}{}/v2/api-docs?group=RestfulApi]", sname, instance.getIp(), instance.getPort(), baseUrl);
//                    // 解析
//                    ParseOpenAPI parseOpenAPI = new ParseOpenAPI();
//                    parseOpenAPI.parse(baseUrl, instance.getIp(), instance.getPort());
//                    // 保存
//                    this.converter.convert(baseUrl, outDir, parseOpenAPI.getBeanMap(), parseOpenAPI.getTagsMap());
//                    logger.info("转换完成[{}]", sname);
//                } catch (Exception e) {
//                    logger.error("转换失败[{}]", sname, e);
//                }
//            });
//        } catch (Exception e) {
//            logger.error("转换失败[{}]", properties, e);
//        }
//    }
}
