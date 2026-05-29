// /**
//  * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
//  * 注意：本内容仅限于内部传阅，禁止外泄
//  */

// package com.uoquo.web.common.annotation;

// import com.fasterxml.jackson.databind.util.JSONPObject;
// import com.uoquo.utils.StringUtil;
// import com.uoquo.utils.json.JsonUtil;
// import com.uoquo.web.utils.WebUtil;

// import java.lang.reflect.Array;
// import java.lang.reflect.GenericArrayType;
// import java.lang.reflect.ParameterizedType;
// import java.lang.reflect.Type;
// import java.lang.reflect.TypeVariable;
// import java.lang.reflect.WildcardType;

// import java.util.Collection;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;
// import jakarta.servlet.http.HttpServletRequest;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// import org.springframework.core.MethodParameter;
// import org.springframework.web.bind.support.WebDataBinderFactory;
// import org.springframework.web.context.request.NativeWebRequest;
// import org.springframework.web.method.support.HandlerMethodArgumentResolver;
// import org.springframework.web.method.support.ModelAndViewContainer;

// /**
//  * 描述：解析传入的json消息体. <br>
//  * <pre>
//  * 传入的消息如下格式：
//  * {
//  *   user : {name: 'userName',....},
//  *   group: {name: 'groupName',...}
//  * }
//  * 或者，传入的form表单如下格式：
//  * user.name=userName
//  * group.name=groupName
//  * 
//  * 在spring mvc的controller中定义方法：
//  * 
//  * public void saveUser(@RequestParam(value='user') User user, @RequestParam(value='group') Group group)
//  * 
//  * 则会将上述消息内容解析为user和group两个对象，其中
//  * user的name属性值为userName，
//  * group的name属性值为groupName
//  * </pre>
//  * 日期：2018-01-25 09:21 <br>
//  * 变更：
//  * <pre>
//  * Version      Date           ModifiedBy       Content
//  * --------     ----------     ------------     -----------------------
//  * 1.0          2018-01-25     xuhz.           创建
//  * </pre>
//  * @since   JDK 1.8
//  * @version 1.0
//  * @author  uoquo team
//  */
// public class RequestParamResolver implements HandlerMethodArgumentResolver {

//     private final Logger log = LoggerFactory.getLogger(RequestParamResolver.class);

//     private final Pattern patten = Pattern.compile("\"[A-Za-z0-9_\\-]+\":"); // 解析{"abc": {abc}}中的"abc":

//     /**
//      * 请求参数缓存
//      */
//     public final static String REQUEST_PARAMS_TEMP_KEY = "TEMP_REQUEST_PARAMS";
    
//     @Override
//     public boolean supportsParameter(MethodParameter parameter) {
//         return parameter.hasParameterAnnotation(RequestParam.class);
//     }

//     @Override
//     public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory)
//             throws Exception {
// 			//TODO 基础类从request中获取，对象不存在时返回null，而不是初始化对象
//         long bgn = System.currentTimeMillis();
//         mavContainer.setRequestHandled(true); // 设置这个就是最终的处理类了，处理完不再去找下一个类进行处理
//         RequestParam annotation = parameter.getParameterAnnotation(RequestParam.class);
//         // 需返回的对象
//         parameter = parameter.nestedIfOptional();
//         Type targetType = parameter.getNestedGenericParameterType();
//         Class<?> targetClass = null;
//         try {
//             targetClass = getRawType(targetType);
//         } catch (Exception e) {
//             targetClass = parameter.getNestedParameterType();
//         }
        
