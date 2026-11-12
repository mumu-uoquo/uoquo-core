package com.uoquo.test.events;

import com.uoquo.web.events.AppEventListenerFactory;
import com.uoquo.web.events.UoquoEventListenerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring 自身的事件（生命周期事件）与无参监听方法：由 Spring 原生同步执行，不能被适配器吞掉或异步化。
 * （来自 demo 日志：springdoc 的 {@code @EventListener(ApplicationReadyEvent.class) init()} 曾因“事件为空”被跳过）
 */
@DisplayName("框架事件与无参监听方法")
class FrameworkEventListenerTest {

    static class Listener {
        final List<String> calls = new CopyOnWriteArrayList<>();

        /** 无参监听方法 */
        @EventListener(ContextRefreshedEvent.class)
        public void noArg() {
            calls.add("noArg@" + Thread.currentThread().getName());
        }

        /** 带框架事件参数的监听方法 */
        @EventListener
        public void withArg(ContextRefreshedEvent event) {
            calls.add("withArg@" + Thread.currentThread().getName());
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

    @Test
    @DisplayName("无参监听方法与框架事件监听方法都会被调用，并且在发布线程同步执行")
    void invokedSynchronously() {
        String caller = Thread.currentThread().getName();
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Cfg.class)) {
            // 上下文刷新（ContextRefreshedEvent）在构造器内完成：同步执行时，此处已经调用过
            List<String> calls = ctx.getBean(Listener.class).calls;
            assertEquals(2, calls.size(), calls.toString());
            assertTrue(calls.contains("noArg@" + caller), calls.toString());
            assertTrue(calls.contains("withArg@" + caller), calls.toString());
        }
    }
}
