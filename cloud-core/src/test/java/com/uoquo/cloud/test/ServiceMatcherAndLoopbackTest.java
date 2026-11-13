package com.uoquo.cloud.test;

import com.uoquo.cloud.config.ServiceMatcherAutoConfiguration;
import com.uoquo.cloud.events.RemoteEventListenerFactory;
import com.uoquo.cloud.events.UoquoServiceMatcher;
import com.uoquo.utils.Config;
import com.uoquo.web.events.UoquoEventListenerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.bus.BusBridge;
import org.springframework.cloud.bus.BusConsumer;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.bus.DefaultBusPathMatcher;
import org.springframework.cloud.bus.PathServiceMatcher;
import org.springframework.cloud.bus.RemoteApplicationEventListener;
import org.springframework.cloud.bus.ServiceMatcher;
import org.springframework.cloud.bus.event.AckRemoteApplicationEvent;
import org.springframework.cloud.bus.event.PathDestinationFactory;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.util.AntPathMatcher;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 服务级“来自自己”判断，以及发布 / 接收两端的自处理与防回环行为。
 */
@DisplayName("ServiceMatcher 服务级判断与防回环")
class ServiceMatcherAndLoopbackTest {

    private static final String APP_NAME = "svc-a";

    @BeforeEach
    void setUp() {
        Config.put("spring.application.name", APP_NAME);
    }

    @AfterEach
    void tearDown() {
        Config.put("spring.application.name", (String) null);
    }

    /* ======================== UoquoServiceMatcher ======================== */

    @Test
    @DisplayName("RemoteEvent 的 originService 等于本服务名时，视为来自自己")
    void remoteEventFromSameServiceIsFromSelf() {
        ServiceMatcher delegate = Mockito.mock(ServiceMatcher.class);
        UoquoServiceMatcher matcher = new UoquoServiceMatcher(delegate, () -> APP_NAME);

        TestRemoteEvent event = new TestRemoteEvent("BIZ", "OP", "OK");

        assertEquals(APP_NAME, event.getOriginService());
        assertTrue(matcher.isFromSelf(event));
    }

    @Test
    @DisplayName("RemoteEvent 来自其他服务时，沿用 Bus 原有判断")
    void remoteEventFromOtherServiceUsesDelegate() {
        ServiceMatcher delegate = Mockito.mock(ServiceMatcher.class);
        UoquoServiceMatcher matcher = new UoquoServiceMatcher(delegate, () -> "svc-other");

        TestRemoteEvent event = new TestRemoteEvent("BIZ", "OP", "OK");

        assertFalse(matcher.isFromSelf(event));
        verify(delegate).isFromSelf(event);
    }

    @Test
    @DisplayName("非 RemoteEvent（Bus 自带事件）保持 Bus 原有判断")
    void nonRemoteEventUsesDelegate() {
        ServiceMatcher delegate = Mockito.mock(ServiceMatcher.class);
        RemoteApplicationEvent ack = new AckRemoteApplicationEvent(this, "svc-a:000", new PathDestinationFactory().getDestination(null),
                "**", "id-1", TestRemoteEvent.class);
        Mockito.when(delegate.isFromSelf(ack)).thenReturn(true);
        UoquoServiceMatcher matcher = new UoquoServiceMatcher(delegate, () -> APP_NAME);

        assertTrue(matcher.isFromSelf(ack));
    }

    @Test
    @DisplayName("未配置应用名时，不按服务名判断，退回 Bus 原有判断")
    void noAppNameFallsBackToDelegate() {
        Config.put("spring.application.name", (String) null);
        ServiceMatcher delegate = Mockito.mock(ServiceMatcher.class);
        UoquoServiceMatcher matcher = new UoquoServiceMatcher(delegate,
                () -> new com.uoquo.cloud.events.UoquoOriginFactory().getOrigin(null));

        TestRemoteEvent event = new TestRemoteEvent("BIZ", "OP", "OK");

        assertFalse(matcher.isFromSelf(event));
    }

    @Test
    @DisplayName("isForSelf、getBusId 直接委托")
    void delegatesOtherMethods() {
        ServiceMatcher delegate = Mockito.mock(ServiceMatcher.class);
        Mockito.when(delegate.getBusId()).thenReturn("svc-a:000");
        UoquoServiceMatcher matcher = new UoquoServiceMatcher(delegate, () -> APP_NAME);
        TestRemoteEvent event = new TestRemoteEvent("BIZ", "OP", "OK");
        Mockito.when(delegate.isForSelf(event)).thenReturn(true);

        assertEquals("svc-a:000", matcher.getBusId());
        assertTrue(matcher.isForSelf(event));
    }

