/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.events;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.jspecify.annotations.NonNull;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.uoquo.utils.StringUtil;
import com.uoquo.web.events.EventHeader;
import com.uoquo.web.events.EventSupport;
import com.uoquo.web.events.HeaderBackedEvent;

/**
 * 微服务事件（经 Spring Cloud Bus 发往其他服务）。<br>
 * 事件的公共信息（会话、请求、业务维度、操作人、操作端等）保存在 {@link EventHeader} 中，
 * 通过 {@link HeaderBackedEvent} 的默认方法访问（如 {@code getBusinessId()}、{@code setOperatorId(..)}）；
 * 来源服务、目标服务（路由）由父类 {@link RemoteApplicationEvent} 保存（{@code originService}、{@code destinationService}），
 * 本类不再保存第二份；父类的 {@code id} 是 Bus 自己使用的ID，业务的事件ID是公共信息中的 {@code eventId}。
 * 本类只维护与泛型 {@code T} 相关的业务数据：{@code dataType}、旧数据、新数据。
 *
 * <p>子类必须实现无参构造函数，否则事件处理器会报错；子类通过 getter/setter 访问公共信息。</p>
 * @author xuhz
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// destination：旧版本事件 JSON 中的属性（已删除，路由以 destinationService 为准），读取历史记录/重发时需要忽略
@JsonIgnoreProperties({"source", "destination"})
public class RemoteEvent<T> extends RemoteApplicationEvent implements HeaderBackedEvent {
    protected static final Object TRANSIENT_SOURCE = new Object();

    protected static final UoquoOriginFactory ORIGIN_FACTORY = new UoquoOriginFactory();

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
    protected RemoteEvent() {
        super(TRANSIENT_SOURCE, ORIGIN_FACTORY.getOrigin(null), DEFAULT_DESTINATION_FACTORY.getDestination(null));
        // 子类可以通过反射获取泛型类型
        this.dataType = resolveDataType();
    }

    /**
     * 构造函数
     * @param businessType    业务类型
     * @param operationType   操作类型
     * @param operationResult 操作结果
     */
    public RemoteEvent(@NonNull String businessType, @NonNull String operationType, String operationResult) {
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
    public RemoteEvent(@NonNull String businessType, @NonNull String operationType, String operationResult, String businessStatus) {
        this(businessType, operationType, operationResult, businessStatus, null);
    }

    /**
     * 构造函数
     * @param businessType    业务类型
     * @param operationType   操作类型
     * @param operationResult 操作结果
     * @param businessStatus  业务状态（可为空）
     * @param destinationService 定向服务名（可为空）
     */
    public RemoteEvent(@NonNull String businessType, @NonNull String operationType, String operationResult, String businessStatus, String destinationService) {
        super(TRANSIENT_SOURCE, ORIGIN_FACTORY.getOrigin(null), DEFAULT_DESTINATION_FACTORY.getDestination(StringUtil.isNull(destinationService) ? null : destinationService));
        // 业务类型、操作类型是事件的必填项，构造时即校验（比发布时才发现更容易定位调用方）
        if (StringUtil.isNull(businessType)) {
            throw new IllegalArgumentException("businessType must not be empty");
        }
        if (StringUtil.isNull(operationType)) {
            throw new IllegalArgumentException("operationType must not be empty");
        }
        // 其他属性（目标服务由父类保存，上面的 super(...) 已确定路由）
        header.setBusinessType(businessType);
        header.setOperationType(operationType);
        header.setOperationResult(operationResult);
        header.setBusinessStatus(businessStatus);
        // 子类可以通过反射获取泛型类型
        this.dataType = resolveDataType();
    }

    /**
     * 从子类声明的泛型父类（如 {@code extends RemoteEvent<UserInfo>}）解析数据类型名称。
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

    /**
     * Spring Cloud Bus 自己使用的ID（构造时随机生成，用于 Bus 的 Ack、追踪、equals 等），<b>不是</b>业务的事件ID。
     * @return Bus 内部ID
     * @deprecated 业务代码请使用 {@link #getEventId()}：事件消息的唯一标识。
     *             （保留本方法的覆盖仅为了在误用时给出编译警告，行为与父类一致）
     */
    @Deprecated
    @Override
    public String getId() {
        return super.getId();
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
