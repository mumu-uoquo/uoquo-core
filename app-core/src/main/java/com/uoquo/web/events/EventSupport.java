/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import com.uoquo.utils.StringUtil;
import com.uoquo.utils.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 事件相关的公共工具方法（供 {@code AppEvent}、{@code RemoteEvent} 及各监听适配器共用）。
 *
 * <p>主要解决三类问题：</p>
 * <ol>
 *   <li>{@code dataType} 命名规范化：嵌套类（{@code Outer$Inner}）、带泛型参数（{@code java.util.List<com.x.Foo>}）</li>
 *   <li>监听方法声明的泛型类型与事件实际数据的匹配</li>
 *   <li>日志链路标识（MDC requestId）的拼装，避免出现 {@code null-xxx}</li>
 * </ol>
 *
 * @author xuhz
 */
public final class EventSupport {
    private static final Logger log = LoggerFactory.getLogger(EventSupport.class);

    private EventSupport() {
    }

    /* ======================== dataType 命名 ======================== */

    /**
     * 获取泛型实参对应的类型名称（用于写入事件的 {@code dataType}）。<br>
     * 带泛型参数的类型（如 {@code List<Foo>}）只取原始类型名，避免写入 {@code java.util.List<com.x.Foo>} 这类无法加载的名称。
     * @param type 泛型实参
     * @return 类型名称
     */
    public static String typeNameOf(Type type) {
        if (type instanceof ParameterizedType) {
            return typeNameOf(((ParameterizedType) type).getRawType());
        }
        return type.getTypeName();
    }

    /**
     * 提取简单类名：去掉泛型参数、包名及外部类前缀。<br>
     * 例如：{@code com.a.Outer$Inner} → {@code Inner}；{@code java.util.List<com.x.Foo>} → {@code List}
     * @param typeName 类型名称（可为空）
     * @return 简单类名；入参为空时返回 null
     */
    public static String simpleName(String typeName) {
        if (typeName == null) {
            return null;
        }
        String name = typeName.trim();
        int generic = name.indexOf('<');
        if (generic >= 0) {
            name = name.substring(0, generic);
        }
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        int dollar = name.lastIndexOf('$');
        if (dollar >= 0) {
            name = name.substring(dollar + 1);
        }
        return name;
    }

    /**
     * 去掉类型名称中的泛型参数部分，保留可被类加载器加载的原始类型名。
     * @param typeName 类型名称（可为空）
     * @return 原始类型名称；入参为空时返回 null
     */
    public static String rawTypeName(String typeName) {
        if (typeName == null) {
            return null;
        }
        String name = typeName.trim();
        int generic = name.indexOf('<');
        return generic >= 0 ? name.substring(0, generic) : name;
    }

    /* ======================== 监听方法的泛型匹配 ======================== */

    /**
     * 解析监听方法第一个参数中事件的泛型实参类型。<br>
     * 仅当参数类型恰好是 {@code eventClass}（非子类）且泛型实参为具体类型（非 {@code ?}、非 Object）时返回，否则返回 null。
     * @param method     监听方法
     * @param eventClasses 需要识别的事件基类（如 AppEvent、RemoteEvent）
     * @return 声明的数据类型；无法确定时返回 null（表示不过滤）
     */
    public static Class<?> declaredDataClass(Method method, Class<?>... eventClasses) {
        if (method == null || method.getParameterCount() == 0) {
            return null;
        }
        ResolvableType parameterType = ResolvableType.forMethodParameter(method, 0);
        Class<?> raw = parameterType.getRawClass();
        for (Class<?> eventClass : eventClasses) {
            if (raw == eventClass) {
                Class<?> resolved = parameterType.getGeneric(0).resolve();
                if (resolved != null && resolved != Object.class) {
                    return resolved;
                }
                return null;
            }
        }
        return null;
    }

