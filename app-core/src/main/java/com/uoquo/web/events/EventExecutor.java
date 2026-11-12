/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import com.uoquo.utils.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 事件专用线程池（事件的投递与监听器的执行共用），与 {@code ThreadPoolUtil}（业务手动启动的异步任务）相互独立，
 * 避免监听器中的耗时操作（如 Feign 调用）挤占其他任务的线程。
 *
 * <p>可通过配置调整（均有默认值）：</p>
 * <table>
 *   <caption>配置项</caption>
 *   <tr><td>{@code app.event.pool.core-size}</td><td>核心线程数，默认 4</td></tr>
 *   <tr><td>{@code app.event.pool.max-size}</td><td>最大线程数，默认 16（队列满后才会扩展到该值）</td></tr>
 *   <tr><td>{@code app.event.pool.queue-capacity}</td><td>等待队列长度，默认 1000</td></tr>
 *   <tr><td>{@code app.event.pool.keep-alive-seconds}</td><td>非核心线程空闲存活时间（秒），默认 60</td></tr>
 * </table>
 * <p>线程池与队列都满时，由提交任务的线程直接执行（不丢事件，同时形成反压）。</p>
 *
 * @author xuhz
 */
public final class EventExecutor {
    private static final Logger log = LoggerFactory.getLogger(EventExecutor.class);

    /** 核心线程数默认值 */
    public static final int DEFAULT_CORE_SIZE = 4;
    /** 最大线程数默认值 */
    public static final int DEFAULT_MAX_SIZE = 16;
    /** 队列长度默认值 */
    public static final int DEFAULT_QUEUE_CAPACITY = 1000;
    /** 空闲线程存活时间默认值（秒） */
    public static final int DEFAULT_KEEP_ALIVE_SECONDS = 60;

    private static final Object LOCK = new Object();

    private static volatile ThreadPoolExecutor executor;

    private EventExecutor() {
    }

    /**
     * 异步执行任务。
     * @param task 任务（任务内的异常需要自行处理，线程池只负责兜底记录日志）
     */
    public static void execute(Runnable task) {
        executor().execute(() -> {
            try {
                task.run();
            } catch (Throwable e) {
                log.error("event task error.", e);
            }
        });
    }

    /**
     * 获取线程池（未创建或已关闭则重新创建）。
     */
    public static ExecutorService executor() {
        ThreadPoolExecutor current = executor;
        if (current != null && !current.isShutdown()) {
            return current;
        }
        synchronized (LOCK) {
            if (executor == null || executor.isShutdown()) {
                executor = create();
            }
            return executor;
        }
    }

    /**
     * 关闭线程池：等待已提交的任务执行完毕（最长 10 秒），超时则中断。
     */
    public static void shutdown() {
        ThreadPoolExecutor current;
        synchronized (LOCK) {
            current = executor;
            executor = null;
        }
        if (current == null) {
            return;
        }
        log.info("stopping event executor.");
        current.shutdown();
        try {
            if (!current.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("event executor did not terminate in time, dropped tasks={}", current.shutdownNow().size());
            }
        } catch (InterruptedException e) {
            current.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadPoolExecutor create() {
        int core = positive(Config.getInt("app.event.pool.core-size", DEFAULT_CORE_SIZE), DEFAULT_CORE_SIZE);
        int max = Math.max(core, positive(Config.getInt("app.event.pool.max-size", DEFAULT_MAX_SIZE), DEFAULT_MAX_SIZE));
        int queue = positive(Config.getInt("app.event.pool.queue-capacity", DEFAULT_QUEUE_CAPACITY), DEFAULT_QUEUE_CAPACITY);
        int keepAlive = positive(Config.getInt("app.event.pool.keep-alive-seconds", DEFAULT_KEEP_ALIVE_SECONDS), DEFAULT_KEEP_ALIVE_SECONDS);
        log.info("initialize event executor. core={}, max={}, queue={}, keepAlive={}s", core, max, queue, keepAlive);

        AtomicInteger index = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "event-pool-" + index.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        RejectedExecutionHandler callerRuns = new ThreadPoolExecutor.CallerRunsPolicy();
        return new ThreadPoolExecutor(core, max, keepAlive, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queue),
                threadFactory, (task, pool) -> {
            log.warn("event executor is saturated, run the task in caller thread. active={}, queue={}",
                    pool.getActiveCount(), pool.getQueue().size());
            callerRuns.rejectedExecution(task, pool);
        });
    }

    private static int positive(int value, int def) {
        return value > 0 ? value : def;
    }
}
