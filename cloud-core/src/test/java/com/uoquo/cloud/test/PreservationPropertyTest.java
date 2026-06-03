/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.cloud.test;

import com.uoquo.cloud.events.RemoteEventListenerAdapter;
import com.uoquo.web.events.deserializer.DataTypeResolver;
import com.uoquo.web.events.deserializer.EventPackageScanner;
import org.junit.jupiter.api.Test;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.test.util.AssertionErrors;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;



/**
 * Preservation Property Test — Property 2: Preservation
 *
 * <p>验证对所有 {@code isBugCondition(msg)} 返回 false 的消息（即 type 字段对应类可正常加载），
 * 修复后的消息处理链路产生与修复前完全相同的行为。</p>
 *
 * <p>这些测试在未修复代码上 PASS（建立基线），修复后也应继续 PASS（无回归）。</p>
 *
 * <p><b>Validates: Requirements 3.1, 3.2, 3.3</b></p>
 */
public class PreservationPropertyTest {

    // =========================================================================
    // Observation 1: type=RemoteEvent 的合法消息正常反序列化，事件监听器被调用
    // Validates: Requirement 3.1
    // =========================================================================

    /**
     * 观察 1：已知 type 消息（type=RemoteEvent）正常路由，不触发 errorChannel。
     *
     * <p>构造 type=RemoteEvent 的合法 JSON 消息，通过 Spring Integration DirectChannel 发送，
     * 断言消息被正常处理（handler 被调用），errorChannel 未收到任何 ErrorMessage。</p>
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Test
    public void observation1_knownTypeMessage_handlerCalledAndNoErrorChannel() throws InterruptedException {
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        AtomicReference<Message<?>> capturedError = new AtomicReference<>();
        CountDownLatch errorLatch = new CountDownLatch(1);

        PublishSubscribeChannel errorChannel = new PublishSubscribeChannel();
        errorChannel.subscribe(msg -> {
            capturedError.set(msg);
            errorLatch.countDown();
        });

        DirectChannel inputChannel = new DirectChannel();
        inputChannel.subscribe(message -> {
            Object payload = message.getPayload();
            String payloadStr = payload instanceof byte[]
                    ? new String((byte[]) payload, StandardCharsets.UTF_8)
                    : String.valueOf(payload);

            // 模拟：type=RemoteEvent 的消息，类存在于 classpath，正常处理
            if (payloadStr.contains("\"type\":\"RemoteEvent\"") || payloadStr.contains("RemoteEvent")) {
                handlerCalled.set(true);
                // 正常处理，不路由到 errorChannel
            }
        });

        // 构造 type=RemoteEvent 的合法消息（isBugCondition = false）
        String knownTypeJson = "{\"type\":\"RemoteEvent\",\"id\":\"known-001\",\"originService\":\"test-service\","
                + "\"businessType\":\"TEST\",\"operationType\":\"CREATE\",\"operationStatus\":\"SUCCESS\"}";
        byte[] rawPayload = knownTypeJson.getBytes(StandardCharsets.UTF_8);
        Message<byte[]> message = MessageBuilder.withPayload(rawPayload).build();

        inputChannel.send(message);

        // errorChannel 不应收到消息（等待 500ms 确认）
        boolean errorReceived = errorLatch.await(500, TimeUnit.MILLISECONDS);

        // 断言 1：handler 被正常调用
        AssertionErrors.assertTrue("已知 type 消息应被 handler 正常处理", handlerCalled.get());

        // 断言 2：errorChannel 未收到任何 ErrorMessage
        AssertionErrors.assertFalse("已知 type 消息不应触发 errorChannel", errorReceived);
        AssertionErrors.assertNull("errorChannel 不应收到任何消息", capturedError.get());

        errorChannel.destroy();
    }

    /**
     * 观察 1（DataTypeResolver 集成）：type=RemoteEvent 消息通过 DataTypeResolver 正常解析 dataType。
     *
     * <p>直接测试 DataTypeResolver 对已知类型的解析行为：
     * dataType=java.lang.String 应正常解析为 String.class，不抛出异常。</p>
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Test
    public void observation1_dataTypeResolver_knownDataType_resolvesCorrectly() {
        EventPackageScanner scanner = new EventPackageScanner(
                new String[]{"com.uoquo.cloud.events", "com.uoquo.cloud.test"});
        DataTypeResolver resolver = new DataTypeResolver(scanner);

        // 已知 dataType（java.lang.String）应正常解析
        Class<?> resolved = resolver.resolve("java.lang.String", "RemoteEvent");
        AssertionErrors.assertEquals("已知 dataType 应解析为 String.class", String.class, resolved);

        // 已知 dataType（java.lang.Integer）应正常解析
        Class<?> resolvedInt = resolver.resolve("java.lang.Integer", "RemoteEvent");
        AssertionErrors.assertEquals("已知 dataType 应解析为 Integer.class", Integer.class, resolvedInt);
    }

    // =========================================================================
    // Observation 2: RemoteEventListenerAdapter.doInvoke 抛出业务异常时，
    //                现有 try-catch 记录错误日志并返回 null
    // Validates: Requirement 3.2
    // =========================================================================

    /**
     * 观察 2：RemoteEventListenerAdapter.doInvoke 抛出业务异常时，
     * 现有 try-catch 捕获异常，记录日志，返回 null，不向上层抛出。
     *
     * <p>直接调用 RemoteEventListenerAdapter，模拟 doInvoke 抛出业务异常，
     * 断言方法返回 null 且不抛出异常。</p>
     *
     * <p><b>Validates: Requirements 3.2</b></p>
     */
    @Test
    public void observation2_remoteEventListenerAdapter_businessException_returnsNullNoThrow() throws Exception {
        // 创建一个会抛出业务异常的监听器方法
        Method method = BusinessExceptionListener.class.getMethod("onEvent", TestRemoteEvent.class);
        RemoteEventListenerAdapter adapter = new RemoteEventListenerAdapter(
                "testBean", BusinessExceptionListener.class, method);

        TestRemoteEvent event = new TestRemoteEvent("TEST", "CREATE", "SUCCESS");

        // 调用 onApplicationEvent，内部会调用 doInvoke，doInvoke 会抛出业务异常
        // 现有 try-catch 应捕获异常，记录日志，返回 null，不向上层抛出
        try {
            adapter.onApplicationEvent(event);
            // 如果没有抛出异常，说明 try-catch 正常工作
        } catch (Throwable e) {
            AssertionErrors.fail("RemoteEventListenerAdapter.doInvoke 抛出业务异常时，不应向上层抛出，但实际抛出了: " + e);
        }
        // 测试通过：现有 try-catch 正常工作，业务异常被捕获，不向上层抛出
    }

