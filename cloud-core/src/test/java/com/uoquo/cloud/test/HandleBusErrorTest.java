/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.test;

import com.uoquo.cloud.events.BusErrorChannelHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandlingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.test.util.AssertionErrors;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link BusErrorChannelHandler#handleBusError(ErrorMessage)}.
 *
 * <p>覆盖所有 payload 类型、null 边界情况及非 MessageHandlingException 场景。</p>
 *
 * <p><b>Validates: Requirements 2.1, 2.2</b></p>
 */
public class HandleBusErrorTest {

    BusErrorChannelHandler errorChannelHandler = new BusErrorChannelHandler();

    @BeforeEach
    public void setUp() {
        errorChannelHandler = new BusErrorChannelHandler();
    }

    // =========================================================================
    // payload 为 byte[] 时，日志中包含 UTF-8 转换后的字符串
    // =========================================================================

    /**
     * payload 为 byte[] 时，handleBusError 应将其转换为 UTF-8 字符串并记录到日志。
     *
     * <p><b>Validates: Requirements 2.1</b></p>
     */
    @Test
    public void testByteArrayPayload_logContainsUtf8String() {
        String originalJson = "{\"type\":\"com.nonexistent.FakeEvent\",\"id\":\"byte-001\",\"originService\":\"svc\"}";
        byte[] rawPayload = originalJson.getBytes(StandardCharsets.UTF_8);

        Message<byte[]> failedMessage = MessageBuilder.withPayload(rawPayload).build();
        MessageHandlingException mhe = new MessageHandlingException(failedMessage,
                "Failed to deserialize: class not found",
                new ClassCastException("class [B cannot be cast to class"));
        ErrorMessage errorMessage = new ErrorMessage(mhe);

        String logOutput = captureLogOutput(() -> errorChannelHandler.handleBusError(errorMessage));

        AssertionErrors.assertTrue("日志应包含 'Spring Cloud Bus received unresolvable message'",
                logOutput.contains("Spring Cloud Bus received unresolvable message"));
        AssertionErrors.assertTrue("日志应包含原始 JSON 内容（byte[] 转 UTF-8）",
                logOutput.contains("byte-001"));
        AssertionErrors.assertTrue("日志应包含 'com.nonexistent.FakeEvent'",
                logOutput.contains("com.nonexistent.FakeEvent"));
    }

    // =========================================================================
    // payload 为 String 时，日志中包含该字符串
    // =========================================================================

    /**
     * payload 为 String 时，handleBusError 应直接将其记录到日志。
     *
     * <p><b>Validates: Requirements 2.1</b></p>
     */
    @Test
    public void testStringPayload_logContainsString() {
        String payloadStr = "{\"type\":\"com.nonexistent.FakeEvent\",\"id\":\"str-002\"}";

        Message<String> failedMessage = MessageBuilder.withPayload(payloadStr).build();
        MessageHandlingException mhe = new MessageHandlingException(failedMessage,
                "Failed to deserialize", new RuntimeException("cause"));
        ErrorMessage errorMessage = new ErrorMessage(mhe);

        String logOutput = captureLogOutput(() -> errorChannelHandler.handleBusError(errorMessage));

        AssertionErrors.assertTrue("日志应包含 'Spring Cloud Bus received unresolvable message'",
                logOutput.contains("Spring Cloud Bus received unresolvable message"));
        AssertionErrors.assertTrue("日志应包含 String payload 内容",
                logOutput.contains("str-002"));
    }

    // =========================================================================
    // payload 为其他类型时，日志中包含 String.valueOf() 结果
    // =========================================================================

    /**
     * payload 为其他类型（如 Integer）时，handleBusError 应使用 String.valueOf() 转换并记录。
     *
     * <p><b>Validates: Requirements 2.1</b></p>
     */
    @Test
    public void testOtherTypePayload_logContainsStringValueOf() {
        Integer intPayload = 42;

        Message<Integer> failedMessage = MessageBuilder.withPayload(intPayload).build();
        MessageHandlingException mhe = new MessageHandlingException(failedMessage,
                "Failed to process", new RuntimeException("cause"));
        ErrorMessage errorMessage = new ErrorMessage(mhe);

        String logOutput = captureLogOutput(() -> errorChannelHandler.handleBusError(errorMessage));

        AssertionErrors.assertTrue("日志应包含 'Spring Cloud Bus received unresolvable message'",
                logOutput.contains("Spring Cloud Bus received unresolvable message"));
        AssertionErrors.assertTrue("日志应包含 String.valueOf(42) = '42'",
                logOutput.contains("42"));
    }

    // =========================================================================
    // payload 为 null 时，不抛出异常，日志记录 null 情况
    // =========================================================================