    /**
     * 判断事件是否应由声明了具体泛型类型的监听方法处理。
     * <ol>
     *   <li>事件携带了 {@code dataType} 时，按简单类名比较（跨服务包名可能不同）；</li>
     *   <li>事件的数据对象存在时，还要求其确实是声明类型的实例。
     *       当类型无法还原（反序列化降级为 Map）时，数据对象不是声明类型的实例，此时跳过并告警，
     *       避免进入监听方法后出现 {@link ClassCastException}。</li>
     * </ol>
     * @param declared      监听方法声明的数据类型，为 null 时不过滤
     * @param eventDataType 事件的 {@code dataType}（可空）
     * @param oldData       事件的旧数据（可空）
     * @param newData       事件的新数据（可空）
     * @return true 表示应当处理
     */
    public static boolean matchesDataType(Class<?> declared, String eventDataType, Object oldData, Object newData) {
        if (declared == null) {
            return true;
        }
        if (StringUtil.notNull(eventDataType) && !declared.getSimpleName().equals(simpleName(eventDataType))) {
            return false;
        }
        Object data = (newData != null) ? newData : oldData;
        if (data != null && !declared.isInstance(data)) {
            log.warn("event data type mismatch, skip listener. declared: {}, event dataType: {}, actual data class: {}",
                    declared.getName(), eventDataType, data.getClass().getName());
            return false;
        }
        return true;
    }

    /* ======================== 日志脱敏 ======================== */

    private static final Pattern TOKEN_PATTERN = Pattern.compile("(\"token\"\\s*:\\s*\")([^\"]*)(\")");

    /**
     * 对 JSON 文本中的会话 token 做脱敏（事件中携带发起人的会话标识，不应原样出现在日志里）：
     * 完整显示前 {@value #TOKEN_VISIBLE_LENGTH} 位，其余用 {@code ***} 代替；不足 {@value #TOKEN_VISIBLE_LENGTH} 位时没有可隐藏的部分，原样显示。
     * @param json JSON 文本（可空）
     * @return 脱敏后的文本
     */
    public static String maskToken(String json) {
        if (json == null || json.indexOf("\"token\"") < 0) {
            return json;
        }
        return TOKEN_PATTERN.matcher(json).replaceAll(match ->
                Matcher.quoteReplacement(match.group(1) + maskValue(match.group(2)) + match.group(3)));
    }

    /** 脱敏后保留的 token 前缀长度（完整显示前 N 位，其余隐藏；与业务存储短 token 的长度一致） */
    public static final int TOKEN_VISIBLE_LENGTH = 20;

    private static String maskValue(String value) {
        if (value == null || value.length() <= TOKEN_VISIBLE_LENGTH) {
            return value == null ? "" : value;
        }
        return value.substring(0, TOKEN_VISIBLE_LENGTH) + "***";
    }

    /**
     * 把事件（或事件原文）转换成可以写入日志的文本：序列化失败时退回 toString，并对 token 脱敏。
     * @param event 事件对象
     * @return 日志文本
     */
    public static String toLogString(Object event) {
        if (event == null) {
            return "null";
        }
        String text;
        try {
            text = (event instanceof CharSequence) ? event.toString() : JsonUtil.serialize(event);
        } catch (Throwable e) {
            text = String.valueOf(event);
        }
        return maskToken(text);
    }

    /* ======================== 日志链路标识 ======================== */

    /**
     * 拼装 MDC 的 requestId：{@code traceId-businessId}，任一为空时只保留非空的部分。
     * @param traceId    请求ID
     * @param businessId 业务ID
     * @return 日志链路标识
     */
    public static String requestId(String traceId, String businessId) {
        boolean hasTrace = StringUtil.notNull(traceId);
        boolean hasBiz = StringUtil.notNull(businessId);
        if (hasTrace && hasBiz) {
            return traceId + "-" + businessId;
        }
        if (hasTrace) {
            return traceId;
        }
        return hasBiz ? businessId : "";
    }
}