    @Test
    @DisplayName("BeanPostProcessor 只包装 ServiceMatcher，且不重复包装")
    void postProcessorWrapsServiceMatcherOnce() {
        BeanPostProcessor processor = ServiceMatcherAutoConfiguration.uoquoServiceMatcherPostProcessor();
        ServiceMatcher original = Mockito.mock(ServiceMatcher.class);

        Object wrapped = processor.postProcessAfterInitialization(original, "pathServiceMatcher");
        assertInstanceOf(UoquoServiceMatcher.class, wrapped);
        assertSame(original, ((UoquoServiceMatcher) wrapped).getDelegate());

        assertSame(wrapped, processor.postProcessAfterInitialization(wrapped, "pathServiceMatcher"));

        Object other = new Object();
        assertSame(other, processor.postProcessAfterInitialization(other, "other"));
    }

    /* ======================== 发布 + 接收 ======================== */

    /** 记录收到的事件 */
    static class RecordingListener {
        final List<String> received = new CopyOnWriteArrayList<>();

        @EventListener
        public void on(com.uoquo.cloud.events.RemoteEvent<String> event) {
            received.add(event.getEventId());
        }
    }

    @Configuration
    static class BusConfig {
        @Bean
        UoquoEventListenerFactory uoquoEventListenerFactory(ObjectProvider<ServiceMatcher> serviceMatcher) {
            return new RemoteEventListenerFactory(serviceMatcher);
        }

        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }

        @Bean
        BusBridge busBridge() {
            return Mockito.mock(BusBridge.class);
        }

        @Bean
        ServiceMatcher serviceMatcher() {
            return new PathServiceMatcher(new DefaultBusPathMatcher(new AntPathMatcher(":")), "svc-a:000");
        }

        @Bean
        RemoteApplicationEventListener busRemoteApplicationEventListener(ServiceMatcher serviceMatcher, BusBridge busBridge) {
            return new RemoteApplicationEventListener(serviceMatcher, busBridge);
        }

