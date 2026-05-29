/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.utils.json.jackson;

import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.uoquo.annotation.json.Sensitive;

import java.io.Serial;

/**
 * Jackson 注解内省器：将带 {@link Sensitive} 注解的字段路由到 {@link SensitiveSerializer}
 * 和 {@link SensitiveDeserializer}.
 *
 * <p>相比在 {@code SimpleModule} 中通过
 * {@code addSerializer(String.class, ...)} / {@code addDeserializer(String.class, ...)}
 * 的全局接管方式，使用注解内省器的优势：</p>
 * <ul>
 *   <li><b>精确路由</b>：仅有 {@code @Sensitive} 标注的字段才会进入自定义处理逻辑，
 *       其他 String 字段保持 Jackson 默认行为，避免性能与作用域副作用。</li>
 *   <li><b>语义清晰</b>：与 Jackson 内置 {@code @JsonSerialize}/{@code @JsonDeserialize}
 *       注解的工作方式一致，便于排查和维护。</li>
 * </ul>
 *
 * <p>注册方式参考 {@link com.uoquo.utils.json.JsonUtil#initialJackson(com.fasterxml.jackson.databind.ObjectMapper)}.</p>
 *
 * @author xuhz
 */
public class SensitiveAnnotationIntrospector extends JacksonAnnotationIntrospector {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当属性带有 {@link Sensitive} 注解时，返回 {@link SensitiveSerializer} 类供 Jackson 自动实例化；
     * 否则委托给父类（通常返回 null，由 Jackson 选择默认序列化器）.
     *
     * @param a 注解元素（属性 / 字段 / 方法 / 类）
     * @return {@code SensitiveSerializer.class} 或父类返回值
     */
    @Override
    public Object findSerializer(Annotated a) {
        if (a.getAnnotation(Sensitive.class) != null) {
            return SensitiveSerializer.class;
        }
        return super.findSerializer(a);
    }

    /**
     * 当属性带有 {@link Sensitive} 注解时，返回 {@link SensitiveDeserializer} 类供 Jackson 自动实例化；
     * 否则委托给父类（通常返回 null，由 Jackson 选择默认反序列化器）.
     *
     * @param a 注解元素（属性 / 字段 / 方法 / 类）
     * @return {@code SensitiveDeserializer.class} 或父类返回值
     */
    @Override
    public Object findDeserializer(Annotated a) {
        if (a.getAnnotation(Sensitive.class) != null) {
            return SensitiveDeserializer.class;
        }
        return super.findDeserializer(a);
    }
}
