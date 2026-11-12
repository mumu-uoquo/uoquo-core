package com.uoquo.test.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.events.AppEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 事件的公共信息委托给 EventHeader 之后：访问方法、复制、JSON 格式保持不变。
 */
@DisplayName("HeaderBackedEvent 公共信息委托")
class HeaderBackedEventTest {

    /** 业务数据 */
    public static class Pojo {
        public String name;
        public BigDecimal amount;
        public long count;
    }

    private static AppEvent<Pojo> fullEvent() {
        AppEvent<Pojo> event = new AppEvent<>("ORDER", "CREATE", "SUCCESS", "PAID");
        event.setEventId("evt-1");
        event.setRetry(true);
        event.setToken("tk");
        event.setTraceId("tr");
        event.setBusinessSubType("SUB");
        event.setBusinessTable("t_order");
        event.setBusinessId("b-1");
        event.setBusinessInstituteId("i-1");
        event.setOperatorId("u-1");
        event.setOperatorName("alice");
        event.setOperatorInstituteId("i-2");
        event.setOperationTime(new Date(1_700_000_000_000L));
        event.setContent("content");
        event.addExtension("k", "v");
        event.setAppKey("ak");
        event.setAppDeviceId("dev");
        event.setAppVersion("1.0");
        event.setAppIp("10.0.0.1");
        event.setRemarks("remark");
        Pojo pojo = new Pojo();
        pojo.name = "n";
        pojo.amount = new BigDecimal("12.10");
        pojo.count = 3L;
        event.setNewData(pojo);
        return event;
    }

    @Test
    @DisplayName("所有公共信息都能读写，构造函数的参数写入对应字段")
    void accessors() {
        AppEvent<Pojo> event = fullEvent();

        assertEquals("ORDER", event.getBusinessType());
        assertEquals("CREATE", event.getOperationType());
        assertEquals("SUCCESS", event.getOperationResult());
        assertEquals("PAID", event.getBusinessStatus());
        assertEquals("evt-1", event.getEventId());
        assertTrue(event.isRetry());
        assertEquals("tk", event.getToken());
        assertEquals("tr", event.getTraceId());
        assertEquals("SUB", event.getBusinessSubType());
        assertEquals("t_order", event.getBusinessTable());
        assertEquals("b-1", event.getBusinessId());
        assertEquals("i-1", event.getBusinessInstituteId());
        assertEquals("u-1", event.getOperatorId());
        assertEquals("alice", event.getOperatorName());
        assertEquals("i-2", event.getOperatorInstituteId());
        assertEquals(new Date(1_700_000_000_000L), event.getOperationTime());
        assertEquals("content", event.getContent());
        assertEquals("v", event.getExtension().get("k"));
        assertEquals("ak", event.getAppKey());
        assertEquals("dev", event.getAppDeviceId());
        assertEquals("1.0", event.getAppVersion());
        assertEquals("10.0.0.1", event.getAppIp());
        assertEquals("remark", event.getRemarks());
    }

    @Test
    @DisplayName("setExtension：复制传入的 Map；传 null 得到空 Map")
    void extension() {
        AppEvent<Pojo> event = new AppEvent<>("A", "B", "C");
        Map<String, Object> source = new HashMap<>();
        source.put("x", 1);
        event.setExtension(source);
        source.put("y", 2);

        assertEquals(1, event.getExtension().size());
        event.setExtension(null);
        assertTrue(event.getExtension().isEmpty());
    }

    @Test
    @DisplayName("JSON 保持扁平格式：公共信息的属性名不变，不出现 header")
    void jsonIsFlat() throws Exception {
        JsonNode json = new ObjectMapper().readTree(JsonUtil.serialize(fullEvent()));

        for (String name : new String[]{"type", "eventId", "retry", "token", "traceId", "businessType", "businessSubType",
                "businessTable", "businessId", "businessInstituteId", "operatorId", "operatorName", "operatorInstituteId",
                "operationType", "operationResult", "businessStatus", "operationTime", "content", "extension", "appKey",
                "appDeviceId", "appVersion", "appIp", "remarks", "dataType", "newData"}) {
            assertTrue(json.has(name), "缺少属性：" + name + "，实际：" + json);
        }
        assertFalse(json.has("header"), json.toString());
        assertFalse(json.has("destination"), json.toString());
        assertFalse(json.has("id"), json.toString());
        assertEquals("AppEvent", json.get("type").asText());
        assertEquals("ORDER", json.get("businessType").asText());
        assertEquals("evt-1", json.get("eventId").asText());
    }

    @Test
    @DisplayName("JSON 往返：公共信息与业务数据都能还原")
    void jsonRoundTrip() {
        String json = JsonUtil.serialize(fullEvent());

        AppEvent<Pojo> restored = JsonUtil.deserialize(json, AppEvent.class, Pojo.class);

        assertEquals("ORDER", restored.getBusinessType());
        assertEquals("evt-1", restored.getEventId());
        assertTrue(restored.isRetry());
        assertEquals("alice", restored.getOperatorName());
        assertEquals("v", restored.getExtension().get("k"));
        assertEquals("remark", restored.getRemarks());
        assertEquals("n", restored.getNewData().name);
    }

    @Test
    @DisplayName("从已解析的 JSON 树反序列化，结果与「树转成字符串再解析」一致（去掉的只是重复解析）")
    void deserializeFromTreeEqualsFromTreeText() throws Exception {
        String json = JsonUtil.serialize(fullEvent());
        JsonNode tree = new ObjectMapper().readTree(json);

        // 之前的做法：先 toString，再解析一遍
        AppEvent<Pojo> viaText = JsonUtil.deserialize(tree.toString(), AppEvent.class, Pojo.class);
        // 现在的做法：直接使用 JSON 树
        AppEvent<Pojo> viaTree = JsonUtil.deserialize(tree, AppEvent.class, Pojo.class);

        assertEquals(JsonUtil.serialize(viaText), JsonUtil.serialize(viaTree));
        assertEquals(viaText.getNewData().amount, viaTree.getNewData().amount);
        assertEquals(3L, viaTree.getNewData().count);
        assertEquals(viaText.getOperationTime(), viaTree.getOperationTime());
        assertEquals("alice", viaTree.getOperatorName());
    }

    @Test
    @DisplayName("copy：只补充尚未设置的信息")
    void copyOnlyFillsMissing() {
        AppEvent<Pojo> source = fullEvent();
        AppEvent<Pojo> target = new AppEvent<>("A", "B", "C");
        target.setTraceId("mine");

        target.copy(source);

        assertEquals("mine", target.getTraceId());
        assertEquals("tk", target.getToken());
        assertEquals("u-1", target.getOperatorId());
        assertEquals("b-1", target.getBusinessId());
        assertEquals("t_order", target.getBusinessTable());
        assertEquals("ak", target.getAppKey());
    }
}
