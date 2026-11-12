package com.uoquo.cloud.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uoquo.cloud.events.RemoteEvent;
import com.uoquo.utils.json.JsonUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RemoteEvent 的公共信息委托给 EventHeader 之后：事件ID（eventId）、JSON 格式、路由信息。
 */
@DisplayName("RemoteEvent 公共信息委托")
class RemoteEventHeaderTest {

    @Test
    @DisplayName("eventId 是业务的事件ID，与 Bus 自己使用的 id 互不影响；发布前为空")
    @SuppressWarnings("deprecation")
    void eventIdIsSeparateFromBusId() {
        TestRemoteEvent event = new TestRemoteEvent("BIZ", "OP", "OK");
        String busId = event.getId();
        assertNull(event.getEventId());
        assertNotNull(busId);

        event.setEventId("evt-1");

        assertEquals("evt-1", event.getEventId());
        assertEquals(busId, event.getId());
    }

    @Test
    @DisplayName("构造函数的参数写入对应字段；路由信息由构造参数决定")
    void constructorFields() {
        RemoteEvent<String> event = new RemoteEvent<>("BIZ", "OP", "OK", "DONE", "svc-b");

        assertEquals("BIZ", event.getBusinessType());
        assertEquals("OP", event.getOperationType());
        assertEquals("OK", event.getOperationResult());
        assertEquals("DONE", event.getBusinessStatus());
        assertEquals("svc-b:**", event.getDestinationService());
    }

    @Test
    @DisplayName("目标服务：广播为 **，定向为 服务名:**，空白与 \"null\" 视为未指定")
    void destinationService() {
        assertEquals("**", new RemoteEvent<String>("BIZ", "OP", "OK").getDestinationService());
        assertEquals("svc-b:**", new RemoteEvent<String>("BIZ", "OP", "OK", null, "svc-b").getDestinationService());
        assertEquals("svc-b:**", new RemoteEvent<String>("BIZ", "OP", "OK", null, "svc-b:**").getDestinationService());
        assertEquals("**", new RemoteEvent<String>("BIZ", "OP", "OK", null, "  ").getDestinationService());
        assertEquals("**", new RemoteEvent<String>("BIZ", "OP", "OK", null, "null").getDestinationService());
    }

    @Test
    @DisplayName("业务类型、操作类型为空时构造失败")
    void constructorValidates() {
        assertThrows(IllegalArgumentException.class, () -> new RemoteEvent<String>(null, "OP", "OK"));
        assertThrows(IllegalArgumentException.class, () -> new RemoteEvent<String>("BIZ", " ", "OK"));
    }

    @Test
    @DisplayName("JSON 保持扁平格式，不出现 header；有 eventId，没有 destination；公共信息往返一致")
    void jsonIsFlatAndRoundTrips() throws Exception {
        RemoteEvent<String> event = new RemoteEvent<>("BIZ", "OP", "OK", null, "svc-b");
        event.setEventId("evt-1");
        event.setOperatorId("u-1");
        event.setBusinessId("b-1");
        event.setRemarks("remark");
        event.addExtension("k", "v");
        event.setNewData("data");

        String json = JsonUtil.serialize(event);
        JsonNode node = new ObjectMapper().readTree(json);

        assertEquals("RemoteEvent", node.get("type").asText());
        assertEquals("evt-1", node.get("eventId").asText());
        assertEquals("svc-b:**", node.get("destinationService").asText());
        assertTrue(node.has("id"), "Bus 自己的 id 仍然存在：" + json);
        assertNotEquals("evt-1", node.get("id").asText());
        assertFalse(node.has("destination"), json);
        assertEquals("BIZ", node.get("businessType").asText());
        assertEquals("u-1", node.get("operatorId").asText());
        assertEquals("remark", node.get("remarks").asText());
        assertEquals("java.lang.String", node.get("dataType").asText());
        assertFalse(node.has("header"), json);

        // 用 JSON 树反序列化（与 RemoteEventDeserializer 的做法一致）
        RemoteEvent<String> restored = JsonUtil.deserialize(node, RemoteEvent.class, String.class);
        assertEquals("evt-1", restored.getEventId());
        assertEquals("svc-b:**", restored.getDestinationService());
        assertEquals("u-1", restored.getOperatorId());
        assertEquals("b-1", restored.getBusinessId());
        assertEquals("v", restored.getExtension().get("k"));
        assertEquals("data", restored.getNewData());
    }

    @Test
    @DisplayName("旧版本的 JSON（id 是事件ID，带 destination）可以读取：destination 被忽略，路由以 destinationService 为准")
    void readsLegacyJson() throws Exception {
        RemoteEvent<String> event = new RemoteEvent<>("BIZ", "OP", "OK", null, "svc-b");
        event.setNewData("data");
        ObjectNode node = (ObjectNode) new ObjectMapper().readTree(JsonUtil.serialize(event));
        // 旧版本：id 保存事件ID，destination 是目标服务名（这里故意与路由不一致）
        node.put("id", "K7D9N00SM9TXV02Y");
        node.put("destination", "svc-x");
        node.remove("eventId");

        RemoteEvent<String> restored = JsonUtil.deserialize(node, RemoteEvent.class, String.class);

        assertNull(restored.getEventId());
        assertEquals("svc-b:**", restored.getDestinationService());
        assertEquals("data", restored.getNewData());

        // 旧版本广播事件：destination 为 null，destinationService 为 **
        node.putNull("destination");
        node.put("destinationService", "**");
        assertEquals("**", JsonUtil.deserialize(node, RemoteEvent.class, String.class).getDestinationService());
    }
}
