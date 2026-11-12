/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import com.uoquo.utils.StringUtil;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 事件的公共信息（与泛型的业务数据无关的部分）。按含义分为以下几组（与 {@link UoquoEvent}、{@link HeaderBackedEvent} 的分组一致）：
 * <ol>
 *   <li>事件标识：事件ID、重发标识；</li>
 *   <li>会话与请求：会话、请求ID；</li>
 *   <li>事件四维度（谁做了什么、成没成）：业务类型、业务子类型、操作类型、操作结果，以及业务状态；</li>
 *   <li>业务数据：业务表、业务ID、数据所属机构；</li>
 *   <li>操作人信息：操作人、所属机构、操作时间；</li>
 *   <li>操作内容：内容、扩展信息；</li>
 *   <li>操作端信息：授权、设备、版本、IP；</li>
 *   <li>备注。</li>
 * </ol>
 *
 * <p>{@code AppEvent}（本地事件，继承 {@code ApplicationEvent}）与 {@code RemoteEvent}（微服务事件，继承
 * {@code RemoteApplicationEvent}）的父类不同，无法共用基类，因此把这部分状态与逻辑集中在本类中，
 * 两个事件类通过 {@link HeaderBackedEvent} 委托给它，避免重复维护两份相同的字段与方法。</p>
 *
 * <p>本类只是状态的容器，不参与 JSON 序列化：事件的 JSON 属性来自事件类对外的 getter/setter。</p>
 *
 * @author xuhz
 */
public class EventHeader {

    /* ============================ 事件标识 ============================ */

    /** 事件ID：自定义事件消息的唯一标识（可空，发布时生成） */
    private String eventId;

    /** 重发标识（标识当前事件信息是否是重发的） */
    private boolean retry;

    /* ============================ 会话与请求 ============================ */

    /** 会话标识 */
    private String token;

    /** 当次请求ID */
    private String traceId;

    /* ============================ 事件四维度（谁做了什么、成没成） ============================ */

    /** 业务类型（009） */
    private String businessType;

    /** 业务子类型（可自由定义） */
    private String businessSubType;

    /** 操作类型（即：业务动作，010） */
    private String operationType;

    /** 操作结果（011） */
    private String operationResult;

    /** 业务状态（当前操作结束后，业务流程所处状态） */
    private String businessStatus;

    /* ============================ 业务数据 ============================ */

    /** 业务数据表名 */
    private String businessTable;

    /** 业务ID */
    private String businessId;

    /** 业务数据所属机构ID */
    private String businessInstituteId;

    /* ============================ 操作人信息 ============================ */

    /** 操作人ID */
    private String operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** 操作人所属机构ID */
    private String operatorInstituteId;

    /** 操作时间 */
    private Date operationTime;

    /* ============================ 操作内容 ============================ */

    /** 操作内容 */
    private String content;

    /** 扩展信息 */
    private Map<String, Object> extension = new HashMap<>();

    /* ============================ 操作端信息 ============================ */

    /** 操作端授权 */
    private String appKey;

    /** 操作端标识 */
    private String appDeviceId;

    /** 操作端版本 */
    private String appVersion;

    /** 操作端IP */
    private String appIp;

    /* ============================ 备注 ============================ */

    /** 备注 */
    private String remarks;

    /* ============================ 事件标识 ============================ */

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public boolean isRetry() {
        return retry;
    }

    public void setRetry(boolean retry) {
        this.retry = retry;
    }

    /* ============================ 会话与请求 ============================ */

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /* ============================ 事件四维度（谁做了什么、成没成） ============================ */

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getBusinessSubType() {
        return businessSubType;
    }

    public void setBusinessSubType(String businessSubType) {
        this.businessSubType = businessSubType;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getOperationResult() {
        return operationResult;
    }

    public void setOperationResult(String operationResult) {
        this.operationResult = operationResult;
    }

    public String getBusinessStatus() {
        return businessStatus;
    }

    public void setBusinessStatus(String businessStatus) {
        this.businessStatus = businessStatus;
    }

    /* ============================ 业务数据 ============================ */

    public String getBusinessTable() {
        return businessTable;
    }

    public void setBusinessTable(String businessTable) {
        this.businessTable = businessTable;
    }

    public String getBusinessId() {
        return businessId;
    }

    public void setBusinessId(String businessId) {
        this.businessId = businessId;
    }

    public String getBusinessInstituteId() {
        return businessInstituteId;
    }

    public void setBusinessInstituteId(String businessInstituteId) {
        this.businessInstituteId = businessInstituteId;
    }

    /* ============================ 操作人信息 ============================ */

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getOperatorInstituteId() {
        return operatorInstituteId;
    }

    public void setOperatorInstituteId(String operatorInstituteId) {
        this.operatorInstituteId = operatorInstituteId;
    }

    public Date getOperationTime() {
        return operationTime;
    }

    public void setOperationTime(Date operationTime) {
        this.operationTime = operationTime;
    }

    /* ============================ 操作内容 ============================ */

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, ?> getExtension() {
        return extension;
    }

    public void setExtension(Map<String, ?> extension) {
        if (extension == null) {
            this.extension = new HashMap<>();
        } else {
            this.extension = new HashMap<>(extension);
        }
    }

    public <E> void addExtension(String key, E val) {
        if (this.extension == null) {
            this.extension = new HashMap<>();
        }
        this.extension.put(key, val);
    }

    /* ============================ 操作端信息 ============================ */

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppDeviceId() {
        return appDeviceId;
    }

    public void setAppDeviceId(String appDeviceId) {
        this.appDeviceId = appDeviceId;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getAppIp() {
        return appIp;
    }

    public void setAppIp(String appIp) {
        this.appIp = appIp;
    }

    /* ============================ 备注 ============================ */

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    /* ============================ 复制 ============================ */

    /**
     * 复制事件的基本信息：只补充本事件中尚未设置的部分，已有的值不会被覆盖。
     * @param sourceEvent 源事件
     */
    public void copy(UoquoEvent sourceEvent) {
        // 事件标识
        if (StringUtil.isNull(this.getEventId())) {
            this.setEventId(sourceEvent.getEventId());
        }
        // 会话与请求
        if (StringUtil.isNull(this.getToken())) {
            this.setToken(sourceEvent.getToken());
        }
        if (StringUtil.isNull(this.getTraceId())) {
            this.setTraceId(sourceEvent.getTraceId());
        }
        // 业务数据
        if (StringUtil.isNull(this.getBusinessId())) {
            this.setBusinessId(sourceEvent.getBusinessId());
            this.setBusinessInstituteId(sourceEvent.getBusinessInstituteId());
            this.setBusinessTable(sourceEvent.getBusinessTable());
        }
        // 操作人信息
        if (StringUtil.isNull(this.getOperatorId())) {
            this.setOperatorId(sourceEvent.getOperatorId());
            this.setOperatorName(sourceEvent.getOperatorName());
        }
        if (StringUtil.isNull(this.getOperatorInstituteId())) {
            this.setOperatorInstituteId(sourceEvent.getOperatorInstituteId());
        }
        // 操作时间与操作人相互独立：即使已设置了操作人，也需要补充操作时间
        if (this.getOperationTime() == null) {
            this.setOperationTime(sourceEvent.getOperationTime());
        }
        // 操作端信息
        if (StringUtil.isNull(this.getAppKey())) {
            this.setAppKey(sourceEvent.getAppKey());
            this.setAppDeviceId(sourceEvent.getAppDeviceId());
            this.setAppVersion(sourceEvent.getAppVersion());
            this.setAppIp(sourceEvent.getAppIp());
        }
    }
}
