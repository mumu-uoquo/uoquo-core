/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.metrics.buffering.StartupTimeline;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.core.Ordered;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.StreamSupport;

/**
 * 启动耗时分析（默认关闭，需要时手动开启）。
 *
 * <p>开启方式：JVM 参数 {@code -Dapp.startup.profile=true}，或环境变量 {@code APP_STARTUP_PROFILE=true}。
 * 启动类在创建 {@link SpringApplication} 后调用 {@link #enable(SpringApplication)}：</p>
 * <pre>
 * SpringApplication application = new SpringApplication(XxxApplication.class);
 * StartupProfiler.enable(application);
 * </pre>
 * 未开启时 {@code enable} 什么都不做，没有额外开销。开启后，在应用启动完成（{@code ApplicationReadyEvent}）时输出一份报告（INFO 日志，前缀 {@code [startup]}）：
 * <ol>
 *   <li><b>里程碑</b>：从 JVM 启动开始，到各个启动事件的时间点及与上一个里程碑的间隔，
 *       用来区分“引导上下文与配置中心”“Bean 创建”“Web 容器与服务注册”等大阶段；</li>
 *   <li><b>顶层步骤</b>：Spring Boot 记录的最外层启动步骤耗时；</li>
 *   <li><b>最慢的 Bean</b>：按“自身耗时”（扣除它创建过程中触发的其他 Bean 的耗时）排序，并同时给出包含依赖的总耗时；</li>
 *   <li><b>其他最慢的步骤</b>：配置类解析、BeanFactoryPostProcessor、BeanPostProcessor 注册等。</li>
 * </ol>
 * 说明：{@link BufferingApplicationStartup} 只记录主应用上下文的启动步骤；
 * 引导上下文（Spring Cloud bootstrap，含 Nacos 配置拉取）不在其中，需要从里程碑的间隔判断。
 *
 * @author xuhz
 */
public final class StartupProfiler {
    private static final Logger log = LoggerFactory.getLogger(StartupProfiler.class);

    /** 开启启动耗时分析的 JVM 参数 */
    public static final String PROPERTY = "app.startup.profile";

    /** 开启启动耗时分析的环境变量 */
    public static final String ENV = "APP_STARTUP_PROFILE";

    /** 最多记录的启动步骤数（超出后不再记录） */
    private static final int CAPACITY = 100_000;

    /** 每个排行榜输出的条数 */
    private static final int TOP = 20;

    /** 要记录时间点的启动事件（按类的简单名匹配，避免依赖 Web 相关的类） */
    private static final Set<String> MILESTONES = Set.of(
            "ApplicationStartingEvent",
            "ApplicationEnvironmentPreparedEvent",
            "ApplicationContextInitializedEvent",
            "ApplicationPreparedEvent",
            "ServletWebServerInitializedEvent",
            "ReactiveWebServerInitializedEvent",
            "ContextRefreshedEvent",
            "ApplicationStartedEvent",
            "ApplicationReadyEvent");

    private StartupProfiler() {
    }

    /**
     * 是否开启了启动耗时分析。
     */
    public static boolean isEnabled() {
        String value = System.getProperty(PROPERTY);
        if (value == null || value.isBlank()) {
            value = System.getenv(ENV);
        }
        return value != null && "true".equalsIgnoreCase(value.trim());
    }

    /**
     * 开启启动耗时分析（没有开启时什么都不做）。必须在 {@link SpringApplication#run} 之前调用。
     *
     * @return 是否已开启
     */
    public static boolean enable(SpringApplication application) {
        if (!isEnabled()) {
            return false;
        }
        application.setApplicationStartup(new BufferingApplicationStartup(CAPACITY));
        application.addListeners(new Recorder(application, System.currentTimeMillis()));
        return true;
    }

    /**
     * 记录里程碑，并在启动完成时输出报告。
     */
    private static final class Recorder implements ApplicationListener<ApplicationEvent>, Ordered {

        private final long enableAt;

        /** 里程碑名称 -> 时间点（毫秒）；每个事件只记录第一次 */
        private final Map<String, Long> milestones = new LinkedHashMap<>();

        private final AtomicBoolean reported = new AtomicBoolean();

        private final SpringApplication application;

        /** 主应用上下文（子上下文，如 Feign、LoadBalancer、Stream binder 的刷新事件会传播到父上下文，需要排除） */
        private volatile ApplicationContext mainContext;

        Recorder(SpringApplication application, long enableAt) {
            this.application = application;
            this.enableAt = enableAt;
        }

        @Override
        public int getOrder() {
            // 尽量早于其他监听器执行，减少对里程碑时间点的影响
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            if (reported.get()) {
                return;
            }
            if (event instanceof ApplicationPreparedEvent prepared && prepared.getSpringApplication() == application) {
                mainContext = prepared.getApplicationContext();
            }
            String name = event.getClass().getSimpleName();
            if (!MILESTONES.contains(name)) {
                return;
            }
            // 刷新事件、Web 容器初始化事件只记录主上下文的（子上下文的事件会传播到父上下文）
            ApplicationContext source = null;
            if (event instanceof ApplicationContextEvent contextEvent) {
                source = contextEvent.getApplicationContext();
            } else if (event instanceof WebServerInitializedEvent webEvent) {
                source = webEvent.getApplicationContext();
            }
            if (source != null && source != mainContext) {
                return;
            }
            synchronized (milestones) {
                milestones.putIfAbsent(name, System.currentTimeMillis());
            }
            if (event instanceof ApplicationReadyEvent ready && reported.compareAndSet(false, true)) {
                try {
                    report(ready);
                } catch (Throwable e) {
                    // 分析报告不能影响应用启动
                    log.warn("[startup] 输出启动耗时报告失败", e);
                }
            }
        }