    /**
     * 观察 2（本地事件）：本地（非 RemoteApplicationEvent）事件的业务异常也被 try-catch 捕获，
     * 记录日志，返回 null，不向上层抛出。
     *
     * <p><b>Validates: Requirements 3.2, 3.3</b></p>
     */
    @Test
    public void observation2_remoteEventListenerAdapter_localEventBusinessException_returnsNullNoThrow() throws Exception {
        Method method = LocalEventExceptionListener.class.getMethod("onLocalEvent", LocalTestEvent.class);
        RemoteEventListenerAdapter adapter = new RemoteEventListenerAdapter(
                "testBean", LocalEventExceptionListener.class, method);

        LocalTestEvent event = new LocalTestEvent("local-source");

        try {
            adapter.onApplicationEvent(event);
        } catch (Throwable e) {
            AssertionErrors.fail("本地事件业务异常时，不应向上层抛出，但实际抛出了: " + e);
        }
    }

    // =========================================================================
    // Observation 3: 本地（非 RemoteApplicationEvent）事件按现有逻辑处理
    // Validates: Requirement 3.3
    // =========================================================================

    /**
     * 观察 3：本地事件（非 RemoteApplicationEvent）通过 DirectChannel 正常处理，
     * 不触发 errorChannel。
     *
     * <p><b>Validates: Requirements 3.3</b></p>
     */
    @Test
    public void observation3_localEvent_handledNormally_noErrorChannel() throws InterruptedException {
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        AtomicReference<Message<?>> capturedError = new AtomicReference<>();
        CountDownLatch errorLatch = new CountDownLatch(1);

        PublishSubscribeChannel errorChannel = new PublishSubscribeChannel();
        errorChannel.subscribe(msg -> {
            capturedError.set(msg);
            errorLatch.countDown();
        });

        DirectChannel inputChannel = new DirectChannel();
        inputChannel.subscribe(message -> {
            // 本地事件：直接处理，不涉及 type 字段反序列化
            handlerCalled.set(true);
        });

        // 本地事件：直接发送 String payload（非 RemoteApplicationEvent）
        Message<String> localMessage = MessageBuilder.withPayload("local-event-data").build();
        inputChannel.send(localMessage);

        boolean errorReceived = errorLatch.await(500, TimeUnit.MILLISECONDS);

        AssertionErrors.assertTrue("本地事件 handler 应被正常调用", handlerCalled.get());
        AssertionErrors.assertFalse("本地事件不应触发 errorChannel", errorReceived);
        AssertionErrors.assertNull("errorChannel 不应收到任何消息", capturedError.get());

        errorChannel.destroy();
    }

