package com.uoquo.test.events;

import com.uoquo.utils.CurrentUser;
import com.uoquo.web.events.AppEvent;
import com.uoquo.web.events.DefaultUoquoEventPublisher;
import com.uoquo.web.events.UoquoEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 事件发布器：补充发起人信息、按事务状态决定投递时机。
 */
@DisplayName("DefaultUoquoEventPublisher 发布时机")
class DefaultUoquoEventPublisherTest {

    private final List<Object> published = new CopyOnWriteArrayList<>();

    private final ApplicationEventPublisher delegate = published::add;

    private final UoquoEventPublisher publisher = new DefaultUoquoEventPublisher(delegate);

    @BeforeEach
    void setUp() {
        CurrentUser.clear();
        CurrentUser.setToken("token-1");
        CurrentUser.setTraceId("trace-1");
        CurrentUser.UserInfo info = new CurrentUser.UserInfo();
        info.setUserId("u-1");
        info.setUserName("alice");
        CurrentUser.setInfo(info);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        CurrentUser.clear();
    }

    private static AppEvent<String> newEvent() {
        return new AppEvent<>("BIZ", "OP", "OK");
    }

    private static void complete(int status) {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(status);
        }
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void assertNothingPublishedFor(Duration duration) {
        await().during(duration).atMost(duration.plusSeconds(5)).untilAsserted(() -> assertTrue(published.isEmpty()));
    }

    @Test
    @DisplayName("没有事务：立即异步投递，并补充了发起人信息")
    void noTransactionPublishesImmediately() {
        AppEvent<String> event = newEvent();

        publisher.publishEvent(event);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(List.of(event), published));
        assertEquals("u-1", event.getOperatorId());
        assertEquals("token-1", event.getToken());
        assertEquals("trace-1", event.getTraceId());
        assertNotNull(event.getOperationTime());
    }

    @Test
    @DisplayName("有事务：事务提交前不投递，提交后投递")
    void publishEventWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AppEvent<String> event = newEvent();

        publisher.publishEvent(event);
        assertNothingPublishedFor(Duration.ofMillis(300));

        complete(TransactionSynchronization.STATUS_COMMITTED);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(List.of(event), published));
    }

    @Test
    @DisplayName("有事务：publishEvent 在回滚后仍然投递（登录失败等事件先发布再抛异常）")
    void publishEventStillPublishedAfterRollback() {
        TransactionSynchronizationManager.initSynchronization();
        AppEvent<String> event = newEvent();

        publisher.publishEvent(event);
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(List.of(event), published));
    }

    @Test
    @DisplayName("有事务：publishAfterCommit 提交成功后才投递")
    void publishAfterCommitOnCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AppEvent<String> event = newEvent();

        publisher.publishAfterCommit(event);
        assertNothingPublishedFor(Duration.ofMillis(300));

        complete(TransactionSynchronization.STATUS_COMMITTED);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(List.of(event), published));
    }

    @Test
    @DisplayName("有事务：publishAfterCommit 回滚则丢弃")
    void publishAfterCommitDiscardedOnRollback() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishAfterCommit(newEvent());
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertNothingPublishedFor(Duration.ofMillis(500));
    }

    @Test
    @DisplayName("没有事务：publishAfterCommit 立即投递")
    void publishAfterCommitWithoutTransaction() {
        AppEvent<String> event = newEvent();

        publisher.publishAfterCommit(event);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(List.of(event), published));
    }

    @Test
    @DisplayName("null 事件：忽略，不抛异常")
    void nullEventIgnored() {
        assertDoesNotThrow(() -> publisher.publishEvent((Object) null));
        assertNothingPublishedFor(Duration.ofMillis(200));
    }

    @Test
    @DisplayName("业务类型为空：在业务线程中抛出 IllegalArgumentException，不会投递")
    void invalidEventRejectedInCallerThread() {
        AppEvent<String> event = newEvent();
        event.setBusinessType(null);

        assertThrows(IllegalArgumentException.class, () -> publisher.publishEvent(event));
        assertNothingPublishedFor(Duration.ofMillis(200));
    }

    @Test
    @DisplayName("下游发布失败：只记录日志，不影响后续事件")
    void deliveryFailureDoesNotAffectNextEvent() {
        List<Object> received = new CopyOnWriteArrayList<>();
        UoquoEventPublisher failingFirst = new DefaultUoquoEventPublisher(event -> {
            if (received.isEmpty() && received.add("first")) {
                throw new IllegalStateException("boom");
            }
            received.add(event);
        });
        AppEvent<String> second = newEvent();

        failingFirst.publishEvent(newEvent());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(1, received.size()));
        failingFirst.publishEvent(second);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertTrue(received.contains(second)));
    }
}
