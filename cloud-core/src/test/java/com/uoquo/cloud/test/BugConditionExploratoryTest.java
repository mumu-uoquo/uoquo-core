/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.test;

import com.uoquo.cloud.events.BusErrorChannelHandler;
import org.junit.jupiter.api.Test;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.test.util.AssertionErrors;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bug Condition Exploratory Test — Property 1: Bug Condition
 *
 * <p>此测试编码了期望行为（修复后应通过）。在未修复代码上运行时，
 * 测试 FAIL，即证明 Bug 存在。</p>
 *
 * <p><b>Validates: Requirements 1.1, 1.2</b></p>
 *
 * <p>Bug 场景：当 Spring Cloud Bus 消息的 {@code type} 字段对应的类在当前
 * ClassLoader 中不存在时，Jackson 多态反序列化失败，Spring Integration 将其
 * 包装为 {@link MessagingException} 并路由到 {@code errorChannel}。
 * 现有代码中 {@code errorChannel} 无自定义处理器，原始 payload 未被记录到日志。</p>
 */
public class BugConditionExploratoryTest {

    private static final String FAKE_TYPE_PAYLOAD =
            "{\"type\":\"com.nonexistent.FakeEvent\",\"id\":\"test-id-001\",\"originService\":\"test-service\"}";

    private static final String PAYLOAD_MARKER = "test-id-001";

    /**
     * Property 1: Bug Condition — 未知 type 消息触发 MessagingException 且 payload 未记录
     *
     * <p><b>Validates: Requirements 1.1, 1.2</b></p>
     *
     * <p>此测试在未修复代码上 FAIL（证明 Bug 存在），修复后 PASS（验证 Bug 已修复）。</p>
     *
     * <p>测试步骤：
     * <ol>
     *   <li>构造 {@code type=com.nonexistent.FakeEvent} 的 JSON 消息（byte[] payload，模拟 MQ 原始消息）</li>
     *   <li>模拟 Spring Integration 管道：handler 尝试加载不存在的类，抛出异常，包装为 MessagingException 路由到 errorChannel</li>
     *   <li>断言 errorChannel 收到 ErrorMessage（验证异常被路由到 errorChannel）</li>
     *   <li>断言 failedMessage 不为 null（验证原始消息可被提取）</li>
     *   <li>断言日志中包含 payload 字符串内容（修复后通过；未修复时 FAIL，证明 Bug 存在）</li>
     * </ol>
     * </p>
     */
    @Test
    public void testUnknownTypeTriggersBugCondition_payloadShouldBeLoggedAfterFix() throws InterruptedException {
        // 捕获 errorChannel 收到的消息
        AtomicReference<Message<?>> capturedErrorMessage = new AtomicReference<>();
        CountDownLatch errorLatch = new CountDownLatch(1);

        // 创建 errorChannel（Spring Integration 内置错误通道）
        PublishSubscribeChannel errorChannel = new PublishSubscribeChannel();
        errorChannel.subscribe(message -> {
            capturedErrorMessage.set(message);
            errorLatch.countDown();
        });

        // 创建 inputChannel，模拟 Spring Cloud Bus 消息入口
        DirectChannel inputChannel = new DirectChannel();

        // 注册消息处理器：模拟 Jackson 反序列化失败（type 对应类不存在）
        inputChannel.subscribe(message -> {
            Object payload = message.getPayload();
            String payloadStr;
            if (payload instanceof byte[]) {
                payloadStr = new String((byte[]) payload, StandardCharsets.UTF_8);
            } else {
                payloadStr = String.valueOf(payload);
            }

            // 模拟 Jackson 多态反序列化失败：type 对应类不在 classpath 中
            if (payloadStr.contains("com.nonexistent.FakeEvent")) {
                try {
                    Class.forName("com.nonexistent.FakeEvent");
                } catch (ClassNotFoundException e) {
                    // 模拟 Spring Integration 将异常包装为 MessagingException 并路由到 errorChannel
                    MessagingException messagingException = new MessagingException(message,
                            "Failed to deserialize: class com.nonexistent.FakeEvent not found", e);
                    ErrorMessage errorMessage = new ErrorMessage(messagingException, message);
                    errorChannel.send(errorMessage);
                    return;
                }
            }
        });

        // 构造 byte[] payload，模拟 RabbitMQ/Kafka 原始消息
        byte[] rawPayload = FAKE_TYPE_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        Message<byte[]> message = MessageBuilder.withPayload(rawPayload).build();

        // 发送消息到 inputChannel
        inputChannel.send(message);

        // 等待 errorChannel 收到消息（最多 3 秒）
        boolean received = errorLatch.await(3, TimeUnit.SECONDS);

        // 断言 1：errorChannel 收到了 ErrorMessage
        AssertionErrors.assertTrue("errorChannel 应收到 ErrorMessage，但未收到（超时）", received);
        AssertionErrors.assertNotNull("errorChannel 收到的消息不应为 null", capturedErrorMessage.get());

        // 断言 2：ErrorMessage 的 payload 是 MessagingException
        Message<?> errorMsg = capturedErrorMessage.get();
        AssertionErrors.assertTrue("ErrorMessage 的 payload 应为 MessagingException",
                errorMsg.getPayload() instanceof MessagingException);

        // 断言 3：failedMessage 不为 null（原始消息可被提取）
        MessagingException exception = (MessagingException) errorMsg.getPayload();
        AssertionErrors.assertNotNull("MessagingException 应包含 failedMessage（原始消息）",
                exception.getFailedMessage());

        // 断言 4：failedMessage 的 payload 包含原始 JSON 内容
        Message<?> failedMessage = exception.getFailedMessage();
        Object failedPayload = failedMessage.getPayload();
        String failedPayloadStr;
        if (failedPayload instanceof byte[]) {
            failedPayloadStr = new String((byte[]) failedPayload, StandardCharsets.UTF_8);
        } else {
            failedPayloadStr = String.valueOf(failedPayload);
        }
        AssertionErrors.assertTrue("failedMessage 的 payload 应包含原始 JSON 内容",
                failedPayloadStr.contains("com.nonexistent.FakeEvent"));

        // ============================================================
        // 断言 5（关键断言）：日志中应包含 payload 字符串内容
        //
        // Bug Condition 核心断言（修复后验证）：
        // - 修复后：CloudConfig.handleBusError 捕获 MessagingException，
        //   提取 failedMessage 的 payload，以 WARN 级别记录到日志，此断言 PASS
        // - 未修复时：errorChannel 无自定义处理器，payload 未被记录到日志，
        //   此断言 FAIL，即证明 Bug 存在
        //
        // 通过直接调用 BusErrorChannelHandler.handleBusError 并捕获日志，验证修复后行为
        // ============================================================
        BusErrorChannelHandler errorChannelHandler = new BusErrorChannelHandler();
        String logContent = captureLogOutput(() ->
                errorChannelHandler.handleBusError(new ErrorMessage(exception, message)));

        // 修复后，此断言应 PASS（handleBusError 记录了 payload 到日志）
        AssertionErrors.assertTrue(
                "【Bug Condition 验证】日志中应包含 payload 字符串内容（marker: " + PAYLOAD_MARKER + "）。" +
                "此断言在未修复代码上 FAIL，证明 Bug 存在：errorChannel 无自定义处理器，原始 payload 未被记录。" +
                "实际日志内容: [" + logContent + "]",
                logContent.contains(PAYLOAD_MARKER)
        );

        // 清理
        errorChannel.destroy();
    }