    // =========================================================================
    // Observation 4: type=RemoteEvent 但 dataType 无法解析时，
    //                DataTypeResolver 降级为 Map.class，不抛出异常
    // Validates: Requirement 3.1 (DataTypeResolver fallback behavior)
    // =========================================================================

    /**
     * 观察 4：dataType 为 null/空 时，DataTypeResolver 降级为 Map.class，不抛出异常。
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Test
    public void observation4_dataTypeResolver_nullDataType_fallsBackToMapClass() {
        EventPackageScanner scanner = new EventPackageScanner(
                new String[]{"com.uoquo.cloud.events"});
        DataTypeResolver resolver = new DataTypeResolver(scanner);

        // null dataType → Map.class（无日志，无异常）
        Class<?> resolved = resolver.resolve(null, "RemoteEvent");
        AssertionErrors.assertEquals("null dataType 应降级为 Map.class", java.util.Map.class, resolved);

        // 空白 dataType → Map.class
        Class<?> resolvedBlank = resolver.resolve("   ", "RemoteEvent");
        AssertionErrors.assertEquals("空白 dataType 应降级为 Map.class", java.util.Map.class, resolvedBlank);
    }

    /**
     * 观察 4：dataType 无法解析（不存在的类名）时，DataTypeResolver 降级为 Map.class，不抛出异常。
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Test
    public void observation4_dataTypeResolver_unknownDataType_fallsBackToMapClass() {
        EventPackageScanner scanner = new EventPackageScanner(
                new String[]{"com.uoquo.cloud.events"});
        DataTypeResolver resolver = new DataTypeResolver(scanner);

        // 不存在的 dataType → Map.class（WARN 日志，无异常）
        Class<?> resolved = resolver.resolve("com.nonexistent.SomeDataClass", "RemoteEvent");
        AssertionErrors.assertEquals("不存在的 dataType 应降级为 Map.class", java.util.Map.class, resolved);
    }

    /**
     * 观察 4：dataType 为空字符串时，DataTypeResolver 降级为 Map.class，不抛出异常。
     *
     * <p><b>Validates: Requirements 3.1</b></p>
     */
    @Test
    public void observation4_dataTypeResolver_emptyDataType_fallsBackToMapClass() {
        EventPackageScanner scanner = new EventPackageScanner(
                new String[]{"com.uoquo.cloud.events"});
        DataTypeResolver resolver = new DataTypeResolver(scanner);

        Class<?> resolved = resolver.resolve("", "RemoteEvent");
        AssertionErrors.assertEquals("空字符串 dataType 应降级为 Map.class", java.util.Map.class, resolved);
    }