        private void report(ApplicationReadyEvent ready) {
            long jvmStart = ManagementFactory.getRuntimeMXBean().getStartTime();
            StringBuilder sb = new StringBuilder("[startup] ====== 启动耗时分析 ======\n");

            // 1. 里程碑
            sb.append("[startup] -- 1. 里程碑（从 JVM 启动开始；间隔 = 与上一个里程碑的差）--\n");
            long previous = jvmStart;
            sb.append(line("main 方法调用 enable()", enableAt - jvmStart, enableAt - previous));
            previous = enableAt;
            Map<String, Long> copy;
            synchronized (milestones) {
                copy = new LinkedHashMap<>(milestones);
            }
            for (Map.Entry<String, Long> entry : copy.entrySet()) {
                sb.append(line(entry.getKey(), entry.getValue() - jvmStart, entry.getValue() - previous));
                previous = entry.getValue();
            }

            // 2. 启动步骤
            ApplicationStartup startup = ready.getApplicationContext().getApplicationStartup();
            if (startup instanceof BufferingApplicationStartup buffering) {
                appendSteps(sb, buffering.getBufferedTimeline());
            } else {
                sb.append("[startup] 当前上下文没有使用 BufferingApplicationStartup，无法输出启动步骤\n");
            }
            sb.append("[startup] ====== 结束 ======");
            log.info("\n{}", sb);
        }

        private static String line(String name, long elapsed, long interval) {
            return String.format("[startup] %8d ms  (+%6d ms)  %s%n", elapsed, interval, name);
        }

        private static void appendSteps(StringBuilder sb, StartupTimeline timeline) {
            List<StartupTimeline.TimelineEvent> events = timeline.getEvents();
            // 每个步骤的子步骤耗时合计，用于计算自身耗时
            Map<Long, Long> childMillis = new HashMap<>();
            for (StartupTimeline.TimelineEvent event : events) {
                Long parentId = event.getStartupStep().getParentId();
                if (parentId != null) {
                    childMillis.merge(parentId, event.getDuration().toMillis(), Long::sum);
                }
            }
            List<Step> steps = new ArrayList<>(events.size());
            for (StartupTimeline.TimelineEvent event : events) {
                StartupStep step = event.getStartupStep();
                long total = event.getDuration().toMillis();
                long self = Math.max(0, total - childMillis.getOrDefault(step.getId(), 0L));
                steps.add(new Step(step.getName(), tagsOf(step), step.getParentId() == null, total, self));
            }
            sb.append(String.format("[startup] 共记录 %d 个启动步骤%n", steps.size()));

            // 2. 顶层步骤
            sb.append("[startup] -- 2. 顶层步骤（总耗时）--\n");
            sb.append("[startup]    （括号内为自身耗时：没有被细分步骤覆盖的时间，如 Web 容器启动、服务注册、各 SmartLifecycle 的启动）\n");
            steps.stream().filter(s -> s.root).forEach(s -> sb.append(String.format(
                    "[startup] %8d ms (自身 %d ms)  %s  %s%n", s.total, s.self, s.name, s.tags)));

            // 3. 最慢的 Bean
            long beanCount = steps.stream().filter(s -> s.name.equals("spring.beans.instantiate")).count();
            long beanSelf = steps.stream().filter(s -> s.name.equals("spring.beans.instantiate")).mapToLong(s -> s.self).sum();
            sb.append(String.format("[startup] -- 3. 最慢的 Bean（共 %d 个，自身耗时合计 %d ms；下面按自身耗时排序，括号内为包含依赖的总耗时）--%n",
                    beanCount, beanSelf));
            steps.stream().filter(s -> s.name.equals("spring.beans.instantiate"))
                    .sorted(Comparator.comparingLong((Step s) -> s.self).reversed()).limit(TOP)
                    .forEach(s -> sb.append(stepLine(s, true)));

            // 4. 其他最慢的步骤
            sb.append("[startup] -- 4. 其他最慢的步骤（按自身耗时，不含顶层步骤和 Bean 创建）--\n");
            steps.stream().filter(s -> !s.root && !s.name.equals("spring.beans.instantiate"))
                    .sorted(Comparator.comparingLong((Step s) -> s.self).reversed()).limit(TOP)
                    .forEach(s -> sb.append(stepLine(s, true)));
        }

        private static String tagsOf(StartupStep step) {
            return StreamSupport.stream(step.getTags().spliterator(), false)
                    .map(tag -> tag.getKey() + "=" + tag.getValue())
                    .reduce((a, b) -> a + ", " + b).orElse("");
        }

        private static String stepLine(Step step, boolean bySelf) {
            long main = bySelf ? step.self : step.total;
            String total = bySelf ? " (" + step.total + " ms)" : "";
            return String.format("[startup] %8d ms%s  %s  %s%n", main, total, step.name, step.tags);
        }

        private record Step(String name, String tags, boolean root, long total, long self) {
        }
    }
}
