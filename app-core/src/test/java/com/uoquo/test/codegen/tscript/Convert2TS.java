/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.codegen.tscript;

import com.uoquo.test.codegen.ConvertInterface;
import com.uoquo.test.codegen.openapi.ServiceBean;
import com.uoquo.test.codegen.openapi.ServiceMethod;
import com.uoquo.test.codegen.openapi.ServiceProperty;
import com.uoquo.test.codegen.openapi.ServiceTag;
import com.uoquo.test.codegen.util.PkgUtils;
import com.uoquo.utils.FileUtil;
import com.uoquo.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 生成TS文件
 */
public class Convert2TS implements ConvertInterface {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static String NEW_LINE = "\r\n";
    private static String MODEL_PURE = "pure";
    private static String MODEL_ELMT = "elmt";
    private static String MODEL_UOQU = "uoqu";
    // URL前缀
    private String baseUrl;
    // 文件生成目录
    private String baseDir;
    // 待写入文件的Bean
    private Map<String, ServiceBean> beanMap;
    // 待写入文件的Service
    private Map<String, ServiceTag> tagsMap;
    // 已写入文件的Bean
    private List<String> writedBeanList;
    // 框架模式（vue、pure）
    private String model;

    public Convert2TS() {
        this(MODEL_ELMT);
    }
    public Convert2TS(String model) {
        if (MODEL_PURE.equals(model)) {
            this.model = MODEL_PURE;
        } else if(MODEL_UOQU.equals(model)) {
            this.model = MODEL_UOQU;
        } else {
            this.model = MODEL_ELMT;
        }
    }

    @Override
    public void convert(String baseUrl, String baseDir, Map<String, ServiceBean> beanMap, Map<String, ServiceTag> tagsMap) {
        this.baseUrl = baseUrl;
        this.baseDir = baseDir;
        this.beanMap = beanMap;
        this.tagsMap = tagsMap;

        this.convert();
    }


    private void convert() {
        tagsMap.forEach((key, tag) -> {
            if (tag.getServices().isEmpty()) {
                return;
            }
            // 1. 组装数据
            StringBuilder sb = new StringBuilder();
            String namespace = String.format("%sAPI", PkgUtils.firstChartToUpper(tag.getName()));
            // 组装头
            sb.append("import type { AxiosRequestConfig } from \"axios\";").append(NEW_LINE);
            if (MODEL_UOQU.equals(model)) {
                sb.append("import { http } from \"@/utils/http\";").append(NEW_LINE);
            } else if (MODEL_PURE.equals(model)) {
                sb.append("import { http } from \"@/utils/http\";").append(NEW_LINE);
            } else {
                sb.append("import request from \"@/utils/request\";").append(NEW_LINE);
            }
            sb.append("const USER_BASE_URL = \"").append(baseUrl).append("\";").append(NEW_LINE);
            sb.append(NEW_LINE);
            // 组装Service
            sb.append("/**").append(NEW_LINE);
            sb.append(" * ").append(tag.getDescription()).append(NEW_LINE);
            sb.append(" */").append(NEW_LINE);
            sb.append("const ").append(namespace).append(" = {").append(NEW_LINE);
            List<ServiceBean> beans = writeService(sb, tag.getServices());
            sb.append("};").append(NEW_LINE);
            sb.append(NEW_LINE);
            sb.append("export default ").append(namespace).append(";").append(NEW_LINE);
            sb.append(NEW_LINE);
            // 组装Bean
            writedBeanList = new ArrayList<>();
            writeBean(sb, beans);
            // 删除最后的换行符
            if (!beans.isEmpty()) {
                sb.delete(sb.length() - 2, sb.length());
            }
            // 2. 写入文件
            String fileName = String.format("%s/%s.ts", baseDir, tag.getName());
            FileUtil.write(new File(fileName), sb.toString().getBytes(StandardCharsets.UTF_8), false);
        });
    }