//         // 请求的内容
//         HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
//         // 不放入session，而是放入request的attruibute中，防止session在序列化时保存太多数据
//         JsonElement json = (JsonElement) request.getAttribute(REQUEST_PARAMS_TEMP_KEY);
//         if (json == null) {
//             json = getJson4Request(annotation, request);
//             request.setAttribute(REQUEST_PARAMS_TEMP_KEY, json); // cache request body content to request
//             log.debug("request all params:{}", json);
//             // in global interceptor.preHandle() session.removeAttribute("TEMP_JSON_BODY")
//         }
//         // 空值及JsonNull对象，则返回空对象。（目前仅支持request body传入的list数据，form表单方式传入的list无法处理）
//         if ((json == null) || json.isJsonNull()) {
//             logExcuteTime(bgn, "params is null");
//             return getDefaultValue(targetClass);
//         }
//         // 参数前缀
//         String paramPrefix = annotation.value();
//         // 没有指定参数前缀，从根开始解析对象
//         if (StringUtil.isNull(paramPrefix)) {
//             paramPrefix = parameter.getParameterName();
//             try {
//                 // 默认从根下开始解析
//                 Object temp = formatValue(paramPrefix, json, targetType, targetClass);
//                 if (temp != null) {
//                     if (temp instanceof BaseEntity) {
//                         if (((BaseEntity)temp).isNull()) {
//                             throw new Exception("parse object is null");
//                         }
//                     }
//                     return temp;
//                 }
//             } catch (Exception e) {
//                 // do nothing
//             } finally {
//                 logExcuteTime(bgn, "parse from root");
//             }
//         }
//         // 获取指定前缀的参数对象
//         JsonElement temp = ((JsonObject) json).get(paramPrefix);
//         if (temp != null) {
//             try {
//                 logExcuteTime(bgn, "parse from prefix: " + paramPrefix);
//                 return formatValue(paramPrefix, temp, targetType, targetClass);
//             } catch (Exception e) {
//                 log.error("parse json error. param_clz={}, param_key={}, data={}", targetType, paramPrefix, temp, e);
//             }
//         }
//         // 所有都处理失败，则返回空对象
//         logExcuteTime(bgn, "no params");
//         return getDefaultValue(targetClass);
//     }
    
//     /**
//      * 格式化传入值.<br>
//      * 注：主要处理feign参数为null时，不替换自定义变量的问题
//      * @param key  变量名
//      * @param temp 目前对象
//      * @param type 目标类型（包含内部泛型类）
//      * @param claz 目标class（仅外部类）
//      * @return 参数值
//      * @throws Exception  异常信息
//      */
//     private Object formatValue(String key, JsonElement temp, Type type, Class<?> claz) throws Exception {
//         // 处理feign参数为null时，不替换自定义变量的问题
//         String tempKey = "{" + key + "}";
//         String tempVal = "";
//         try {
//             tempVal = temp.getAsString();
//         } catch (Exception e) {
//             // do nothing
//         }
//         if (tempKey.equals(tempVal) || "null".equals(tempVal)) {
//             return getDefaultValue(claz);
//         }
//         // 解析数据
//         Object obj = JsonUtil.deserialize(temp, type);
//         if ((obj == null) && BaseEntity.class.isAssignableFrom(claz)) {
//             return claz.newInstance(); // uoquo自定义对象返回空实例，便于service层拼接查询条件
//         } else {
//             return obj;
//         }
//     }
    
//     /**
//      * 获取默认值.<br>
//      * @param claz 目标class
//      * @return 默认值
//      * @throws InstantiationException 异常信息
//      * @throws IllegalAccessException 异常信息
//      */
//     private Object getDefaultValue(Class<?> claz) throws InstantiationException, IllegalAccessException {
//         if (claz.isPrimitive()) {
//             // 基础类型
//             if (char.class.isAssignableFrom(claz)) {
//                 return Character.MIN_VALUE;
//             } else {
//                 return 0;
//             }
// //        } else if (claz.isAssignableFrom(Date.class) 
// //                || Number.class.isAssignableFrom(claz) 
// //                || Boolean.class.isAssignableFrom(claz)
// //                || Character.class.isAssignableFrom(claz)
// //                || CharSequence.class.isAssignableFrom(claz)) {
// //            return null; // 返回null
//         } else if (BaseEntity.class.isAssignableFrom(claz)) {
//             return claz.newInstance(); // uoquo自定义对象返回空实例，便于service层拼接查询条件
//         } else {
//             return null; // 其他对象都返回null
//         }
//     }
    
