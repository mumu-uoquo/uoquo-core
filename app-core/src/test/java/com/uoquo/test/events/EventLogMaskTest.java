package com.uoquo.test.events;

import com.uoquo.web.events.AppEvent;
import com.uoquo.web.events.EventSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 日志脱敏：事件携带发起人的会话 token，不能原样写入日志。
 */
@DisplayName("事件日志脱敏")
class EventLogMaskTest {

    @Test
    @DisplayName("长 token 完整显示前20位，其余隐藏")
    void masksLongToken() {
        String masked = EventSupport.maskToken("{\"a\":1,\"token\":\"01234567890123456789abcdefghijkl\",\"b\":2}");

        assertEquals("{\"a\":1,\"token\":\"01234567890123456789***\",\"b\":2}", masked);
    }

    @Test
    @DisplayName("恰好20位及更短的 token 没有可隐藏的部分，原样显示；空 token 保持为空")
    void keepsShortToken() {
        assertEquals("{\"token\":\"01234567890123456789\"}", EventSupport.maskToken("{\"token\":\"01234567890123456789\"}"));
        assertEquals("{\"token\":\"abc\"}", EventSupport.maskToken("{\"token\":\"abc\"}"));
        assertEquals("{\"token\":\"\"}", EventSupport.maskToken("{\"token\":\"\"}"));
        // 21位：隐藏第21位
        assertEquals("{\"token\":\"01234567890123456789***\"}", EventSupport.maskToken("{\"token\":\"012345678901234567890\"}"));
    }

    @Test
    @DisplayName("没有 token 字段时原样返回；null 安全")
    void untouchedWithoutToken() {
        assertEquals("{\"a\":1}", EventSupport.maskToken("{\"a\":1}"));
        assertNull(EventSupport.maskToken(null));
    }

    @Test
    @DisplayName("toLogString：事件序列化后的 token 已脱敏")
    void toLogStringMasksEventToken() {
        AppEvent<String> event = new AppEvent<>("BIZ", "OP", "OK");
        event.setToken("SECRET-TOKEN-VALUE-0001");

        String text = EventSupport.toLogString(event);

        assertFalse(text.contains("SECRET-TOKEN-VALUE-0001"), text);
        assertTrue(text.contains("SECRET-TOKEN-VALUE-0***"), text);
    }

    @Test
    @DisplayName("toLogString：字符串入参（消息原文）同样脱敏；null 返回 \"null\"")
    void toLogStringForRawText() {
        assertEquals("{\"token\":\"abcdefghij0123456789***\"}", EventSupport.toLogString("{\"token\":\"abcdefghij0123456789ZZZZ\"}"));
        assertEquals("null", EventSupport.toLogString(null));
    }
}