    private List<ServiceBean> writeService(StringBuilder sb, List<ServiceMethod> services) {
        List<ServiceBean> beans = new ArrayList<>();
        if (services.isEmpty()) {
            return beans;
        }
        services.sort(Comparator.comparing(ServiceMethod::getName));
        services.forEach(service -> {
            // 入参
            String requestBean = "";
            if (service.getRequestBean() != null) {
                requestBean = service.getRequestBean().getName();
                beans.add(service.getRequestBean());
            }
            ServiceBean requestParam = null;
            if (service.getRequestParam() != null) {
                requestParam = service.getRequestParam();
                beans.add(service.getRequestParam());
            }
            // 出参
            String returnBean = "";
            if (service.getResponseBean() != null) {
                returnBean = service.getResponseBean().getName();
                beans.add(service.getResponseBean());
            }
            sb.append("  /**").append(NEW_LINE);
            sb.append("   * ").append(service.getDescription()).append(NEW_LINE);
            if (StringUtil.notNull(requestBean)) {
                sb.append("   * @param data ").append(service.getRequestDescription()).append(NEW_LINE);
            }
            if (requestParam != null) {
                sb.append("   * @param param ").append(service.getRequestDescription()).append(NEW_LINE);
            }
            sb.append("   */").append(NEW_LINE);
            if (StringUtil.isNull(requestBean)) {
                sb.append("  ").append(service.getName()).append("(");
            } else {
                sb.append("  ").append(service.getName()).append("(data: ").append(requestBean).append(", ");
            }
            if (requestParam != null) {
                sb.append("param: ").append(requestParam.getName()).append(", ");
            }
            sb.append("config?: AxiosRequestConfig").append(") {").append(NEW_LINE);

            if (MODEL_UOQU.equals(model)) {
                writeService4Uoqu(sb, service, requestBean, requestParam, returnBean);
            } else if (MODEL_PURE.equals(model)) {
                writeService4Pure(sb, service, requestBean, requestParam, returnBean);
            } else {
                writeService4Elmt(sb, service, requestBean, requestParam, returnBean);
            }
            sb.append("  },").append(NEW_LINE).append(NEW_LINE);
        });
        // 删除最后的换行符
        sb.delete(sb.length() - 2, sb.length());
        return beans;
    }

    /**
     * vue3-element-admin
     */
    private void writeService4Elmt(StringBuilder sb, ServiceMethod service, String requestBean, ServiceBean requestParam, String returnBean) {
        if (StringUtil.notNull(service.getContentType()) && service.getContentType().contains("stream")) {
            // 文件下载的，返回参数为any
            sb.append("    return request<any>({").append(NEW_LINE);
        } else if (StringUtil.isNull(returnBean)) {
            // 无响应对象的，返回参数为any
            sb.append("    return request<any>({").append(NEW_LINE);
        } else if (returnBean.startsWith("List<")) {
            // springfox：List<RoleInfoDTO> --> RoleInfoDTO[]
            returnBean = returnBean.substring(5, returnBean.length() - 1);
            sb.append("    return request<any, ").append(formatType(returnBean)).append("[]>({").append(NEW_LINE);
        } else if (returnBean.startsWith("List")) {
            // springdoc：ListRoleInfoDTO --> RoleInfoDTO[]
            returnBean = returnBean.substring(4);
            sb.append("    return request<any, ").append(formatType(returnBean)).append("[]>({").append(NEW_LINE);
        } else {
            sb.append("    return request<any, ").append(formatType(returnBean)).append(">({").append(NEW_LINE);
        }
        sb.append("      url: `${USER_BASE_URL}").append(service.getUrl()).append(writePathParam(requestParam)).append("`,").append(NEW_LINE);
        sb.append("      method: \"").append(service.getMethod()).append("\",").append(NEW_LINE);
        if (StringUtil.notNull(requestBean)) {
            sb.append("      data: data,").append(NEW_LINE);
        }
        if (StringUtil.notNull(service.getContentType()) && service.getContentType().contains("stream")) {
            sb.append("      responseType: \"blob\",").append(NEW_LINE);
        }
        sb.append("    });").append(NEW_LINE);
    }