        @Bean
        static BeanPostProcessor uoquoServiceMatcherPostProcessor() {
            return ServiceMatcherAutoConfiguration.uoquoServiceMatcherPostProcessor();
        }
    }

    @Test
    @DisplayName("发布：本地监听器处理一次，同时发往总线一次")
    void publishHandledLocallyAndSentToBus() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(BusConfig.class)) {
            RecordingListener listener = ctx.getBean(RecordingListener.class);
            BusBridge busBridge = ctx.getBean(BusBridge.class);
            TestRemoteEvent event = new TestRemoteEvent("BIZ", "OP", "OK");
            event.setEventId("evt-1");

            ctx.publishEvent(event);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(List.of("evt-1"), listener.received));
            verify(busBridge, times(1)).send(event);
        }
    }

    private static com.uoquo.cloud.events.RemoteEvent<String> directedEvent(String id, String destinationService) {
        com.uoquo.cloud.events.RemoteEvent<String> event =
                new com.uoquo.cloud.events.RemoteEvent<>("BIZ", "OP", "OK", null, destinationService);
        event.setEventId(id);
        event.setNewData("data");
        return event;
    }

    @Test
    @DisplayName("发布：定向给其他服务的事件，发送方本地的监听器不处理，只发往总线")
    void eventDirectedToOtherServiceIsNotHandledLocally() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(BusConfig.class)) {
            RecordingListener listener = ctx.getBean(RecordingListener.class);
            BusBridge busBridge = ctx.getBean(BusBridge.class);
            com.uoquo.cloud.events.RemoteEvent<String> toOther = directedEvent("evt-other", "svc-b");
            com.uoquo.cloud.events.RemoteEvent<String> broadcast = directedEvent("evt-all", null);

            ctx.publishEvent(toOther);
            ctx.publishEvent(broadcast);

            // 只有广播事件被本地监听器处理；两个事件都发往了总线
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(List.of("evt-all"), listener.received));
            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertEquals(List.of("evt-all"), listener.received));
            verify(busBridge, times(1)).send(toOther);
            verify(busBridge, times(1)).send(broadcast);
        }
    }

    @Test
    @DisplayName("发布：定向给本服务的事件，本地监听器处理一次")
    void eventDirectedToSelfIsHandledLocally() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(BusConfig.class)) {
            RecordingListener listener = ctx.getBean(RecordingListener.class);

            ctx.publishEvent(directedEvent("evt-self", APP_NAME));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(List.of("evt-self"), listener.received));
        }
    }

    @Test
    @DisplayName("没有启用 Bus（没有 ServiceMatcher）时，按本服务名匹配目标服务：发给其他服务的事件不处理")
    void withoutBusEventDirectedToOtherServiceIsNotHandled() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(NoBusConfig.class)) {
            RecordingListener listener = ctx.getBean(RecordingListener.class);

            ctx.publishEvent(directedEvent("evt-other", "svc-b"));
            ctx.publishEvent(directedEvent("evt-other2", "svc-b:**"));
            ctx.publishEvent(directedEvent("evt-all", null));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(List.of("evt-all"), listener.received));
            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertEquals(List.of("evt-all"), listener.received));
        }
    }

    @Test
    @DisplayName("没有启用 Bus（没有 ServiceMatcher）时：广播、发给本服务的事件正常处理")
    void withoutBusBroadcastAndSelfDirectedAreHandled() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(NoBusConfig.class)) {
            RecordingListener listener = ctx.getBean(RecordingListener.class);

            ctx.publishEvent(directedEvent("evt-1", null));
            ctx.publishEvent(directedEvent("evt-2", "**"));
            ctx.publishEvent(directedEvent("evt-3", APP_NAME));
            ctx.publishEvent(directedEvent("evt-4", APP_NAME + ":**"));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertEquals(4, listener.received.size(), "received=" + listener.received));
        }
    }

    @Test
    @DisplayName("没有启用 Bus 时，本服务的标识都取不到则无法判断，事件照常处理")
    void withoutBusAndWithoutServiceNameEverythingIsHandled() {
        Config.put("spring.application.name", (String) null);
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(NoBusConfig.class)) {
            RecordingListener listener = ctx.getBean(RecordingListener.class);

            ctx.publishEvent(directedEvent("evt-x", "svc-b"));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(List.of("evt-x"), listener.received));
        }
    }

    @Configuration
    static class NoBusConfig {
        @Bean
        UoquoEventListenerFactory uoquoEventListenerFactory(ObjectProvider<ServiceMatcher> serviceMatcher) {
            return new RemoteEventListenerFactory(serviceMatcher);
        }

        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }

    @Test
    @DisplayName("接收：自己发出的事件经总线回来时被丢弃，不重复处理、不再次转发")
    void ownEventComingBackIsDropped() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(BusConfig.class)) {
            RecordingListener listener = ctx.getBean(RecordingListener.class);
            BusBridge busBridge = ctx.getBean(BusBridge.class);
            BusConsumer consumer = new BusConsumer(ctx, ctx.getBean(ServiceMatcher.class), ctx.getBeanProvider(BusBridge.class),
                    new BusProperties(), new PathDestinationFactory());
            TestRemoteEvent event = new TestRemoteEvent("BIZ", "OP", "OK");
            event.setEventId("evt-2");
            ctx.publishEvent(event);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(1, listener.received.size()));

            // 模拟总线把事件投递回发送方
            consumer.accept(event);

            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertEquals(List.of("evt-2"), listener.received));
            verify(busBridge, times(1)).send(event);
        }
    }

    @Test
    @DisplayName("接收：其他服务发来的事件正常处理，且不会被转发回总线")
    void eventFromOtherServiceIsHandled() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(BusConfig.class)) {
            RecordingListener listener = ctx.getBean(RecordingListener.class);
            BusBridge busBridge = ctx.getBean(BusBridge.class);
            BusConsumer consumer = new BusConsumer(ctx, ctx.getBean(ServiceMatcher.class), ctx.getBeanProvider(BusBridge.class),
                    new BusProperties(), new PathDestinationFactory());
            // 事件在其他服务中创建（originService 在构造时确定）
            Config.put("spring.application.name", "svc-b");
            TestRemoteEvent event = new TestRemoteEvent("BIZ", "OP", "OK");
            event.setEventId("evt-3");
            Config.put("spring.application.name", APP_NAME);

            consumer.accept(event);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(List.of("evt-3"), listener.received));
            verify(busBridge, never()).send(event);
            verify(busBridge, never()).send((RemoteApplicationEvent) any(TestRemoteEvent.class));
        }
    }
}
