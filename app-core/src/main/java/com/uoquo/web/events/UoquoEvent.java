/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.Map;

/**
 * 事件的公共信息。按含义分为以下几组（与 {@link EventHeader}、{@link HeaderBackedEvent} 的分组一致）：
 * <ol>
 *   <li>事件标识：事件ID、重发标识；</li>
 *   <li>会话与请求：会话、请求ID；</li>
 *   <li>事件四维度（谁做了什么、成没成）：业务大类、业务子类、操作动作、操作结果；</li>
 *   <li>业务数据：业务表、业务ID、数据所属机构；</li>
 *   <li>操作人信息：操作人、所属机构、操作时间；</li>
 *   <li>操作内容：内容、扩展信息；</li>
 *   <li>操作端信息：授权、设备、版本、IP。</li>
 * </ol>
 * 注：“业务状态”作为与“业务数据”同级的载荷数据，仅用于标识业务在做完动作后的流程状态，不作为消息头的内容
 */
public interface UoquoEvent {

    /* ============================ 事件标识 ============================ */

    /**
     * 获取事件ID：自定义事件消息的唯一标识（业务用它标识消息、入库）。<br>
     * 由发布器在每次发布时生成并覆盖（回放时也会重新生成），发布前为空。
     * @return 事件ID
     */
    String getEventId();

    /**
     * 设置事件ID（内部统一设置）
     * @param eventId 事件ID
     */
    void setEventId(String eventId);

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

    /* ============================ 会话与请求 ============================ */

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
    void setTraceId(String traceId);

    /* ============================ 事件四维度（谁做了什么、成没成） ============================ */

    /**
     * 获取业务类型（009）
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
     * 获取操作动作（即：业务动作，010）
     * @return 操作动作
     */
    String getOperationType();

    /**
     * 设置操作动作（即：业务动作，010）
     * @param operationType 操作动作
     */
    void setOperationType(@NotNull String operationType);

    /**
     * 获取操作结果（011）
     * @return 操作结果
     */
    String getOperationResult();

    /**
     * 设置操作结果（011）
     * @param operationResult 操作结果
     */
    void setOperationResult(String operationResult);

    /**
     * 获取业务状态（当前操作结束后，业务流程所处状态）
     * @return 业务状态
     */
    String getBusinessStatus();

    /**
     * 设置业务状态（当前操作结束后，业务流程所处状态）
     * @param businessStatus 业务状态
     */
    void setBusinessStatus(String businessStatus);

    /* ============================ 业务数据 ============================ */

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

    /* ============================ 操作人信息 ============================ */

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
     * 获取操作时间
     * @return 操作时间
     */
    Date getOperationTime();

    /**
     * 设置操作时间
     * @param operationTime 操作时间
     */
    void setOperationTime(Date operationTime);

    /* ============================ 操作内容 ============================ */

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

    /* ============================ 操作端信息 ============================ */

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