    /**
     * vue-pure-admin
     */
    private void writeService4Pure(StringBuilder sb, ServiceMethod service, String requestBean, ServiceBean requestParam, String returnBean) {
        if (StringUtil.notNull(service.getContentType()) && service.getContentType().contains("stream")) {
            // 文件下载的，返回参数为any
            sb.append("    return http.request<any>(");
        } else if (StringUtil.isNull(returnBean)) {
            // 无响应对象的，返回参数为any
            sb.append("    return http.request<any>(");
        } else if (returnBean.startsWith("List<")) {
            // springfox：List<RoleInfoDTO> --> RoleInfoDTO[]
            returnBean = returnBean.substring(5, returnBean.length() - 1);
            sb.append("    return http.request<").append(formatType(returnBean)).append("[]>(");
        } else if (returnBean.startsWith("List")) {
            // springdoc：ListRoleInfoDTO --> RoleInfoDTO[]
            returnBean = returnBean.substring(4);
            sb.append("    return http.request<").append(formatType(returnBean)).append("[]>(");
        } else {
            sb.append("    return http.request<").append(formatType(returnBean)).append(">(");
        }
        sb.append("\"").append(service.getMethod()).append("\", ");
        sb.append("`${USER_BASE_URL}").append(service.getUrl()).append(writePathParam(requestParam)).append("`");
        if (StringUtil.notNull(requestBean)) {
            sb.append(", {").append(NEW_LINE);
            sb.append("      data,").append(NEW_LINE);
            sb.append("    }");
        }
        if (StringUtil.notNull(service.getContentType()) && service.getContentType().contains("stream")) {
            sb.append(", {").append(NEW_LINE);
            sb.append("      responseType: \"blob\",").append(NEW_LINE);
            sb.append("    }");
        }
        sb.append(");").append(NEW_LINE);
    }

    /**
     * uoquo-cloud
     */
    private void writeService4Uoqu(StringBuilder sb, ServiceMethod service, String requestBean, ServiceBean requestParam, String returnBean) {
        if (StringUtil.notNull(service.getContentType()) && service.getContentType().contains("stream")) {
            // 文件下载的，返回参数为any
            sb.append("    return http.request<any>(");
        } else if (StringUtil.isNull(returnBean)) {
            // 无响应对象的，返回参数为any
            sb.append("    return http.request<any>(");
        } else if (returnBean.startsWith("List<")) {
            // springfox：List<RoleInfoDTO> --> RoleInfoDTO[]
            returnBean = returnBean.substring(5, returnBean.length() - 1);
            sb.append("    return http.request<").append(formatType(returnBean)).append("[]>(");
        } else if (returnBean.startsWith("List")) {
            // springdoc：ListRoleInfoDTO --> RoleInfoDTO[]
            returnBean = returnBean.substring(4);
            sb.append("    return http.request<").append(formatType(returnBean)).append("[]>(");
        } else {
            sb.append("    return http.request<").append(formatType(returnBean)).append(">(");
        }
        sb.append("\"").append(service.getMethod()).append("\", ");
        sb.append("`${USER_BASE_URL}").append(service.getUrl()).append(writePathParam(requestParam)).append("`");
        sb.append(", {").append(NEW_LINE);
        if (StringUtil.notNull(requestBean)) {
            sb.append("      data,").append(NEW_LINE);
        }
        sb.append("      ...config,").append(NEW_LINE);
        if (StringUtil.notNull(service.getContentType()) && service.getContentType().contains("stream")) {
            sb.append("      responseType: \"blob\",").append(NEW_LINE);
        }
        sb.append("    }");
        sb.append(");").append(NEW_LINE);
    }

