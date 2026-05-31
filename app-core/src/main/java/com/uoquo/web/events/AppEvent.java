/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.uoquo.utils.StringUtil;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEvent;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 子类必须实现无参构造函数，否则事件处理器会报错
 * @author xuhz
 */
@JsonIgnoreProperties("source")
public class AppEvent<T> extends ApplicationEvent implements UoquoEvent {
    private static final Object TRANSIENT_SOURCE = new Object();

    /**
     * 消息ID（可空）
     */
    protected String id;

    /**
     * 重发标识（标识当前事件信息是否是重发的）
     */
    protected boolean retry;

    /**
     * 事件目的地
     */
    protected String destination;

    /**
     * 会话标识
     */
    protected String token;

    /**
     * 当次请求ID
     */
    protected String traceId;

    /**
     * 业务类型（009）
     */
    protected String businessType;

    /**
     * 业务子类型（可自由定义）
     */
    protected String businessSubType;

    /**
     * 业务数据表名
     */
    protected String businessTable;

    /**
     * 业务ID
     */
    protected String businessId;

    /**
     * 业务数据所属机构ID
     */
    protected String businessInstituteId;

    /**
     * 操作人ID
     */
    protected String operatorId;

    /**
     * 操作人名称
     */
    protected String operatorName;

    /**
     * 操作人所属机构ID
     */
    protected String operatorInstituteId;

    /**
     * 操作类型（即：业务动作，010）
     */
    protected String operationType;

    /**
     * 执行状态（011）
     */
    protected String operationStatus;

    /**
     * 操作时间
     */
    protected Date operationTime;

    /**
     * 操作内容
     */
    protected String content;

    /**
     * 扩展信息
     */
    protected Map<String, Object> extension = new HashMap<>();

    /**
     * 操作端授权
     */
    protected String appKey;

    /**
     * 操作端标识
     */
    protected String appDeviceId;

    /**
     * 操作端版本
     */
    protected String appVersion;

    /**
     * 操作端IP
     */
    protected String appIp;

