/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.uoquo.utils.StringUtil;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEvent;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 本地事件（只在当前 JVM 内分发）。<br>
 * 事件的公共信息（会话、请求、业务维度、操作人、操作端等）保存在 {@link EventHeader} 中，
 * 通过 {@link HeaderBackedEvent} 的默认方法访问（如 {@code getBusinessId()}、{@code setOperatorId(..)}）；
 * 本类只维护与泛型 {@code T} 相关的业务数据：{@code dataType}、旧数据、新数据。
 *
 * <p>子类必须实现无参构造函数，否则事件处理器会报错；子类通过 getter/setter 访问公共信息。</p>
 * @author xuhz
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// id、destination：旧版本事件 JSON 中的属性（现已改名为 eventId、不再有 destination），读取历史记录/重发时需要忽略
@JsonIgnoreProperties({"source", "id", "destination"})
public class AppEvent<T> extends ApplicationEvent implements HeaderBackedEvent {
    private static final Object TRANSIENT_SOURCE = new Object();

    /**
     * 公共信息
     */
    private final EventHeader header = new EventHeader();

    /**
     * 业务数据类型（即泛型T的全限定名）
     */
    private String dataType;

    /**
     * 旧数据
     */
    protected T oldData;

    /**
     * 新数据
     */
    protected T newData;

    /**
     * 构造函数（子类必须有无参构造函数）！！
     */
    protected AppEvent() {
        super(TRANSIENT_SOURCE);
        // 子类可以通过反射获取泛型类型
        this.dataType = resolveDataType();
    }

    /**
     * 构造函数
     * @param businessType    业务类型
     * @param operationType   操作类型
     * @param operationResult 操作结果
     */
    public AppEvent(@NonNull String businessType, @NonNull String operationType, String operationResult) {
        // 默认以业务类型为事件主题
        this(businessType, operationType, operationResult, null);
    }

    /**
     * 构造函数
     * @param businessType    业务类型
     * @param operationType   操作类型
     * @param operationResult 操作结果
     * @param businessStatus  业务状态（可为空）
     */
    public AppEvent(@NonNull String businessType, @NonNull String operationType, String operationResult, String businessStatus) {
        super(TRANSIENT_SOURCE);
        // 业务类型、操作类型是事件的必填项，构造时即校验（比发布时才发现更容易定位调用方）
        if (StringUtil.isNull(businessType)) {
            throw new IllegalArgumentException("businessType must not be empty");
        }
        if (StringUtil.isNull(operationType)) {
            throw new IllegalArgumentException("operationType must not be empty");
        }
        // 其他属性
        header.setBusinessType(businessType);
        header.setOperationType(operationType);
        header.setOperationResult(operationResult);
        header.setBusinessStatus(businessStatus);
        // 子类可以通过反射获取泛型类型
        this.dataType = resolveDataType();
    }

    /**
     * 从子类声明的泛型父类（如 {@code extends AppEvent<UserInfo>}）解析数据类型名称。
     */
    private String resolveDataType() {
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof ParameterizedType) {
            Type type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
            return EventSupport.typeNameOf(type);
        }
        return null;
    }

    @Override
    public EventHeader header() {
        return header;
    }

    public String getDataType() {
        if (this.dataType != null) {
            return this.dataType;
        } else if (this.oldData != null) {
            return this.oldData.getClass().getTypeName();
        } else if (this.newData != null) {
            return this.newData.getClass().getTypeName();
        }
        return null;
    }

    public void setDataType(@NonNull Class<T> clazz) {
        this.dataType = clazz.getTypeName();
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public T getOldData() {
        return oldData;
    }

    public void setOldData(T oldData) {
        this.oldData = oldData;
    }

    public T getNewData() {
        return newData;
    }

    public void setNewData(T newData) {
        this.newData = newData;
    }
}
