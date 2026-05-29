/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.Map;

public interface UoquoEvent {

    /**
     * 获取事件ID
     * @return 事件ID
     */
    String getId();

    /**
     * 设置事件ID（内部统一设置）
     * @param id 事件ID
     */
    void setId(String id);

    /**
     * 获取重发标识（标识当前事件信息是否重发的）
     * @return 重发标识
     */
    default boolean isRetry() {
        return false;
    }

    /**
     * 设置重发标识（标识当前事件信息是否重发的）
     * @param retry 重发标识
     */
    default void setRetry(boolean retry) {
        // do nothing;
    }

    /**
     * 获取事件目标服务名
     * @return 目标服务名（可空）
     */
    String getDestination();

    /**
     * 设置事件目标服务名
     * @param destination 目标服务名（可空）
     */
    void setDestination(String destination);

    /**
     * 获取会话ID
     * @return 当前的会话ID
     */
    String getToken();

    /**
     * 设置会话ID
     * @param token 当前的会话ID
     */
    void setToken(String token);

    /**
     * 获取请求ID
     * @return 本次请求的ID
     */
    String getTraceId();

    /**
     * 设置请求ID
     * @param traceId 本次请求的ID
     */
    void setTraceId(String traceId) ;

    /**
     * 获取业务类型
     * @return 业务类型
     */
    String getBusinessType();

    /**
     * 设置业务类型（009）
     * @param businessType 业务类型
     */
    void setBusinessType(@NotNull String businessType);

    /**
     * 获取业务子类型（可自由定义）
     * @return 业务子类型
     */
    String getBusinessSubType();

    /**
     * 设置业务子类型（可自由定义）
     * @param businessSubType 业务子类型
     */
    void setBusinessSubType(String businessSubType);

    /**
     * 获取业务表名
     * @return 业务表名
     */
    String getBusinessTable();

    /**
     * 设置业务表名
     * @param businessTable 业务表名
     */
    void setBusinessTable(String businessTable);

    /**
     * 获取业务ID
     * @return 业务ID
     */
    String getBusinessId();

    /**
     * 设置业务的关联 ID
     * @param businessId 业务ID
     */
    void setBusinessId(String businessId);

    /**
     * 获取业务数据所属机构ID
     * @return 业务数据所属机构ID
     */
    String getBusinessInstituteId();

    /**
     * 设置业务数据所属机构ID
     * @param businessInstituteId 业务数据所属机构ID
     */
    void setBusinessInstituteId(String businessInstituteId);

    /**
     * 获取操作人ID
     * @return 操作人ID
     */
    String getOperatorId();

    /**
     * 设置操作人ID
     * @param operatorId 操作人ID
     */
    void setOperatorId(String operatorId);

    /**
     * 获取操作人名称
     * @return 操作人名称
     */
    String getOperatorName();

    /**
     * 设置操作人名称
     * @param operatorName 操作人名称
     */
    void setOperatorName(String operatorName);

    /**
     * 获取操作人所属机构ID
     * @return 操作人所属机构ID
     */
    String getOperatorInstituteId();

    /**
     * 设置操作人所属机构ID
     * @param operatorInstituteId 操作人所属机构ID
     */
    void setOperatorInstituteId(String operatorInstituteId);

    /**
     * 获取操作类型（即：业务动作，010）
     * @return 操作类型
     */
    String getOperationType();

    /**
     * 设置操作类型（即：业务动作，010）
     * @param operationType 操作类型
     */
    void setOperationType(@NotNull String operationType);

    /**
     * 获取执行状态
     * @return 执行状态
     */
    String getOperationStatus();

    /**
     * 设置执行状态（011）
     * @param operationStatus 执行状态
     */
    void setOperationStatus(String operationStatus);

    /**
     * 获取操作时间
     * @return 操作时间
     */
    Date getOperationTime();

    /**
     * 设置操作时间
     * @param operationTime 操作时间
     */
    void setOperationTime(Date operationTime);

    /**
     * 获取操作内容
     * @return 操作内容
     */
    String getContent();

    /**
     * 设置操作内容
     * @param content 操作内容
     */
    void setContent(String content);

    /**
     * 获取扩展信息
     * @return 扩展信息
     */
    Map<String, ?> getExtension();

    /**
     * 设置扩展信息
     * @param extension 扩展信息
     */
    void setExtension(Map<String, ?> extension);

    /**
     * 追加扩展信息
     * @param key 扩展信息key
     * @param val 扩展信息value
     */
    <E> void addExtension(String key, E val);

    /**
     * 获取操作端授权
     * @return 操作端授权
     */
    String getAppKey();

    /**
     * 设置操作端授权
     * @param appKey 操作端授权
     */
    void setAppKey(String appKey);

    /**
     * 获取操作端标识
     * @return 操作端标识
     */
    String getAppDeviceId();

    /**
     * 设置操作端标识
     * @param appDeviceId 操作端标识
     */
    void setAppDeviceId(String appDeviceId);

    /**
     * 获取操作端版本
     * @return 操作端版本
     */
    String getAppVersion();

    /**
     * 设置操作端版本
     * @param appVersion 作端版本
     */
    void setAppVersion(String appVersion);

    /**
     * 获取操作端IP
     * @return 操作端IP
     */
    String getAppIp();

    /**
     * 设置操作端IP
     * @param appIp 操作端IP
     */
    void setAppIp(String appIp);

}