//     /**
//      * 获取传入的所有参数信息. <br>
//      * @param request HttpServletRequest对象
//      * @return 拼接为json的消息体
//      */
//     @SuppressWarnings("deprecation")
//     private JsonElement getJson4Request(RequestParam annotation, HttpServletRequest request) {
//         long bgn = System.nanoTime();
//         // 读取传入的form表单数据
//         // 注：必须先读取form表单数据，因为x-www-form-urlencoded也会采用body方式传输，所以先通过form表单获取，避免乱码情况
//         // 当文件上传时，form-data也会采用body传输，此时读取form后，底层会将数据缓存到map中，此时可以多次使用
//         JsonElement formJson = getJson4Form(annotation, request);
//         logExcuteTime(bgn, "get request form.");
//         log.debug("request form:{}", formJson);
//         // 获取消息体的内容
//         JsonElement bodyJson = null;
//         if (annotation.readBody()) {
//             // 此时主要是提交的json消息体
//             bodyJson = getJson4Body(annotation, request);
//         }
//         logExcuteTime(bgn, "get request body.");
//         log.debug("request body:{}", bodyJson);
//         // 没有消息体，则直接返回form表单数据
//         if ((bodyJson == null) || bodyJson.isJsonNull()) {
//             return formJson;
//         }
//         // 没有form表单，则直接返回消息体
//         if ((formJson == null) || formJson.isJsonNull()) {
//             return bodyJson;
//         }
//         // 两个都有时，则合并json和json2
//         return JsonUtil.merge(formJson, bodyJson);
//     }

//     /**
//      * 读取传入的消息体. <br>
//      * @param request HttpServletRequest对象
//      * @return 消息体
//      */
//     private JsonElement getJson4Body(RequestParam annotation, HttpServletRequest request) {
//         long bgn = System.nanoTime();
//         // 读取内容
//         String bodyStr = WebUtil.getRequestBody(request);
//         logExcuteTime(bgn, "read request body/form.");
//         if (StringUtil.isNull(bodyStr)) {
//             return JsonNull.INSTANCE;
//         }
//         // 解析内容
//         try {
//             // TODO 替换{"abc": {abc}} 格式的内容为{"abc": null}，注：该操作会替换字串中所有满足条件的数据！
//             // 方案1：直接替换所有的{XXXX}为null
//             // bodyStr = bodyStr.replaceAll("\\{[A-Za-z0-9_\\-]+\\}", "null");
//             // 方案2：根据JSON的key替换{XXXX}为null
//             Matcher m = patten.matcher(bodyStr);
//             while (m.find()) {
//                 String key = m.group();
//                 String temp = key.substring(1, key.length() - 2);
//                 bodyStr = bodyStr.replaceAll("\\{" + temp + "\\}", "null");
//             }
//             // 默认按json解析ContentType为 text/plain 的数据
//             return JsonUtil.deserialize(bodyStr, JsonElement.class);
//         } catch (Exception e) {
//             // 解析ContentType为 application/x-www-form-urlencoded 的数据
//             // TODO 暂时不支持form表单方式传入的list数据
//             boolean flag = false;
//             JsonObject json = new JsonObject();
//             String[] kvs = bodyStr.split("&");
//             for (String kv : kvs) {
//                 if (StringUtil.isNull(kv)) {
//                     continue;
//                 }
//                 flag = true;
//                 String[] temp = kv.split("=", 2);
//                 putInObject(annotation, json, temp[0], temp[1]);
//             }
//             logExcuteTime(bgn, "parse request form to json.");
//             return flag ? json : JsonNull.INSTANCE;
//         } finally {
//             logExcuteTime(bgn, "parse request body/form to json.");
//         }
//     }

