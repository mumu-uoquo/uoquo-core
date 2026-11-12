package com.uoquo.test.events;

import com.uoquo.utils.CurrentUser;
import com.uoquo.web.events.AppEvent;
import com.uoquo.web.events.AppEventListenerFactory;
import com.uoquo.web.events.EventEnricher;
import com.uoquo.web.events.EventExecutor;
import com.uoquo.web.events.UoquoEventListenerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 事件监听适配器：异步执行、泛型过滤、用户上下文填充与恢复、异常隔离。
 */
@DisplayName("UoquoEventListenerAdapter 监听行为")
class UoquoEventListenerAdapterTest {

    /** 监听器执行时观察到的上下文 */
    record Seen(String thread, String token, String operatorId, String operatorName, String traceId, String requestId) {
    }

    static class Listener {
        final List<Seen> seen = new CopyOnWriteArrayList<>();
        final AtomicInteger mismatched = new AtomicInteger();
        final AtomicInteger afterFailure = new AtomicInteger();

        @EventListener
        public void onString(AppEvent<String> event) {
            seen.add(new Seen(Thread.currentThread().getName(), CurrentUser.getToken(), CurrentUser.getInfo().getUserId(),
                    CurrentUser.getInfo().getUserName(), CurrentUser.getTraceId(), MDC.get("requestId")));
        }

        /** 声明了其他泛型：数据类型不匹配，不应被调用 */
        @EventListener
        public void onInteger(AppEvent<Integer> event) {
            mismatched.incrementAndGet();
        }

        /** 抛出异常的监听器不能影响其他监听器 */
        @EventListener
        public void failing(AppEvent<String> event) {
            afterFailure.incrementAndGet();
            throw new IllegalStateException("boom");
        }

        /** 声明为通配符：处理所有 AppEvent */
        @EventListener
        public void onAny(AppEvent<?> event) {
            afterFailure.incrementAndGet();
        }
    }

    @Configuration
    static class Cfg {
        @Bean
        UoquoEventListenerFactory uoquoEventListenerFactory() {
            return new AppEventListenerFactory();
        }

        @Bean
        Listener listener() {
            return new Listener();
        }
    }

    @BeforeEach
    void setUp() {
        CurrentUser.clear();
        CurrentUser.setToken("caller-token");
        CurrentUser.setTraceId("caller-trace");
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
        MDC.remove("requestId");
    }

    private AppEvent<String> eventOf(String operatorId) {
        AppEvent<String> event = new AppEvent<>("BIZ", "OP", "OK");
        event.setNewData("data");
        event.setBusinessId("biz-1");
        event.setToken("event-token");
        event.setTraceId("event-trace");
        event.setOperatorId(operatorId);
        event.setOperatorName("name-of-" + operatorId);
        return event;
    }

    @Test
    @DisplayName("监听器在事件线程池中执行，且 CurrentUser、MDC 取自事件")
    void runsAsyncWithEventContext() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Cfg.class)) {
            Listener listener = ctx.getBean(Listener.class);

            ctx.publishEvent(eventOf("op-1"));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(1, listener.seen.size()));
            Seen seen = listener.seen.get(0);
            assertTrue(seen.thread().startsWith("event-pool-"), seen.thread());
            assertEquals("event-token", seen.token());
            assertEquals("op-1", seen.operatorId());
            assertEquals("name-of-op-1", seen.operatorName());
            assertEquals("event-trace", seen.traceId());
            assertEquals("event-trace-biz-1", seen.requestId());
        }
    }

    @Test
    @DisplayName("发布线程自己的 CurrentUser 不受影响")
    void callerContextUntouched() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Cfg.class)) {
            Listener listener = ctx.getBean(Listener.class);

            ctx.publishEvent(eventOf("op-1"));
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(1, listener.seen.size()));

            assertEquals("caller-token", CurrentUser.getToken());
            assertEquals("caller-trace", CurrentUser.getTraceId());
        }
    }

    @Test
    @DisplayName("泛型不匹配的监听器不会被调用；抛异常的监听器不影响其他监听器")
    void filtersByGenericAndIsolatesFailures() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Cfg.class)) {
            Listener listener = ctx.getBean(Listener.class);

            ctx.publishEvent(eventOf("op-1"));

            // failing + onAny 各调用一次；onString 正常执行
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertEquals(1, listener.seen.size());
                assertEquals(2, listener.afterFailure.get());
            });
            assertEquals(0, listener.mismatched.get());
        }
    }

    @Test
    @DisplayName("没有 token、traceId 的事件也能正常处理，MDC 不出现 null")
    void eventWithoutTraceId() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Cfg.class)) {
            Listener listener = ctx.getBean(Listener.class);
            AppEvent<String> event = new AppEvent<>("BIZ", "OP", "OK");
            event.setNewData("data");

            ctx.publishEvent(event);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(1, listener.seen.size()));
            assertFalse(String.valueOf(listener.seen.get(0).requestId()).contains("null"));
        }
    }

    @Test
    @DisplayName("EventEnricher 补充的信息可以在监听器中取到")
    void enrichedInfoVisibleInListener() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Cfg.class)) {
            Listener listener = ctx.getBean(Listener.class);
            CurrentUser.UserInfo info = new CurrentUser.UserInfo();
            info.setUserId("u-9");
            CurrentUser.setInfo(info);
            AppEvent<String> event = new AppEvent<>("BIZ", "OP", "OK");
            event.setNewData("data");
            EventEnricher.enrich(event);

            ctx.publishEvent(event);

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(1, listener.seen.size()));
            assertEquals("u-9", listener.seen.get(0).operatorId());
            assertEquals("caller-token", listener.seen.get(0).token());
        }
    }

    @Test
    @DisplayName("事件线程池饱和时由调用方线程执行，既不丢事件也不破坏调用方的上下文")
    void saturatedPoolRunsInCaller() throws Exception {
        com.uoquo.utils.Config.put("app.event.pool.core-size", "1");
        com.uoquo.utils.Config.put("app.event.pool.max-size", "1");
        com.uoquo.utils.Config.put("app.event.pool.queue-capacity", "1");
        EventExecutor.shutdown();
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Cfg.class)) {
            Listener listener = ctx.getBean(Listener.class);
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            // 占满唯一的线程与队列
            EventExecutor.execute(() -> waitFor(release));
            EventExecutor.execute(() -> waitFor(release));
            try {
                ctx.publishEvent(eventOf("op-1"));

                // 线程池饱和：监听器在调用方（当前测试线程）内执行
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertFalse(listener.seen.isEmpty()));
                assertEquals("caller-token", CurrentUser.getToken());
                assertEquals("caller-trace", CurrentUser.getTraceId());
            } finally {
                release.countDown();
            }
        } finally {
            com.uoquo.utils.Config.put("app.event.pool.core-size", (String) null);
            com.uoquo.utils.Config.put("app.event.pool.max-size", (String) null);
            com.uoquo.utils.Config.put("app.event.pool.queue-capacity", (String) null);
            EventExecutor.shutdown();
        }
    }

    private static void waitFor(java.util.concurrent.CountDownLatch latch) {
        try {
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