    /**
     * 辅助测试：验证 failedMessage 中的 payload 可以被正确提取为字符串。
     *
     * <p>此测试验证 Bug 修复的前提条件：原始消息确实存在于 MessagingException 中，
     * 只是没有被记录到日志。</p>
     */
    @Test
    public void testFailedMessagePayloadIsExtractable() throws InterruptedException {
        AtomicReference<Message<?>> capturedErrorMessage = new AtomicReference<>();
        CountDownLatch errorLatch = new CountDownLatch(1);

        PublishSubscribeChannel errorChannel = new PublishSubscribeChannel();
        errorChannel.subscribe(message -> {
            capturedErrorMessage.set(message);
            errorLatch.countDown();
        });

        DirectChannel inputChannel = new DirectChannel();
        inputChannel.subscribe(message -> {
            Object payload = message.getPayload();
            String payloadStr = (payload instanceof byte[])
                    ? new String((byte[]) payload, StandardCharsets.UTF_8)
                    : String.valueOf(payload);

            if (payloadStr.contains("com.nonexistent.FakeEvent")) {
                try {
                    Class.forName("com.nonexistent.FakeEvent");
                } catch (ClassNotFoundException e) {
                    MessagingException ex = new MessagingException(message,
                            "Failed to deserialize: class not found", e);
                    errorChannel.send(new ErrorMessage(ex, message));
                }
            }
        });

        byte[] rawPayload = FAKE_TYPE_PAYLOAD.getBytes(StandardCharsets.UTF_8);
        inputChannel.send(MessageBuilder.withPayload(rawPayload).build());

        boolean received = errorLatch.await(3, TimeUnit.SECONDS);
        AssertionErrors.assertTrue("errorChannel 应收到 ErrorMessage", received);

        Message<?> errorMsg = capturedErrorMessage.get();
        MessagingException exception = (MessagingException) errorMsg.getPayload();
        Message<?> failedMessage = exception.getFailedMessage();

        AssertionErrors.assertNotNull("failedMessage 不应为 null", failedMessage);

        Object payload = failedMessage.getPayload();
        AssertionErrors.assertNotNull("failedMessage.payload 不应为 null", payload);

        String payloadStr = (payload instanceof byte[])
                ? new String((byte[]) payload, StandardCharsets.UTF_8)
                : String.valueOf(payload);

        // 验证 payload 包含原始 JSON 内容（这是修复的基础）
        AssertionErrors.assertTrue("payload 应包含 'com.nonexistent.FakeEvent'",
                payloadStr.contains("com.nonexistent.FakeEvent"));
        AssertionErrors.assertTrue("payload 应包含 marker '" + PAYLOAD_MARKER + "'",
                payloadStr.contains(PAYLOAD_MARKER));

        System.out.println("=== Bug Condition 反例 ===");
        System.out.println("errorChannel 收到 ErrorMessage: " + errorMsg.getPayload().getClass().getName());
        System.out.println("failedMessage payload (可提取): " + payloadStr);
        System.out.println("结论：payload 存在于 MessagingException 中，但未被记录到日志（Bug 存在）");

        errorChannel.destroy();
    }

    /**
     * 捕获代码块执行期间的日志输出。
     *
     * <p>通过 Logback 的 {@code ListAppender} 捕获日志，检查是否包含期望内容。</p>
     */
    private String captureLogOutput(Runnable action) {
        // 使用 Logback ListAppender 捕获日志
        ch.qos.logback.classic.Logger rootLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                        ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);

        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender =
                new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        rootLogger.addAppender(listAppender);

        try {
            action.run();
            // 给日志一点时间写入
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        } finally {
            rootLogger.detachAppender(listAppender);
            listAppender.stop();
        }

        // 收集所有日志消息
        List<String> messages = new ArrayList<>();
        for (ch.qos.logback.classic.spi.ILoggingEvent event : listAppender.list) {
            messages.add(event.getFormattedMessage());
        }
        return String.join("\n", messages);
    }
}
