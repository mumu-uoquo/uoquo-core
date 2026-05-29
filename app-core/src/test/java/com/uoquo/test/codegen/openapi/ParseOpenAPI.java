/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.codegen.openapi;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.uoquo.test.codegen.util.PkgUtils;
import com.uoquo.utils.FileUtil;
import com.uoquo.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ParseOpenAPI {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private Map<String, ServiceBean> beanMap;

    private Map<String, ServiceTag> tagsMap;

    public ParseOpenAPI(){
        this.beanMap = null;
        this.tagsMap = null;
    }

    public void parse(String basePath, String filePath) {
        JSONObject json = null;
        if (filePath.startsWith("http")) {
            json = getApiDocs4Http(filePath);
        } else {
            json = getApiDocs4File(filePath);
        }
        if (json == null || json.getJSONObject("info") == null) {
            throw new RuntimeException("无OpenAPI文档");
        }
        parse(basePath, json);
    }

    public void parse(String basePath, String host, int port) {
        JSONObject json = getApiDocs4Http(host, port, basePath);
        if (json == null || json.getJSONObject("info") == null) {
            throw new RuntimeException("无OpenAPI文档");
        }
        parse(basePath, json);
    }

    public Map<String, ServiceBean> getBeanMap() {
        return beanMap;
    }

    public Map<String, ServiceTag> getTagsMap() {
        return tagsMap;
    }

    private void parse(String basePath, JSONObject json) {
        // 文件上传下载的接口不暴露给客户端
        if (basePath.startsWith("/api/dfs")) {
            return;
        }
        // 1. 解析其中的参数
        parseParam(json.getJSONObject("components").getJSONObject("schemas"));
        // 2. 解析标签
        parseTags(json.getJSONArray("tags"));
        // 3. 解析路径
        parsePath(basePath, json.getJSONObject("paths"));
        // 排序
        tagsMap.values().forEach(item -> {
            item.getServices().sort(Comparator.comparing(ServiceMethod::getName));
        });
    }

    /**
     * 参数解析
     * root -> components -> schemas
     */
    private void parseParam(JSONObject json) {
        beanMap = new HashMap<>();
        json.forEach((key, v) -> {
            // eg: PageResult«AgentDTO» --> PageResult<AgentDTO>
            key = key.replaceAll("«", "<").replaceAll("»", ">");
            logger.debug("解析参数：{}", key);
            JSONObject val = (JSONObject) v;
            List<String> requiredProps = new ArrayList<>();
            if (val.getJSONArray("required") != null) {
                val.getJSONArray("required").forEach(item -> {
                    requiredProps.add((String) item);
                });
            }
            logger.debug("{}必须参数：{}", key, requiredProps);
            ServiceBean bean = new ServiceBean();
            bean.setName(key);
            //bean.setName(val.getString("title"));
            bean.setType(val.getString("type"));
            bean.setDescription(val.getString("description"));
            bean.setProperties(new ArrayList<>());
            val.getJSONObject("properties").forEach( (proKey, proVal) -> {
                ServiceProperty p1 = parseProperty(proKey, (JSONObject) proVal);
                if (requiredProps.contains(proKey)) {
                    p1.setRequired(true);
                }
                bean.getProperties().add(p1);
            });
            bean.getProperties().sort(Comparator.comparing(ServiceProperty::getName));
            beanMap.put(key, bean);
        });
    }

    /**
     * 标签解析
     * root -> tags
     */
    private void parseTags(JSONArray json) {
        tagsMap = new HashMap<>();
        if (json == null) {
            return ;
        }
        json.forEach(item -> {
            // TODO 可考虑转拼音
            String tagName = ((JSONObject) item).getString("name");
            String description = ((JSONObject) item).getString("description");
            logger.debug("解析标签：{}", tagName);
            ServiceTag tag = tagsMap.computeIfAbsent(tagName, k -> {
                ServiceTag temp = new ServiceTag();
                temp.setName(tagName);
                temp.setServices(new ArrayList<>());
                return temp;
            });
            if (StringUtil.isNull(tag.getDescription())) {
                tag.setDescription(description);
            } else if (StringUtil.notNull(description)) {
                tag.setDescription(tag.getDescription() + "、" + description);
            }
        });
    }

    /**
     * 路径解析
     * root -> paths
     */
    private void parsePath(String basePath, JSONObject json) {
        if (json == null) {
            return ;
        }

        json.forEach((k, v) -> {
            try {
                this.parsePath(basePath, k, v);
            } catch (Exception e) {
                logger.error("解析路径失败：{}", k, e);
            }
        });
    }

    private void parsePath(String basePath, String k, Object v ) {
        if (k.contains("download") || k.contains("transfer")) {
            logger.debug("debug");
        }
        String url = k.startsWith(basePath) ? k.substring(basePath.length()) : k;
        // 只处理 get 或 post
        String method = "get";
        JSONObject val = ((JSONObject)v).getJSONObject(method);
        if (val == null) {
            method = "post";
            val = ((JSONObject)v).getJSONObject(method);
        }
        if (val == null) {
            logger.warn("[{}]没有get和post方法，跳过.", url);
            return;
        }
        // 分类（TODO 可考虑转拼音）
        String tagName = val.getJSONArray("tags").getString(0);
        ServiceTag tag = tagsMap.computeIfAbsent(tagName, item -> {
            ServiceTag temp = new ServiceTag();
            temp.setName(tagName);
            temp.setServices(new ArrayList<>());
            return temp;
        });
        ServiceMethod info = new ServiceMethod();
        // 请求地址
        info.setTags(tagName);
        info.setUrl(url);
        info.setMethod(method);
        // 方法名（无指定时，将URL转方法名）
        String operationId = val.getString("operationId");
        if (StringUtil.isNull(operationId)) {
            StringBuffer fileName = new StringBuffer();
            String[] strs = url.split("/");
            for (int i = 0; i < strs.length; i++) {
                String[] fns = strs[i].split("_");
                for (int j = 0; j < fns.length; j++) {
                    if ("".equalsIgnoreCase(fns[j])) {
                        continue;
                    }
                    fileName.append(PkgUtils.firstChartToUpper(fns[j]));
                }
            }
            operationId = fileName.toString();
        }
        info.setName(operationId);
        // 方法备注
        String desc = val.getString("description");
        if (StringUtil.isNull(desc)) {
            desc = val.getString("summary");
        }
        info.setDescription(desc);

        // 入参：请求体
        JSONObject reqObj = val.getJSONObject("requestBody");
        if (reqObj != null) {
            reqObj.getJSONObject("content").forEach((reqKey, reqVal) -> {
                JSONObject schema = ((JSONObject) reqVal).getJSONObject("schema");
                String ref  = (schema == null) ? null : schema.getString("$ref");
                String type = (schema == null) ? null : schema.getString("type");
                if (reqKey.contains("stream")){
                    // 优先处理二进制流
                    ServiceBean param = new ServiceBean();
                    param.setName("Blob");
                    param.setType("Blob");
                    logger.warn("[{}][{}]的入参为二进制流 ", url, reqKey);
                    info.setRequestBean(param);
                } else if (StringUtil.notNull(ref)) {
                    // eg: #/components/schemas/ReturnData«PageResult«AgentDTO»»
                    String beanName = ref.substring(ref.lastIndexOf("/") + 1);
                    logger.debug("[{}]的入参[{}] ", url, beanName);
                    info.setRequestBean(beanMap.get(beanName));
                } else if (StringUtil.notNull(type)) {
                    ref = ((JSONObject) reqVal).getJSONObject("schema").getJSONObject("items").getString("$ref");
                    String beanName = ref.substring(ref.lastIndexOf("/") + 1);
                    ServiceBean param = new ServiceBean();
                    param.setName("array".equals(type) ? beanName + "[]" : beanName);
                    param.setType(beanName);
                    logger.warn("[{}]没有$ref信息.", url);
                    info.setRequestBean(param);
                } else {
                    logger.warn("[{}]没有requestBody信息.", url);
                }
            });
            info.setRequestDescription(reqObj.getString("description"));
            if(StringUtil.isNull(info.getRequestDescription()) && info.getRequestBean() != null) {
                info.setRequestDescription(info.getRequestBean().getDescription());
            }
        }
        // 入参：FORM表单或者PATH参数
        if (val.getJSONArray("parameters") != null) {
            // 首字母要大写
            String name = info.getName() + "Param";
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            ServiceBean param = new ServiceBean();
            param.setName(name);
            param.setType("object");
            param.setProperties(new ArrayList<>());
            val.getJSONArray("parameters").forEach(item -> {
                if (!"query".equals(((JSONObject) item).getString("in"))) {
                    logger.info("[{}]非 query 入参[{}] ", url, item);
                    return;
                }
                ServiceProperty p1 = new ServiceProperty();
                p1.setName(((JSONObject) item).getString("name"));
                p1.setDescription(((JSONObject) item).getString("description"));
                p1.setType(((JSONObject) item).getJSONObject("schema").getString("type"));
                param.getProperties().add(p1);
            });
            logger.debug("[{}]的入参[{}] ", url, param.getName());
            if (info.getRequestBean() == null && !"get".equals(method)) {
                info.setRequestBean(param);
            } else {
                info.setRequestParam(param);
            }
            // 第一个参数的备注作为入参描述
            String description = param.getProperties().isEmpty() ? null : param.getProperties().getFirst().getDescription();
            param.setDescription(description);
            if (StringUtil.isNull(info.getRequestDescription())) {
                info.setRequestDescription(description);
            }
            if (StringUtil.isNull(info.getDescription())) {
                info.setDescription(description);
            }
        }
        if (info.getRequestBean() == null){
            logger.warn("[{}]没有requestBody和parameters入参信息.", url);
        }

        // 出参（只取一个）
        Set<String> keys = val.getJSONObject("responses").keySet();
        if (keys.isEmpty()) {
            logger.warn("[{}]没有具体的【responses】信息.", url);
            return;
        }
        JSONObject resObj = null;
        if (keys.contains("200")) {
            resObj = val.getJSONObject("responses").getJSONObject("200").getJSONObject("content");
        } else if (keys.contains("default")) {
            resObj = val.getJSONObject("responses").getJSONObject("default").getJSONObject("content");
        } else {
            resObj = val.getJSONObject("responses").getJSONObject(keys.iterator().next()).getJSONObject("content");
        }
        if (resObj == null) {
            // 无返回参数时（如文件下载）
            tag.getServices().add(info);
            return;
        }
        AtomicBoolean stopLoop = new AtomicBoolean(false);
        resObj.forEach((resKey, resVal) -> {
            if (stopLoop.get()) {
                logger.warn("[{}]有多条出参，已忽略[{}] [{}].", url, resKey, resVal);
                return;
            }
            info.setContentType(resKey);
            if (resKey.contains("stream")){
                // 优先处理二进制流
                ServiceBean bean = new ServiceBean();
                bean.setName("Blob");
                bean.setType("Blob");
                logger.warn("[{}][{}]的出参为二进制流 ", url, resKey);
                info.setResponseBean(bean);
                stopLoop.set(true);
                return;
            }

            if (((JSONObject) resVal).getJSONObject("schema") == null) {
                logger.warn("[{}] [{}] 没有具体的【schema】信息.", url, resKey);
                return;
            }
            String beanName = null;
            String ref = ((JSONObject) resVal).getJSONObject("schema").getString("$ref");
            if (StringUtil.isNull(ref)) {
                beanName = ((JSONObject) resVal).getJSONObject("schema").getString("type");
//                beanName = ((JSONObject) resVal).getJSONObject("items").getString("type");
            } else {
                // eg: #/components/schemas/ReturnData«PageResult«AgentDTO»»
                beanName = ref.substring(ref.lastIndexOf("/") + 1);
            }
            if (StringUtil.isNull(beanName)) {
                logger.warn("[{}] response 没有$ref和type信息.", url);
                return;
            }
            if (beanName.indexOf("«") > 0) {
                // springfox：ReturnData«PageResult«AgentDTO»»  --> PageResult«AgentDTO»
                beanName = beanName.substring(beanName.indexOf("«") + 1, beanName.length() - 1);
            } else if (beanName.startsWith("ReturnData")) {
                // springdoc：ReturnDataPageResultInstituteInfoDto  --> PageResultInstituteInfoDto
                beanName = beanName.substring("ReturnData".length());
            }
            // PageResult«AgentDTO» --> PageResult<AgentDTO>
            beanName = beanName.replaceAll("«", "<").replaceAll("»", ">");
            ServiceBean bean = beanMap.get(beanName);
            if (bean == null) {
                logger.warn("[{}]的出参[{}]未找到对应的Bean对象.", url, beanName);
                // springfox如：beanName == string、integer、List<RoleInfoDTO>、PageResult<TaskInfoDTO>等
                // springdoc如：beanName == String、Integer、ListRoleInfoDTO、PageResultTaskInfoDTO等
                List<String> primitiveTypes = Arrays.asList("string", "int", "integer", "long", "float", "double");
                if (primitiveTypes.contains(beanName.toLowerCase())) {
                    beanName = beanName.toLowerCase();
                }
                bean = new ServiceBean();
                bean.setName(beanName);
                bean.setType(beanName);
            }
            logger.debug("[{}] [{}] 的出参[{}]", url, resKey, beanName);
            info.setResponseBean(bean);
            // 跳出foreach
            stopLoop.set(true);
        });
        tag.getServices().add(info);
    }

    /**
     * 对象属性解析
     */
    private ServiceProperty parseProperty(String name, JSONObject json) {
        ServiceProperty bean = new ServiceProperty();
        bean.setName(name);
        bean.setType(json.getString("type"));
        bean.setDescription(json.getString("description"));
        if ("array".equals(bean.getType())) {
            String ref = json.getJSONObject("items").getString("$ref");
            String beanName = null;
            if (StringUtil.isNull(ref)) {
                beanName = json.getJSONObject("items").getString("type");
            } else {
                // eg: #/components/schemas/AgentDTO
                beanName = ref.substring(ref.lastIndexOf("/")+1);
            }
            bean.setItems(beanName);
        }
        return bean;
    }

    /**
     * 获取 OpenAPI文档
     */
    private JSONObject getApiDocs4File(String filePath) {
        byte[] bytes = null;
        try {
            bytes = FileUtil.read(new File(filePath));
        } catch (Exception ex) {
            logger.error("read file error: {}", filePath, ex);
            return null;
        }
        String content = new String( bytes );
        return JSONObject.parseObject(content);
    }

    /**
     * 获取服务的swagger内容
     */
    private JSONObject getApiDocs4Http(String host, int port, String basePath) {
        basePath = (basePath == null) ? "" : basePath;
        String urlStr = String.format("http://%s:%d%s/v2/api-docs?group=RestfulApi", host, port, basePath);
        return getApiDocs4Http(urlStr);
    }

    private JSONObject getApiDocs4Http(String urlStr) {
        InputStream resIn = null;
        BufferedReader resBf = null;
        HttpURLConnection httpcon = null;
        logger.info("request: {}", urlStr);
        try {
            // 发送URL请求
            URL url = new URL(urlStr);
            httpcon = (HttpURLConnection) url.openConnection();
            httpcon.setDoOutput(true);
            httpcon.setDoInput(true);
            httpcon.setUseCaches(false);
            httpcon.setInstanceFollowRedirects(true);
            httpcon.setRequestProperty("Content-Type","application/json");
            httpcon.setRequestMethod("GET");
            httpcon.setConnectTimeout(30 * 1000);
            httpcon.setReadTimeout(30 * 1000);
            httpcon.connect();
            // 获取响应内容
            int code = httpcon.getResponseCode();
            if (code == 200) {
                resIn = httpcon.getInputStream();
            } else {
                resIn = httpcon.getErrorStream();
            }
            resBf = new BufferedReader(new InputStreamReader(resIn));
            StringBuffer buffer = new StringBuffer();
            String line = "";
            while ((line = resBf.readLine()) != null) {
                buffer.append(line);
            }
            // 解析响应内容
            String jsonStr = buffer.toString();
//            jsonStr = StringUtils.replace(jsonStr, "$ref", "ref");
            jsonStr = StringUtils.replace(jsonStr, "#/definitions/", "");
            return JSONObject.parseObject(jsonStr);
        } catch (Exception ex) {
            logger.error("request: {}", urlStr, ex);
            return null;
        } finally {
            httpcon.disconnect();
            close(resBf);
            close(resIn);
        }
    }

    private void close(Closeable obj) {
        if (obj != null) {
            try {
                obj.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