    private String writePathParam(ServiceBean requestParam) {
        if (requestParam == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("?");
        requestParam.getProperties().forEach(property -> {
            sb.append(property.getName()).append("=").append("${param.").append(property.getName()).append("}").append("&");
        });
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private void writeBean(StringBuilder sb, List<ServiceBean> beans) {
        List<ServiceBean> subItems = new ArrayList<>();
        beans.sort(Comparator.comparing(ServiceBean::getName));
        beans.forEach(bean -> {
            if (bean.getName().startsWith("List<")){
                // springfox：List<RoleInfoDTO> --> RoleInfoDTO
                String beanName = bean.getName().substring(5, bean.getName().length() - 1);
                ServiceBean bean2 = beanMap.get(beanName);
                if (bean2 == null) {
                    throw new RuntimeException(String.format("[%s] 拆分为 [%s] 未找到对象", bean.getName(), beanName));
                }
                bean = bean2;
            } else if (bean.getName().startsWith("List")){
                // springdoc：ListRoleInfoDTO --> RoleInfoDTO
                String beanName = bean.getName().substring(4);
                ServiceBean bean2 = beanMap.get(beanName);
                if (bean2 == null) {
                    throw new RuntimeException(String.format("[%s] 拆分为 [%s] 未找到对象", bean.getName(), beanName));
                }
                bean = bean2;
            }
            if (bean.getProperties() == null || bean.getProperties().isEmpty()) {
                bean = beanMap.get(bean.getType());
                if (bean == null || bean.getProperties() == null || bean.getProperties().isEmpty()) {
                    return;
                }
            }
            // 已写入文件判断
            if (writedBeanList.contains(bean.getName())) {
                return;
            }
            writedBeanList.add(bean.getName());
            // 组装文件：Bean对象
            sb.append("/**").append(NEW_LINE);
            sb.append(" * ").append(bean.getDescription()).append(NEW_LINE);
            sb.append(" */").append(NEW_LINE);
            sb.append("export interface ").append(bean.getName()).append(" {").append(NEW_LINE);
            ServiceBean finalBean = bean;
            bean.getProperties().sort(Comparator.comparing(ServiceProperty::getName));
            bean.getProperties().forEach(field -> {
                sb.append("  /** ").append(field.getDescription()).append(" */").append(NEW_LINE);
                // 完全按照后台返回的require标识来判断是否必填
                sb.append("  ").append(field.getName()).append(field.isRequired() ? "" : "?").append(": ");
                if ("array".equals(field.getType())) {
                    logger.debug("[{}] 数组类型：{}", finalBean.getName(), field.getItems());
                    sb.append(formatType(field.getItems())).append("[];").append(NEW_LINE);
                    try {
                        List<ServiceBean> temp1 = beans.stream().filter(item -> item.getType().equals(field.getItems())).collect(Collectors.toList());
                        List<ServiceBean> temp2 = subItems.stream().filter(item -> item.getName().equals(field.getItems())).collect(Collectors.toList());
                        if (temp1.isEmpty() && temp2.isEmpty() && !writedBeanList.contains(field.getItems())) {
                            ServiceBean bean2 = beanMap.get(field.getItems());
                            if (bean2 == null) {
                                logger.warn("对象 [{}] 的数组属性 [{}] 未找到子对象 [{}]", finalBean.getName(), field.getName(), field.getItems());
                            } else {
                                subItems.add(bean2);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("", e);
                    }
                } else {
                    sb.append(formatType(field.getType())).append(";").append(NEW_LINE);
                }
            });
            sb.append("}").append(NEW_LINE);
            sb.append(NEW_LINE);
        });
        // 追加子对象
        if (!subItems.isEmpty()) {
            writeBean(sb, subItems);
        }
    }

    private String formatType(String type) {
        List<String> numberTypes = Arrays.asList("int", "integer", "long", "float", "double");
        if (numberTypes.contains(type.toLowerCase())) {
            return "number";
        } else if ("string".equalsIgnoreCase(type)) {
            return "string";
        } else  if ("boolean".equalsIgnoreCase(type)) {
            return "boolean";
        } else if ("void".contains(type.toLowerCase())) {
            return "void";
        }
        return type;
    }
}
