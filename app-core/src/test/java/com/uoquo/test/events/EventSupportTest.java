package com.uoquo.test.events;

import com.uoquo.web.events.AppEvent;
import com.uoquo.web.events.EventSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EventSupport} 的单元测试：dataType 命名规范化、监听方法泛型匹配、日志链路标识。
 */
@DisplayName("EventSupport 工具方法")
class EventSupportTest {

    /** 用于模拟嵌套类的数据类型 */
    static class Outer {
        static class Inner {
        }
    }

    /** 带泛型参数的事件子类，用于验证 dataType 只取原始类型 */
    static class ListEvent extends AppEvent<List<String>> {
        ListEvent() {
            super("BIZ", "OP", "OK");
        }
    }

    /** 监听方法声明样例 */
    static class Listeners {
        public void typed(AppEvent<Outer.Inner> event) {
        }

        public void wildcard(AppEvent<?> event) {
        }

        public void subclass(ListEvent event) {
        }

        public void noParam() {
        }
    }

    @Test
    @DisplayName("simpleName：去包名、外部类前缀、泛型参数")
    void simpleName() {
        assertEquals("Inner", EventSupport.simpleName("com.a.Outer$Inner"));
        assertEquals("List", EventSupport.simpleName("java.util.List<com.x.Foo>"));
        assertEquals("Foo", EventSupport.simpleName("com.x.Foo"));
        assertEquals("Foo", EventSupport.simpleName("Foo"));
        assertNull(EventSupport.simpleName(null));
    }

    @Test
    @DisplayName("rawTypeName：去掉泛型参数，保留可加载的类名")
    void rawTypeName() {
        assertEquals("java.util.List", EventSupport.rawTypeName("java.util.List<com.x.Foo>"));
        assertEquals("com.a.Outer$Inner", EventSupport.rawTypeName("com.a.Outer$Inner"));
        assertNull(EventSupport.rawTypeName(null));
    }

    @Test
    @DisplayName("typeNameOf：带泛型参数的类型只取原始类型名")
    void typeNameOfParameterizedType() {
        Type type = ListEvent.class.getGenericSuperclass();
        Type actual = ((java.lang.reflect.ParameterizedType) type).getActualTypeArguments()[0];
        assertEquals("java.util.List", EventSupport.typeNameOf(actual));
        assertEquals("java.lang.String", EventSupport.typeNameOf(String.class));
    }

    @Test
    @DisplayName("泛型事件子类的 dataType 不带泛型参数")
    void eventDataTypeHasNoGenericArguments() {
        assertEquals("java.util.List", new ListEvent().getDataType());
    }

    @Test
    @DisplayName("declaredDataClass：仅对声明了具体泛型的 AppEvent 参数返回类型")
    void declaredDataClass() throws Exception {
        assertEquals(Outer.Inner.class, EventSupport.declaredDataClass(method("typed", AppEvent.class), AppEvent.class));
        assertNull(EventSupport.declaredDataClass(method("wildcard", AppEvent.class), AppEvent.class));
        assertNull(EventSupport.declaredDataClass(method("subclass", ListEvent.class), AppEvent.class));
        assertNull(EventSupport.declaredDataClass(method("noParam"), AppEvent.class));
    }

    @Test
    @DisplayName("matchesDataType：未声明泛型时不过滤")
    void matchesWhenNotDeclared() {
        assertTrue(EventSupport.matchesDataType(null, "x.Y", null, "anything"));
    }

    @Test
    @DisplayName("matchesDataType：嵌套类的 dataType（Outer$Inner）按简单类名匹配")
    void matchesNestedClassName() {
        assertTrue(EventSupport.matchesDataType(Outer.Inner.class, Outer.Inner.class.getName(), null, new Outer.Inner()));
        // 跨服务包名不同，只要简单类名一致即匹配
        assertTrue(EventSupport.matchesDataType(Outer.Inner.class, "other.pkg.Outer$Inner", null, null));
    }

    @Test
    @DisplayName("matchesDataType：简单类名不一致时不匹配")
    void notMatchWhenNameDiffers() {
        assertFalse(EventSupport.matchesDataType(Outer.Inner.class, "com.a.Other", null, null));
    }

    @Test
    @DisplayName("matchesDataType：数据已降级为 Map 时跳过，避免监听方法内 ClassCastException")
    void notMatchWhenDataDegradedToMap() {
        Map<String, Object> degraded = new LinkedHashMap<>();
        assertFalse(EventSupport.matchesDataType(Outer.Inner.class, "com.a.Outer$Inner", null, degraded));
        // 只有旧数据时同样校验
        assertFalse(EventSupport.matchesDataType(Outer.Inner.class, "com.a.Outer$Inner", degraded, null));
    }

    @Test
    @DisplayName("requestId：任一为空时不拼出 null")
    void requestId() {
        assertEquals("t1-b1", EventSupport.requestId("t1", "b1"));
        assertEquals("t1", EventSupport.requestId("t1", null));
        assertEquals("t1", EventSupport.requestId("t1", ""));
        assertEquals("b1", EventSupport.requestId(null, "b1"));
        assertEquals("", EventSupport.requestId(null, null));
    }

    private static Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return Listeners.class.getMethod(name, parameterTypes);
    }
}
