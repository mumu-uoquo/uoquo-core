package com.uoquo.test.events;

import com.uoquo.utils.CurrentUser;
import com.uoquo.web.events.AppEvent;
import com.uoquo.web.events.EventEnricher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EventEnricher} 的单元测试：发布前补充发起人信息。
 */
@DisplayName("EventEnricher 发布前信息补充")
class EventEnricherTest {

    @BeforeEach
    void setUp() {
        CurrentUser.clear();
        CurrentUser.setToken("token-1");
        CurrentUser.setTraceId("trace-1");
        CurrentUser.setAppkey("app-1");
        CurrentUser.setDeviceId("device-1");
        CurrentUser.setAppVersion("1.0");
        CurrentUser.setClientIp("10.0.0.1");
        CurrentUser.UserInfo info = new CurrentUser.UserInfo();
        info.setUserId("u-1");
        info.setUserName("alice");
        info.setInstituteId("inst-1");
        CurrentUser.setInfo(info);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    @Test
    @DisplayName("补充会话、请求、操作端、操作人及操作时间")
    void fillsEverything() {
        AppEvent<String> event = new AppEvent<>("BIZ", "OP", "OK");
        String traceId = EventEnricher.enrich(event);

        assertEquals("trace-1", traceId);
        assertNotNull(event.getEventId());
        assertEquals("token-1", event.getToken());
        assertEquals("trace-1", event.getTraceId());
        assertEquals("app-1", event.getAppKey());
        assertEquals("device-1", event.getAppDeviceId());
        assertEquals("1.0", event.getAppVersion());
        assertEquals("10.0.0.1", event.getAppIp());
        assertEquals("u-1", event.getOperatorId());
        assertEquals("alice", event.getOperatorName());
        assertEquals("inst-1", event.getOperatorInstituteId());
        assertNotNull(event.getOperationTime());
    }

    @Test
    @DisplayName("业务端已设置操作人时，操作时间仍会补充")
    void operationTimeFilledEvenWhenOperatorPreset() {
        AppEvent<String> event = new AppEvent<>("BIZ", "OP", "OK");
        event.setOperatorId("preset");
        event.setOperatorName("preset-name");
        EventEnricher.enrich(event);

        assertEquals("preset", event.getOperatorId());
        assertEquals("preset-name", event.getOperatorName());
        assertNotNull(event.getOperationTime());
    }

    @Test
    @DisplayName("已设置的操作时间不会被覆盖")
    void keepsPresetOperationTime() {
        AppEvent<String> event = new AppEvent<>("BIZ", "OP", "OK");
        Date preset = new Date(1_000_000L);
        event.setOperationTime(preset);
        EventEnricher.enrich(event);

        assertEquals(preset, event.getOperationTime());
    }

    @Test
    @DisplayName("事件已有请求ID时沿用，并作为日志链路使用的请求ID返回")
    void keepsPresetTraceId() {
        AppEvent<String> event = new AppEvent<>("BIZ", "OP", "OK");
        event.setTraceId("replay-trace");

        assertEquals("replay-trace", EventEnricher.enrich(event));
        assertEquals("replay-trace", event.getTraceId());
    }

    @Test
    @DisplayName("当前请求没有请求ID时生成一个，并回写事件，使日志与事件一致")
    void generatesTraceIdWhenAbsent() {
        CurrentUser.setTraceId(null);
        AppEvent<String> event = new AppEvent<>("BIZ", "OP", "OK");
        String traceId = EventEnricher.enrich(event);

        assertNotNull(traceId);
        assertFalse(traceId.isBlank());
        assertEquals(traceId, event.getTraceId());
    }

    @Test
    @DisplayName("每次发布都重新生成事件ID")
    void alwaysRegeneratesId() {
        AppEvent<String> event = new AppEvent<>("BIZ", "OP", "OK");
        event.setEventId("old-id");
        EventEnricher.enrich(event);

        assertNotEquals("old-id", event.getEventId());
    }

    @Test
    @DisplayName("非事件对象只返回请求ID，不处理")
    void nonUoquoEvent() {
        assertEquals("trace-1", EventEnricher.enrich("plain payload"));
    }

    @Test
    @DisplayName("构造事件时业务类型、操作类型不能为空")
    void constructorValidatesRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> new AppEvent<String>(null, "OP", "OK"));
        assertThrows(IllegalArgumentException.class, () -> new AppEvent<String>("BIZ", "", "OK"));
    }

    @Test
    @DisplayName("copy：已设置操作人时，操作时间仍从源事件补充")
    void copyFillsOperationTimeIndependently() {
        AppEvent<String> source = new AppEvent<>("BIZ", "OP", "OK");
        Date time = new Date(2_000_000L);
        source.setOperationTime(time);
        source.setOperatorId("src");

        AppEvent<String> target = new AppEvent<>("BIZ", "OP", "OK");
        target.setOperatorId("mine");
        target.copy(source);

        assertEquals("mine", target.getOperatorId());
        assertEquals(time, target.getOperationTime());
    }
}
