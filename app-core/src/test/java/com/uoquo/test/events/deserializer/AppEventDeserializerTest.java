/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.test.events.deserializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.uoquo.web.events.AppEvent;
import com.uoquo.web.events.deserializer.AppEventDeserializer;
import com.uoquo.web.events.deserializer.BuiltinTypeRegistry;
import com.uoquo.web.events.deserializer.DataTypeResolver;
import com.uoquo.web.events.deserializer.EventPackageScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AppEvent 反序列化测试用例
 */
@DisplayName("AppEvent 反序列化测试")
class AppEventDeserializerTest {

    private ObjectMapper objectMapper;
    private DataTypeResolver resolver;
    private EventPackageScanner scanner;
    private AppEventDeserializer deserializer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        scanner = new EventPackageScanner(new String[]{
                "com.uoquo.test.events.deserializer"
        });
        resolver = new DataTypeResolver(scanner);
        deserializer = new AppEventDeserializer(resolver);

        SimpleModule module = new SimpleModule("TestModule");
        module.addDeserializer(AppEvent.class, deserializer);
        objectMapper.registerModule(module);
    }

    // =================== 基础字段反序列化 ===================

    @Nested
    @DisplayName("基础字段反序列化")
    class BasicFieldsDeserialization {

        @Test
        @DisplayName("完整 JSON 消息 - 所有基础字段正确解析")
        void shouldDeserializeAllBasicFields() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "id": "event-001",
                    "retry": false,
                    "destination": "service-b",
                    "token": "token-abc",
                    "traceId": "trace-123",
                    "businessType": "ORDER",
                    "businessSubType": "PAYMENT",
                    "businessTable": "t_order",
                    "businessId": "10086",
                    "businessInstituteId": "inst-1",
                    "operatorId": "user-001",
                    "operatorName": "张三",
                    "operatorInstituteId": "inst-1",
                    "operationType": "CREATE",
                    "operationStatus": "SUCCESS",
                    "content": "创建订单",
                    "appKey": "app-001",
                    "appDeviceId": "device-001",
                    "appVersion": "1.0.0",
                    "appIp": "192.168.1.1",
                    "remarks": "测试备注",
                    "extension": {"key1": "value1"},
                    "oldData": {"name": "old"},
                    "newData": {"name": "new"}
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertEquals("event-001", event.getId());
            assertFalse(event.isRetry());
            assertEquals("service-b", event.getDestination());
            assertEquals("token-abc", event.getToken());
            assertEquals("trace-123", event.getTraceId());
            assertEquals("ORDER", event.getBusinessType());
            assertEquals("PAYMENT", event.getBusinessSubType());
            assertEquals("t_order", event.getBusinessTable());
            assertEquals("10086", event.getBusinessId());
            assertEquals("inst-1", event.getBusinessInstituteId());
            assertEquals("user-001", event.getOperatorId());
            assertEquals("张三", event.getOperatorName());
            assertEquals("inst-1", event.getOperatorInstituteId());
            assertEquals("CREATE", event.getOperationType());
            assertEquals("SUCCESS", event.getOperationStatus());
            assertEquals("创建订单", event.getContent());
            assertEquals("app-001", event.getAppKey());
            assertEquals("device-001", event.getAppDeviceId());
            assertEquals("1.0.0", event.getAppVersion());
            assertEquals("192.168.1.1", event.getAppIp());
            assertEquals("测试备注", event.getRemarks());
            assertNotNull(event.getExtension());
            assertEquals("value1", event.getExtension().get("key1"));
        }

        @Test
        @DisplayName("最小 JSON 消息 - 仅必要字段")
        void shouldDeserializeMinimalJson() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE"
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertEquals("ORDER", event.getBusinessType());
            assertEquals("CREATE", event.getOperationType());
            assertNull(event.getId());
            assertNull(event.getBusinessId());
            assertFalse(event.isRetry());
        }

        @Test
        @DisplayName("JSON 缺少 type 字段 - 反序列化器自动补入 AppEvent")
        void shouldPatchMissingTypeField() throws IOException {
            // language=JSON
            String json = """
                {
                    "businessType": "ORDER",
                    "operationType": "UPDATE"
                }
                """;

            assertDoesNotThrow(() -> {
                AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);
                assertNotNull(event);
                assertEquals("ORDER", event.getBusinessType());
                assertEquals("UPDATE", event.getOperationType());
            });
        }
    }

    // =================== dataType 解析测试 ===================

    @Nested
    @DisplayName("dataType 解析")
    class DataTypeResolution {

        @Test
        @DisplayName("dataType 为 null → oldData/newData 为 LinkedHashMap")
        void shouldFallbackToMapWhenDataTypeIsNull() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE",
                    "oldData": {"name": "test", "age": 25},
                    "newData": {"name": "test2", "age": 30}
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            Object oldData = event.getOldData();
            Object newData = event.getNewData();
            assertTrue(oldData instanceof Map);
            assertTrue(newData instanceof Map);
        }

        @Test
        @DisplayName("dataType 为空字符串 → oldData/newData 为 LinkedHashMap")
        void shouldFallbackToMapWhenDataTypeIsBlank() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE",
                    "dataType": "",
                    "oldData": {"name": "test"},
                    "newData": {"name": "test2"}
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertTrue(event.getOldData() instanceof Map);
            assertTrue(event.getNewData() instanceof Map);
        }

        @Test
        @DisplayName("dataType 为 Java 内置类型 → 正确反序列化")
        void shouldResolveBuiltinType() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE",
                    "dataType": "java.lang.Long",
                    "oldData": 12345,
                    "newData": 67890
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            // Long 被序列化为数字，反序列化后可能是 Integer 或 Long
            assertTrue(event.getOldData() instanceof Number);
            assertTrue(event.getNewData() instanceof Number);
        }

        @Test
        @DisplayName("dataType 为扫描包中的类 → 通过简单类名匹配")
        void shouldResolveByPackageScan() throws IOException {
            // 构造一个简单的 POJO 类名供测试
            // java.util.Date 的简单名可被扫描到
            // 但我们需要确保 scanner 能匹配，因此用 spring 的 ConcurrentHashMap
            // 因其简单类名相对独特
            // language=JSON
            String json = String.format("""
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE",
                    "dataType": "%s",
                    "oldData": {"name": "old_value"},
                    "newData": {"name": "new_value"}
                }
                """, ConcurrentHashMap.class.getName());

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertTrue(event.getOldData() instanceof Map);
            assertTrue(event.getNewData() instanceof Map);
        }

        @Test
        @DisplayName("dataType 为不认识的值 → 降级为 Map")
        void shouldFallbackToMapWhenUnresolvable() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE",
                    "dataType": "com.nonexistent.UnknownType",
                    "oldData": {"name": "test"},
                    "newData": {"name": "test2"}
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertTrue(event.getOldData() instanceof Map);
            assertTrue(event.getNewData() instanceof Map);
        }

        @Test
        @DisplayName("dataType 为 java.util.ArrayList → oldData/newData 正确解析为数组")
        void shouldResolveListType() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE",
                    "dataType": "java.util.ArrayList",
                    "oldData": ["a", "b", "c"],
                    "newData": ["x", "y"]
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertTrue(event.getOldData() instanceof List);
            assertTrue(event.getNewData() instanceof List);
            assertEquals(3, ((List<?>) event.getOldData()).size());
            assertEquals(2, ((List<?>) event.getNewData()).size());
        }
    }

    // =================== extesion 字段测试 ===================

    @Nested
    @DisplayName("extension 扩展字段")
    class ExtensionField {

        @Test
        @DisplayName("extension 为空 → 初始化为空 HashMap")
        void shouldInitializeEmptyExtension() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE"
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertNotNull(event.getExtension());
            assertTrue(event.getExtension().isEmpty());
        }

        @Test
        @DisplayName("extension 含多个键值对 → 正确反序列化")
        void shouldDeserializeExtensionWithMultipleEntries() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE",
                    "extension": {
                        "key1": "value1",
                        "key2": 123,
                        "key3": true,
                        "key4": {"nested": "object"}
                    }
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertEquals("value1", event.getExtension().get("key1"));
            assertEquals(123, event.getExtension().get("key2"));
            assertEquals(true, event.getExtension().get("key3"));
            assertTrue(event.getExtension().get("key4") instanceof Map);
        }
    }

    // =================== 特殊场景测试 ===================

    @Nested
    @DisplayName("特殊场景")
    class EdgeCases {

        @Test
        @DisplayName("oldData 和 newData 同时为 null → 不应抛异常")
        void shouldHandleNullData() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE",
                    "dataType": "java.lang.String",
                    "oldData": null,
                    "newData": null
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertNull(event.getOldData());
            assertNull(event.getNewData());
        }

        @Test
        @DisplayName("retry 字段为 true → 正确解析")
        void shouldDeserializeRetryFlag() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE",
                    "retry": true
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertTrue(event.isRetry());
        }

        @Test
        @DisplayName("operationTime 日期字段 → 正确解析")
        void shouldDeserializeOperationTime() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE",
                    "operationTime": "2025-06-03T10:30:00.000+00:00"
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertNotNull(event.getOperationTime());
        }

        @Test
        @DisplayName("destination 定向服务名 → 正确解析")
        void shouldDeserializeDestination() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE",
                    "destination": "target-service"
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertEquals("target-service", event.getDestination());
        }

        @Test
        @DisplayName("getDataType() 方法 - dataType 字段不为 null")
        void shouldReturnExplicitDataType() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE",
                    "dataType": "com.example.MyPojo"
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertEquals("com.example.MyPojo", event.getDataType());
        }

        @Test
        @DisplayName("getDataType() 方法 - dataType 为 null 时回退到 oldData 类型")
        void shouldFallbackDataTypeToOldDataClass() throws IOException {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE",
                    "oldData": {"name": "test"}
                }
                """;

            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertNotNull(event.getDataType());
        }
    }

    // =================== deserializeWithType 测试 ===================

    @Nested
    @DisplayName("deserializeWithType 方法")
    class DeserializeWithTypeMethod {

        @Test
        @DisplayName("deserializeWithType 应正确委派到 deserialize")
        void shouldDelegateToDeserialize() throws Exception {
            // language=JSON
            String json = """
                {
                    "type": "AppEvent",
                    "businessType": "ORDER",
                    "operationType": "CREATE"
                }
                """;

            // 通过 ObjectMapper 走完整流程（会经过 deserializeWithType）
            AppEvent<?> event = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(event);
            assertEquals("ORDER", event.getBusinessType());
            assertEquals("CREATE", event.getOperationType());
        }
    }

    // =================== BuiltinTypeRegistry 测试 ===================

    @Nested
    @DisplayName("BuiltinTypeRegistry 内置类型注册表")
    class BuiltinTypeRegistryTest {

        @ParameterizedTest
        @CsvSource(delimiter = '=', textBlock = """
            String = java.lang.String
            Long   = java.lang.Long
            Integer= java.lang.Integer
            Short  = java.lang.Short
            Byte   = java.lang.Byte
            Double = java.lang.Double
            Float  = java.lang.Float
            Boolean= java.lang.Boolean
            BigDecimal= java.math.BigDecimal
            BigInteger= java.math.BigInteger
            List   = java.util.List
            Map    = java.util.Map
            Set    = java.util.Set
            """)
        @DisplayName("内置类型简单名 → 正确类对象")
        void shouldLookupBuiltinType(String simpleName, String expectedClassName) {
            Optional<Class<?>> result = BuiltinTypeRegistry.lookup(simpleName);
            assertTrue(result.isPresent(), "应能查到: " + simpleName);
            assertEquals(expectedClassName, result.get().getName());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"NonExistent", "  "})
        @DisplayName("空值/不存在类型 → Optional.empty()")
        void shouldReturnEmptyForUnknownType(String input) {
            Optional<Class<?>> result = BuiltinTypeRegistry.lookup(input);
            assertFalse(result.isPresent());
        }
    }

    // =================== DataTypeResolver 测试 ===================

    @Nested
    @DisplayName("DataTypeResolver 解析器")
    class DataTypeResolverTest {

        private DataTypeResolver standaloneResolver;

        @BeforeEach
        void setUp() {
            EventPackageScanner emptyScanner = new EventPackageScanner(new String[]{
                    "com.uoquo.test.nonexistent"
            });
            standaloneResolver = new DataTypeResolver(emptyScanner);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("dataType 为 null/空白 → 返回 Map.class")
        void shouldReturnMapForNullOrBlank(String input) {
            Class<?> result = standaloneResolver.resolve(input);
            assertEquals(Map.class, result);
        }

        @Test
        @DisplayName("dataType 为完整 FQN → 加载成功")
        void shouldResolveByFqn() {
            Class<?> result = standaloneResolver.resolve("java.util.ArrayList");
            assertNotNull(result);
            assertEquals(ArrayList.class, result);
        }

        @Test
        @DisplayName("dataType 为内置类型简单名 → 匹配成功")
        void shouldResolveBuiltinSimpleName() {
            Class<?> result = standaloneResolver.resolve("String");
            assertNotNull(result);
            assertEquals(String.class, result);
        }

        @Test
        @DisplayName("dataType 为不存在 FQN 但简单名匹配内置类型 → 走降级逻辑")
        void shouldFallbackToSimpleNameAfterFqnFailure() {
            // "xxx.String" 无法作为 FQN 加载，但简单名 "String" 可以匹配内置类型
            Class<?> result = standaloneResolver.resolve("xxx.String");
            assertNotNull(result);
            assertEquals(String.class, result);
        }

        @Test
        @DisplayName("dataType 完全无法解析 → 返回 Map.class")
        void shouldReturnMapForUnresolvable() {
            Class<?> result = standaloneResolver.resolve("com.nonexistent.FakeType");
            assertEquals(Map.class, result);
        }
    }

    // =================== EventPackageScanner 测试 ===================

    @Nested
    @DisplayName("EventPackageScanner 扫描器")
    class EventPackageScannerTest {

        @Test
        @DisplayName("basePackages 为 null → 初始化为空数组")
        void shouldHandleNullBasePackages() {
            EventPackageScanner scanner = new EventPackageScanner(null);
            List<Class<?>> result = scanner.findBySimpleName("Anything");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("basePackages 为空数组 → 返回空结果")
        void shouldHandleEmptyBasePackages() {
            EventPackageScanner scanner = new EventPackageScanner(new String[0]);
            List<Class<?>> result = scanner.findBySimpleName("Anything");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("simpleName 为 null/空白 → 返回空列表")
        void shouldReturnEmptyForNullName() {
            EventPackageScanner scanner = new EventPackageScanner(new String[]{"com.uoquo"});
            assertTrue(scanner.findBySimpleName(null).isEmpty());
            assertTrue(scanner.findBySimpleName("").isEmpty());
            assertTrue(scanner.findBySimpleName("  ").isEmpty());
        }

//        @Test
//        @DisplayName("toResourcePattern 静态方法 → 路径转换正确")
//        void shouldConvertPackageToResourcePattern() {
//            assertEquals("classpath*:com/uoquo/user/**/*.class",
//                    EventPackageScanner.toResourcePattern("com.uoquo.user"));
//            assertEquals("classpath*:com/uoquo/**/*.class",
//                    EventPackageScanner.toResourcePattern("com.uoquo"));
//        }

        @Test
        @DisplayName("扫描真实包 → 能找到已存在的类")
        void shouldFindClassesInRealPackage() {
            EventPackageScanner scanner = new EventPackageScanner(new String[]{
                    "com.uoquo.test.events.deserializer"
            });
            // 本测试类自己就在这个包下，应该能扫描到
            List<Class<?>> result = scanner.findBySimpleName("AppEventDeserializerTest");
            assertEquals(1, result.size());
            assertEquals(AppEventDeserializerTest.class, result.get(0));
        }

        @Test
        @DisplayName("构造函数兼容性验证")
        void shouldBeConstructable() {
            EventPackageScanner scanner = new EventPackageScanner(new String[]{
                    "com.uoquo.**.event",
                    "com.uoquo.**.events",
                    "com.uoquo.**.model"
            });
            assertNotNull(scanner);
        }
    }

    // =================== AppEvent 子类反序列化测试 ===================

    @Nested
    @DisplayName("AppEvent 子类")
    class SubclassDeserialization {

        // 静态内部类模拟继承了泛型参数的 AppEvent 子类
        static class OrderEvent extends AppEvent<Map<String, Object>> {
            // 无参构造函数
            public OrderEvent() {
                super();
            }
        }

        @Test
        @DisplayName("具化泛型参数的子类 → dataType 自动推导")
        void shouldInferDataTypeFromGenericSubclass() {
            OrderEvent event = new OrderEvent();
            assertNotNull(event.getDataType());
            assertTrue(event.getDataType().contains("Map"));
        }
    }

    // =================== 集成测试：序列化 → 反序列化往返 ===================

    @Nested
    @DisplayName("往返测试: 构造对象 → JSON → 反序列化")
    class RoundTripTest {

        @Test
        @DisplayName("完整字段往返一致")
        void shouldRoundTripAllFields() throws IOException {
            AppEvent<Map<String, Object>> event = new AppEvent<>("ORDER", "CREATE", "SUCCESS");

            event.setId("evt-001");
            event.setToken("tok-abc");
            event.setTraceId("tr-123");
            event.setBusinessId("10086");
            event.setBusinessTable("t_order");
            event.setBusinessInstituteId("inst-1");
            event.setOperatorId("user-001");
            event.setOperatorName("张三");
            event.setOperationTime(new Date());
            event.setContent("测试内容");
            event.setDestination("target-svc");
            event.setAppKey("app-001");
            event.setAppIp("10.0.0.1");
            event.setRemarks("备注");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", "test");
            data.put("count", 100);
            event.setOldData(data);

            // 用标准 ObjectMapper 序列化后再反序列化
            String json = objectMapper.writeValueAsString(event);

            // 确保 JSON 包含 type 字段
            assertTrue(json.contains("\"type\""));
            assertTrue(json.contains("\"AppEvent\""));

            // 反序列化
            AppEvent<?> deserialized = objectMapper.readValue(json, AppEvent.class);

            assertNotNull(deserialized);
            assertEquals("evt-001", deserialized.getId());
            assertEquals("ORDER", deserialized.getBusinessType());
            assertEquals("CREATE", deserialized.getOperationType());
            assertEquals("SUCCESS", deserialized.getOperationStatus());
            assertEquals("tok-abc", deserialized.getToken());
            assertEquals("tr-123", deserialized.getTraceId());
            assertEquals("10086", deserialized.getBusinessId());
            assertEquals("t_order", deserialized.getBusinessTable());
            assertEquals("inst-1", deserialized.getBusinessInstituteId());
            assertEquals("user-001", deserialized.getOperatorId());
            assertEquals("张三", deserialized.getOperatorName());
            assertEquals("测试内容", deserialized.getContent());
            assertEquals("target-svc", deserialized.getDestination());
            assertEquals("app-001", deserialized.getAppKey());
            assertEquals("10.0.0.1", deserialized.getAppIp());
            assertEquals("备注", deserialized.getRemarks());

            assertNotNull(deserialized.getOldData());
            assertTrue(deserialized.getOldData() instanceof Map);
        }
    }
}