//     /**
//      * 读取传入的Form表单. <br>
//      * @param request HttpServletRequest对象
//      * @return 消息体
//      */
//     private JsonElement getJson4Form(RequestParam annotation, HttpServletRequest request) {
//         boolean flag = false;
//         JsonObject json = new JsonObject();
//         Collection<String> keySet = request.getParameterMap().keySet();
//         for (String key : keySet) {
//             flag = true;
//             String val = request.getParameter(key);
//             // TODO 暂时不支持form表单方式传入的list数据
//             if (StringUtil.isNull(val)) {
//                 putInObject(annotation, json, key, JsonNull.INSTANCE);
//             } else {
//                 try {
//                     JsonElement temp;
//                     if (val.startsWith("#")) {
//                         // GSON 的JsonReader会将“#”开头的字符串过滤（在json中#为注释语句，所以gson会将#开头的字符串整行忽略）
//                         // 所以，需要针对该值单独处理
//                         temp = new JsonPrimitive(val);
//                     } else {
//                         temp = JsonUtil.deserialize(val, JsonElement.class);
//                     }
//                     putInObject(annotation, json, key, temp);
//                 } catch (Exception e) {
//                     putInObject(annotation, json, key, val);
//                 }
//             }
            
//         }
//         // 没有值，则返回空json
//         return flag ? json : JsonNull.INSTANCE;
//     }

//     private JsonObject putInObject(RequestParam annotation, JsonObject json, String key, String value) {
//         JsonElement temp = new JsonPrimitive(value);
//         return putInObject(annotation, json, key, temp);
//     }
    
//     /**
//      * 将参数递归放入json对象中.
//      * @param json  当前json对象
//      * @param key   键
//      * @param value 值
//      * @return 放入键值后的json对象
//      */
//     private JSONPObject putInObject(RequestParam annotation, JsonObject json, String key, JsonElement value) {
//         if (StringUtil.isNull(annotation.split())) {
//             json.add(key, value);
//             return json;
//         }
//         String[] temp = key.split(annotation.split(), 2);
//         if (temp.length == 1) {
//             json.add(key, value);
//         } else {
//             JsonObject sub = (JsonObject)json.get(temp[0]);
//             if (sub == null) {
//                 sub = new JsonObject();
//             }
//             sub = putInObject(annotation, sub, temp[1], value);
//             json.add(temp[0], sub);
//         }
//         return json;
//     }
    
//     /**
//      * 记录执行时间. <br>
//      * @param bgn 起始时间
//      * @param cmd 命令名称
//      */
//     private void logExcuteTime(long bgn, String cmd) {
//         if (log.isDebugEnabled()) {
//             long end = System.currentTimeMillis();
//             String runTime = String.format("%.3fs", (end - bgn) / 1_000F);
//             log.debug("uoquo parse request params. excute time = {}, cmd = {}", runTime, cmd);
//         }
//     }
    
//     /**
//      * 获取参数的class.<br>
//      * 参考：{@link com.google.gson.internal.$Gson$Types#getRawType(Type)}
//      * @param type 类型
//      * @return
//      */
//     public static Class<?> getRawType(Type type) {
//         if (type instanceof Class<?>) {
//             // type is a normal class.
//             return (Class<?>) type;
            
//         } else if (type instanceof ParameterizedType) {
//             ParameterizedType parameterizedType = (ParameterizedType) type;
//             // I'm not exactly sure why getRawType() returns Type instead of Class.
//             // Neal isn't either but suspects some pathological case related
//             // to nested classes exists.
//             Type rawType = parameterizedType.getRawType();
//             if (rawType instanceof Class) {
//                 return (Class<?>) rawType;
//             }
//         } else if (type instanceof GenericArrayType) {
//             Type componentType = ((GenericArrayType)type).getGenericComponentType();
//             return Array.newInstance(getRawType(componentType), 0).getClass();
            
//         } else if (type instanceof TypeVariable) {
//             // we could use the variable's bounds, but that won't work if there are multiple.
//             // having a raw type that's more general than necessary is okay
//             return Object.class;
            
//         } else if (type instanceof WildcardType) {
//             return getRawType(((WildcardType) type).getUpperBounds()[0]);
//         }
//         // 其他则抛异常
//         String className = type == null ? "null" : type.getClass().getName();
//         throw new IllegalArgumentException("Expected a Class, ParameterizedType, or "
//             + "GenericArrayType, but <" + type + "> is of type " + className);
//     }
// }