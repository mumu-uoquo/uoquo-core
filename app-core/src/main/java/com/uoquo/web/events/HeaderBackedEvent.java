/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import java.util.Date;
import java.util.Map;

/**
 * 公共信息由 {@link EventHeader} 保存的事件：实现类只需提供 {@link #header()}，
 * {@link UoquoEvent} 的全部 getter/setter（以及 {@code remarks}、{@link #copy(UoquoEvent)}）都由本接口的默认方法委托完成。
 * 分组与 {@link UoquoEvent}、{@link EventHeader} 一致。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>{@code header()} 不是 JavaBean 的 getter，不会参与 JSON 序列化；事件的 JSON 属性仍由下面的 getter/setter 决定，格式与之前一致；</li>
 *   <li>实现类如果继承的父类里已经有同名的方法，父类的方法优先于本接口的默认方法，需要在实现类中显式覆盖。
 *       事件ID使用 {@code eventId} 这个名字，就是为了不与 {@code RemoteApplicationEvent#getId()}（Bus 自己使用的ID）冲突。</li>
 * </ul>
 *
 * @author xuhz
 */
public interface HeaderBackedEvent extends UoquoEvent {

    /**
     * 获取保存公共信息的对象（不能为空，且在事件的生命周期内保持不变）。
     * @return 公共信息
     */
    EventHeader header();

    /* ============================ 事件标识 ============================ */

    @Override
    default String getEventId() {
        return header().getEventId();
    }

    @Override
    default void setEventId(String eventId) {
        header().setEventId(eventId);
    }

    @Override
    default boolean isRetry() {
        return header().isRetry();
    }

    @Override
    default void setRetry(boolean retry) {
        header().setRetry(retry);
    }

    /* ============================ 会话与请求 ============================ */

    @Override
    default String getToken() {
        return header().getToken();
    }

    @Override
    default void setToken(String token) {
        header().setToken(token);
    }

    @Override
    default String getTraceId() {
        return header().getTraceId();
    }

    @Override
    default void setTraceId(String traceId) {
        header().setTraceId(traceId);
    }

    /* ============================ 事件四维度（谁做了什么、成没成） ============================ */

    @Override
    default String getBusinessType() {
        return header().getBusinessType();
    }

    @Override
    default void setBusinessType(String businessType) {
        header().setBusinessType(businessType);
    }

    @Override
    default String getBusinessSubType() {
        return header().getBusinessSubType();
    }

    @Override
    default void setBusinessSubType(String businessSubType) {
        header().setBusinessSubType(businessSubType);
    }

    @Override
    default String getOperationType() {
        return header().getOperationType();
    }

    @Override
    default void setOperationType(String operationType) {
        header().setOperationType(operationType);
    }

    @Override
    default String getOperationResult() {
        return header().getOperationResult();
    }

    @Override
    default void setOperationResult(String operationResult) {
        header().setOperationResult(operationResult);
    }

    @Override
    default String getBusinessStatus() {
        return header().getBusinessStatus();
    }

    @Override
    default void setBusinessStatus(String businessStatus) {
        header().setBusinessStatus(businessStatus);
    }

    /* ============================ 业务数据 ============================ */

    @Override
    default String getBusinessTable() {
        return header().getBusinessTable();
    }

    @Override
    default void setBusinessTable(String businessTable) {
        header().setBusinessTable(businessTable);
    }

    @Override
    default String getBusinessId() {
        return header().getBusinessId();
    }

    @Override
    default void setBusinessId(String businessId) {
        header().setBusinessId(businessId);
    }

    @Override
    default String getBusinessInstituteId() {
        return header().getBusinessInstituteId();
    }

    @Override
    default void setBusinessInstituteId(String businessInstituteId) {
        header().setBusinessInstituteId(businessInstituteId);
    }

    /* ============================ 操作人信息 ============================ */

    @Override
    default String getOperatorId() {
        return header().getOperatorId();
    }

    @Override
    default void setOperatorId(String operatorId) {
        header().setOperatorId(operatorId);
    }

    @Override
    default String getOperatorName() {
        return header().getOperatorName();
    }

    @Override
    default void setOperatorName(String operatorName) {
        header().setOperatorName(operatorName);
    }

    @Override
    default String getOperatorInstituteId() {
        return header().getOperatorInstituteId();
    }

    @Override
    default void setOperatorInstituteId(String operatorInstituteId) {
        header().setOperatorInstituteId(operatorInstituteId);
    }

    @Override
    default Date getOperationTime() {
        return header().getOperationTime();
    }

    @Override
    default void setOperationTime(Date operationTime) {
        header().setOperationTime(operationTime);
    }

    /* ============================ 操作内容 ============================ */

    @Override
    default String getContent() {
        return header().getContent();
    }

    @Override
    default void setContent(String content) {
        header().setContent(content);
    }

    @Override
    default Map<String, ?> getExtension() {
        return header().getExtension();
    }

    @Override
    default void setExtension(Map<String, ?> extension) {
        header().setExtension(extension);
    }

    @Override
    default <E> void addExtension(String key, E val) {
        header().addExtension(key, val);
    }

    /* ============================ 操作端信息 ============================ */

    @Override
    default String getAppKey() {
        return header().getAppKey();
    }

    @Override
    default void setAppKey(String appKey) {
        header().setAppKey(appKey);
    }

    @Override
    default String getAppDeviceId() {
        return header().getAppDeviceId();
    }

    @Override
    default void setAppDeviceId(String appDeviceId) {
        header().setAppDeviceId(appDeviceId);
    }

    @Override
    default String getAppVersion() {
        return header().getAppVersion();
    }

    @Override
    default void setAppVersion(String appVersion) {
        header().setAppVersion(appVersion);
    }

    @Override
    default String getAppIp() {
        return header().getAppIp();
    }

    @Override
    default void setAppIp(String appIp) {
        header().setAppIp(appIp);
    }

    /* ============================ 备注 ============================ */

    /**
     * 获取备注。
     * @return 备注
     */
    default String getRemarks() {
        return header().getRemarks();
    }

    /**
     * 设置备注。
     * @param remarks 备注
     */
    default void setRemarks(String remarks) {
        header().setRemarks(remarks);
    }

    /* ============================ 复制 ============================ */

    /**
     * 复制事件的基本信息：只补充本事件中尚未设置的部分。
     * @param sourceEvent 源事件
     */
    default void copy(UoquoEvent sourceEvent) {
        header().copy(sourceEvent);
    }
}