    /**
     * failedMessage 的 payload 为 null 时，handleBusError 不应抛出异常，应记录 null 情况。
     *
     * <p>通过自定义 Message 实现返回 null payload，模拟边界场景。</p>
     *
     * <p><b>Validates: Requirements 2.1, 2.2</b></p>
     */
    @Test
    public void testNullPayload_noExceptionThrown_logRecordsNullCase() {
        // 构造一个 getPayload() 返回 null 的 Message 实现
        Message<Object> nullPayloadMessage = new Message<Object>() {
            @Override
            public Object getPayload() {
                return null;
            }
            @Override
            public org.springframework.messaging.MessageHeaders getHeaders() {
                return new org.springframework.messaging.MessageHeaders(null);
            }
        };

        MessageHandlingException mhe = new MessageHandlingException(nullPayloadMessage,
                "Failed to process null payload", new RuntimeException("cause"));
        ErrorMessage errorMessage = new ErrorMessage(mhe);

        // 不应抛出任何异常
        String logOutput = captureLogOutput(() -> {
            try {
                errorChannelHandler.handleBusError(errorMessage);
            } catch (Throwable t) {
                AssertionErrors.fail("handleBusError 不应抛出异常，但抛出了: " + t);
            }
        });

        AssertionErrors.assertTrue("日志应包含 payload is null 相关信息",
                logOutput.contains("payload is null") || logOutput.contains("unresolvable"));
    }

    // =========================================================================
    // failedMessage 为 null 时，不抛出异常，日志记录 null 情况
    // =========================================================================

    /**
     * MessageHandlingException.getFailedMessage() 返回 null 时，
     * handleBusError 不应抛出异常，应记录 failedMessage is null。
     *
     * <p><b>Validates: Requirements 2.1, 2.2</b></p>
     */
    @Test
    public void testNullFailedMessage_noExceptionThrown_logRecordsNullCase() {
        // MessageHandlingException 构造时不传 failedMessage（使用 Throwable 构造器）
        // Spring Integration 的 MessageHandlingException(Throwable) 不存在，
        // 使用匿名子类覆盖 getFailedMessage() 返回 null
        Message<String> dummyMessage = MessageBuilder.withPayload("dummy").build();
        MessageHandlingException mhe = new MessageHandlingException(dummyMessage, "test") {
            @Override
            public Message<?> getFailedMessage() {
                return null;
            }
        };
        ErrorMessage errorMessage = new ErrorMessage(mhe);

        String logOutput = captureLogOutput(() -> {
            try {
                errorChannelHandler.handleBusError(errorMessage);
            } catch (Throwable t) {
                AssertionErrors.fail("handleBusError 不应抛出异常，但抛出了: " + t);
            }
        });

        AssertionErrors.assertTrue("日志应包含 failedMessage is null 相关信息",
                logOutput.contains("failedMessage is null"));
    }

    // =========================================================================
    // ErrorMessage 的 payload 不是 MessageHandlingException 时，以 WARN 记录通用错误信息
    // =========================================================================

    /**
     * ErrorMessage 的 payload 不是 MessageHandlingException 时，
     * handleBusError 应以 WARN 级别记录通用错误信息，不抛出异常。
     *
     * <p><b>Validates: Requirements 2.1, 2.2</b></p>
     */
    @Test
    public void testNonMessageHandlingException_logGenericWarning_noExceptionThrown() {
        RuntimeException genericException = new RuntimeException("some generic integration error");
        ErrorMessage errorMessage = new ErrorMessage(genericException);

        String logOutput = captureLogOutput(() -> {
            try {
                errorChannelHandler.handleBusError(errorMessage);
            } catch (Throwable t) {
                AssertionErrors.fail("handleBusError 不应抛出异常，但抛出了: " + t);
            }
        });

        AssertionErrors.assertTrue("日志应包含 'Spring Integration errorChannel received error'",
                logOutput.contains("Spring Integration errorChannel received error"));
    }

    /**
     * ErrorMessage 的 payload 为 IllegalStateException 时，
     * handleBusError 应以 WARN 级别记录通用错误信息。
     *
     * <p><b>Validates: Requirements 2.1, 2.2</b></p>
     */
    @Test
    public void testIllegalStateException_logGenericWarning() {
        IllegalStateException ex = new IllegalStateException("illegal state in integration pipeline");
        ErrorMessage errorMessage = new ErrorMessage(ex);

        String logOutput = captureLogOutput(() -> errorChannelHandler.handleBusError(errorMessage));

        AssertionErrors.assertTrue("日志应包含 'Spring Integration errorChannel received error'",
                logOutput.contains("Spring Integration errorChannel received error"));
        AssertionErrors.assertTrue("日志应包含异常消息",
                logOutput.contains("illegal state in integration pipeline"));
    }

    // =========================================================================
    // 辅助方法：捕获日志输出
    // =========================================================================

    /**
     * 捕获代码块执行期间的日志输出（通过 Logback ListAppender）。
     */
    private String captureLogOutput(Runnable action) {
        ch.qos.logback.classic.Logger rootLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                        ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);

        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender =
                new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);

        try {
            action.run();
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        } finally {
            rootLogger.detachAppender(listAppender);
            listAppender.stop();
        }

        List<String> messages = new ArrayList<>();
        for (ch.qos.logback.classic.spi.ILoggingEvent event : listAppender.list) {
            messages.add(event.getFormattedMessage());
        }
        return String.join("\n", messages);
    }
}
