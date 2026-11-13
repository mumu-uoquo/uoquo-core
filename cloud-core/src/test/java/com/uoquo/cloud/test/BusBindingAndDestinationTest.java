package com.uoquo.cloud.test;

import com.uoquo.cloud.config.BusOutputBindingPostProcessor;
import com.uoquo.cloud.events.RemoteEventDestination;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目标服务匹配（没有 Bus 的 ServiceMatcher 时使用）、Bus 输出绑定在启动阶段创建的配置。
 */
@DisplayName("目标服务匹配 / Bus 输出绑定预创建")
class BusBindingAndDestinationTest {

    /* ======================== RemoteEventDestination ======================== */

    @Test
    @DisplayName("目标为空或通配：发给所有服务")
    void blankAndWildcardMatchEveryService() {
        assertTrue(RemoteEventDestination.isForService(null, "svc-a", null));
        assertTrue(RemoteEventDestination.isForService("", "svc-a", null));
        assertTrue(RemoteEventDestination.isForService("  ", "svc-a", null));
        assertTrue(RemoteEventDestination.isForService("**", "svc-a", null));
    }

    @Test
    @DisplayName("目标是服务名：只匹配同名服务")
    void serviceNameMatchesExactly() {
        assertTrue(RemoteEventDestination.isForService("svc-a", "svc-a", null));
        assertTrue(RemoteEventDestination.isForService("svc-a:**", "svc-a", null));
        assertFalse(RemoteEventDestination.isForService("svc-b", "svc-a", null));
        assertFalse(RemoteEventDestination.isForService("svc-b:**", "svc-a", null));
        assertFalse(RemoteEventDestination.isForService("svc-ab", "svc-a", null));
    }

    @Test
    @DisplayName("目标带通配符的服务名")
    void wildcardServiceName() {
        assertTrue(RemoteEventDestination.isForService("svc-*", "svc-a", null));
        assertFalse(RemoteEventDestination.isForService("other-*", "svc-a", null));
    }

    @Test
    @DisplayName("目标是实例ID：配置了实例ID时按实例ID匹配")
    void instanceIdMatchesWhenConfigured() {
        assertTrue(RemoteEventDestination.isForService("svc-a:011", "svc-a", "svc-a:011"));
        assertFalse(RemoteEventDestination.isForService("svc-a:012", "svc-a", "svc-a:011"));
        // 没有配置实例ID时，实例级的目标无法匹配服务名
        assertFalse(RemoteEventDestination.isForService("svc-a:011", "svc-a", null));
    }

    @Test
    @DisplayName("本服务的标识都取不到：无法判断，按发给本服务处理")
    void unknownLocalServiceIsAccepted() {
        assertTrue(RemoteEventDestination.isForService("svc-b", null, null));
        assertTrue(RemoteEventDestination.isForService("svc-b", "", " "));
        assertTrue(RemoteEventDestination.isForService("svc-b", com.uoquo.cloud.events.UoquoOriginFactory.TRANSIENT_ORIGIN, null));
    }

    /* ======================== BusOutputBindingPostProcessor ======================== */

    private static String bindingsAfterProcess(StandardEnvironment env) {
        BusOutputBindingPostProcessor processor = new BusOutputBindingPostProcessor();
        processor.setEnvironment(env);
        processor.postProcessBeanFactory(new DefaultListableBeanFactory());
        return env.getProperty("spring.cloud.stream.output-bindings");
    }

    @Test
    @DisplayName("没有配置 output-bindings：设置为 springCloudBusOutput")
    void outputBindingsAddedWhenAbsent() {
        assertEquals("springCloudBusOutput", bindingsAfterProcess(new StandardEnvironment()));
    }

    @Test
    @DisplayName("已有 output-bindings：保留原值并追加")
    void outputBindingsAppendedToExisting() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of("spring.cloud.stream.output-bindings", "foo;bar")));

        assertEquals("foo;bar;springCloudBusOutput", bindingsAfterProcess(env));
    }

    @Test
    @DisplayName("已经包含 springCloudBusOutput：不重复追加")
    void outputBindingsNotDuplicated() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("spring.cloud.stream.output-bindings", "foo; springCloudBusOutput")));

        assertEquals("foo; springCloudBusOutput", bindingsAfterProcess(env));
    }
}