    /**
     * 备注
     */
    protected String remarks;

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
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof ParameterizedType) {
            Type type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
            this.dataType = type.getTypeName();
        }
    }

    /**
     * 构造函数
     * @param businessType    业务类型
     * @param operationType   操作类型
     * @param operationStatus 操作状态
     */
    public AppEvent(@NonNull String businessType, @NonNull String operationType, String operationStatus) {
        // 默认以业务类型为事件主题
        this(businessType, operationType, operationStatus, null);
    }

    /**
     * 构造函数
     * @param businessType    业务类型
     * @param operationType   操作类型
     * @param operationStatus 操作状态
     * @param destinationService 定向模块名（可为空）
     */
    public AppEvent(@NonNull String businessType, @NonNull String operationType, String operationStatus, String destinationService) {
        super(TRANSIENT_SOURCE);
        // 其他属性
        this.businessType    = businessType;
        this.operationType   = operationType;
        this.operationStatus = operationStatus;
        this.destination     = destinationService;
        // 子类可以通过反射获取泛型类型
        Type superClass = getClass().getGenericSuperclass();
        if (superClass instanceof ParameterizedType) {
            Type type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
            this.dataType = type.getTypeName();
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public boolean isRetry() {
        return retry;
    }

    @Override
    public void setRetry(boolean retry) {
        this.retry = retry;
    }

    @Override
    public String getDestination() {
        return destination;
    }

    @Override
    public void setDestination(String destination) {
        this.destination = destination;
    }

    @Override
    public String getToken() {
        return token;
    }

    @Override
    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public String getTraceId() {
        return traceId;
    }

    @Override
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    @Override
    public String getBusinessType() {
        return businessType;
    }

    @Override
    public void setBusinessType(@NonNull String businessType) {
        this.businessType = businessType;
    }

    @Override
    public String getBusinessSubType() {
        return businessSubType;
    }

    @Override
    public void setBusinessSubType(String businessSubType) {
        this.businessSubType = businessSubType;
    }

    @Override
    public String getBusinessTable() {
        return businessTable;
    }

    @Override
    public void setBusinessTable(String businessTable) {
        this.businessTable = businessTable;
    }

    @Override
    public String getBusinessId() {
        return businessId;
    }

    @Override
    public void setBusinessId(String businessId) {
        this.businessId = businessId;
    }

    @Override
    public String getBusinessInstituteId() {
        return businessInstituteId;
    }

    @Override
    public void setBusinessInstituteId(String businessInstituteId) {
        this.businessInstituteId = businessInstituteId;
    }

    @Override
    public String getOperatorId() {
        return operatorId;
    }

    @Override
    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    @Override
    public String getOperatorName() {
        return operatorName;
    }

    @Override
    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    @Override
    public String getOperatorInstituteId() {
        return operatorInstituteId;
    }

    @Override
    public void setOperatorInstituteId(String operatorInstituteId) {
        this.operatorInstituteId = operatorInstituteId;
    }

    @Override
    public String getOperationType() {
        return operationType;
    }

    @Override
    public void setOperationType(@NonNull String operationType) {
        this.operationType = operationType;
    }

    @Override
    public String getOperationStatus() {
        return operationStatus;
    }

    @Override
    public void setOperationStatus(String operationStatus) {
        this.operationStatus = operationStatus;
    }

    @Override
    public Date getOperationTime() {
        return operationTime;
    }

    @Override
    public void setOperationTime(Date operationTime) {
        this.operationTime = operationTime;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public Map<String, ?> getExtension() {
        return extension;
    }

    @Override
    public void setExtension(Map<String, ?> extension) {
        if (extension == null) {
            this.extension = new HashMap<>();
        } else {
            this.extension = new HashMap<>(extension);
        }
    }

    @Override
    public <E> void addExtension(String key, E val) {
        if (this.extension == null) {
            this.extension = new HashMap<>();
        }
        this.extension.put(key, val);
    }

    @Override
    public String getAppKey() {
        return appKey;
    }

    @Override
    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    @Override
    public String getAppDeviceId() {
        return appDeviceId;
    }

    @Override
    public void setAppDeviceId(String appDeviceId) {
        this.appDeviceId = appDeviceId;
    }

    @Override
    public String getAppVersion() {
        return appVersion;
    }

    @Override
    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    @Override
    public String getAppIp() {
        return appIp;
    }

    @Override
    public void setAppIp(String appIp) {
        this.appIp = appIp;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
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

    /**
     * 复制事件的基本信息
     * @param sourceEvent 源事件
     */
    public void copy(UoquoEvent sourceEvent) {
        if (StringUtil.isNull(this.getId())) {
            this.setId(sourceEvent.getId());
        }
        if (StringUtil.isNull(this.getToken())) {
            this.setToken(sourceEvent.getToken());
        }
        if (StringUtil.isNull(this.getTraceId())) {
            this.setTraceId(sourceEvent.getTraceId());
        }
        if (StringUtil.isNull(this.getAppKey())) {
            this.setAppKey(sourceEvent.getAppKey());
            this.setAppDeviceId(sourceEvent.getAppDeviceId());
            this.setAppVersion(sourceEvent.getAppVersion());
            this.setAppIp(sourceEvent.getAppIp());
        }
        if (StringUtil.isNull(this.getOperatorId())) {
            this.setOperatorId(sourceEvent.getOperatorId());
            this.setOperatorName(sourceEvent.getOperatorName());
            this.setOperationTime(sourceEvent.getOperationTime());
        }
        if (StringUtil.isNull(this.getOperatorInstituteId())) {
            this.setOperatorInstituteId(sourceEvent.getOperatorInstituteId());
        }
        if (StringUtil.isNull(this.getBusinessId())) {
            this.setBusinessId(sourceEvent.getBusinessId());
            this.setBusinessInstituteId(sourceEvent.getBusinessInstituteId());
            this.setBusinessTable(sourceEvent.getBusinessTable());
        }
    }
}