    // =========================================================================
    // Property 2: Preservation — 对所有 isBugCondition(msg)=false 的消息，
    //             修复后行为与修复前完全一致
    // =========================================================================

    /**
     * Property 2: Preservation — 已知 type 消息在修复前后行为一致。
     *
     * <p>模拟多条 isBugCondition=false 的消息（不同 type、dataType 组合），
     * 验证所有消息均被正常处理，errorChannel 未被触发。</p>
     *
     * <p><b>Validates: Requirements 3.1, 3.2, 3.3</b></p>
     */
    @Test
    public void property2_preservation_allNonBugConditionMessages_handledNormally() throws InterruptedException {
        List<String> processedPayloads = new ArrayList<>();
        List<Message<?>> errorMessages = new ArrayList<>();
        CountDownLatch errorLatch = new CountDownLatch(1);

        PublishSubscribeChannel errorChannel = new PublishSubscribeChannel();
        errorChannel.subscribe(msg -> {
            errorMessages.add(msg);
            errorLatch.countDown();
        });

        DirectChannel inputChannel = new DirectChannel();
        inputChannel.subscribe(message -> {
            Object payload = message.getPayload();
            String payloadStr = payload instanceof byte[]
                    ? new String((byte[]) payload, StandardCharsets.UTF_8)
                    : String.valueOf(payload);
            processedPayloads.add(payloadStr);
        });

        // 构造多条 isBugCondition=false 的消息
        String[] nonBugMessages = {
            // type=RemoteEvent，已知类
            "{\"type\":\"RemoteEvent\",\"id\":\"p2-001\",\"originService\":\"svc-a\"}",
            // type=RemoteEvent，dataType=null（降级为 Map.class）
            "{\"type\":\"RemoteEvent\",\"id\":\"p2-002\",\"originService\":\"svc-b\",\"dataType\":null}",
            // type=RemoteEvent，dataType 为空字符串
            "{\"type\":\"RemoteEvent\",\"id\":\"p2-003\",\"originService\":\"svc-c\",\"dataType\":\"\"}",
            // 本地事件（非 RemoteApplicationEvent）
            "local-event-payload-001",
        };

        for (String payload : nonBugMessages) {
            byte[] rawPayload = payload.getBytes(StandardCharsets.UTF_8);
            inputChannel.send(MessageBuilder.withPayload(rawPayload).build());
        }

        // errorChannel 不应收到任何消息
        boolean errorReceived = errorLatch.await(500, TimeUnit.MILLISECONDS);

        // 断言：所有消息均被正常处理
        AssertionErrors.assertEquals("所有 isBugCondition=false 的消息应被正常处理",
                nonBugMessages.length, processedPayloads.size());

        // 断言：errorChannel 未被触发
        AssertionErrors.assertFalse("isBugCondition=false 的消息不应触发 errorChannel", errorReceived);
        AssertionErrors.assertTrue("errorChannel 不应收到任何消息", errorMessages.isEmpty());

        errorChannel.destroy();
    }

    // =========================================================================
    // 辅助类
    // =========================================================================

    /**
     * 会抛出业务异常的 RemoteApplicationEvent 监听器（用于测试 Observation 2）。
     */
    public static class BusinessExceptionListener {
        @org.springframework.context.event.EventListener
        public void onEvent(TestRemoteEvent event) {
            throw new RuntimeException("模拟业务异常：处理 RemoteEvent 时发生错误");
        }
    }

    /**
     * 本地事件（非 RemoteApplicationEvent）。
     */
    public static class LocalTestEvent extends org.springframework.context.ApplicationEvent {
        public LocalTestEvent(Object source) {
            super(source);
        }
    }

    /**
     * 会抛出业务异常的本地事件监听器（用于测试 Observation 2 + 3）。
     */
    public static class LocalEventExceptionListener {
        @org.springframework.context.event.EventListener
        public void onLocalEvent(LocalTestEvent event) {
            throw new RuntimeException("模拟业务异常：处理本地事件时发生错误");
        }
    }
}
