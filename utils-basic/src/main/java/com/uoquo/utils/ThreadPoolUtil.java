/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.NonNull;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

/**
 * 描述：线程池工具类，用于在代码中手动启动异步任务（Spring注解的异步任务由TaskSchedulerConfig管理，两者线程池相互独立）. <br>
 * 约定：
 * <ul>
 *   <li>延迟、循环等待均由线程池调度完成，不会阻塞调用线程.</li>
 *   <li>Runnable的方法：调度失败（如线程池已关闭）时只记录错误日志，不抛异常，返回null，避免影响主业务；
 *       参数不合法（循环频率、cron表达式）时抛出{@link IllegalArgumentException}.
 *       任务执行时抛出的异常（包括Error），由线程池记录日志；不会导致工作线程退出，也不影响其他任务；循环任务不会因此终止.</li>
 *   <li>Callable的方法：线程池已关闭时抛出{@link RejectedExecutionException}；任务执行时抛出的异常保存在返回的Future中，
 *       调用方需要通过Future.get()获取.</li>
 *   <li>返回的{@link ScheduledFuture}可用于取消任务（cancel）.</li>
 *   <li>{@link #shutdown()}之后，线程池不可再使用，直到再次调用{@link #initScheduler()}.
 *       关闭时先等待正在执行的任务完成（最长5秒），超时则中断并丢弃剩余任务（包括未到执行时间的延迟任务），保证线程被释放.
 *       注意：不响应中断的任务（如死循环）仍然无法被强制结束.</li>
 * </ul>
 * 日期：2018-06-15 16:31 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-06-15     xuhz.           创建
 * 1.1          2026-11-10     uoquo team       延迟不再阻塞调用线程；修复线程池初始化未完成就被使用的竞态；
 *                                              关闭后的行为明确；返回ScheduledFuture用于取消任务；修正参数校验的异常信息
 * </pre>
 * @since   JDK 1.8
 * @version 1.1
 * @author  uoquo team
 */
public class ThreadPoolUtil {
    /**
     * 日志对象.
     */
    private static final Logger log = LoggerFactory.getLogger(ThreadPoolUtil.class);

    /**
     * 锁：保证线程池的创建与关闭互斥.
     */
    private static final Object LOCK = new Object();
    
    /**
     * 执行器.
     */
    private static volatile ThreadPoolTaskScheduler taskScheduler = null;

    /**
     * 是否已关闭（关闭后不再自动创建线程池）.
     */
    private static volatile boolean closed = false;
    
    /**
     * 异步执行任务（单次）.
     * @param command 待执行命令
     * @return 任务句柄；调度失败时返回null
     */
    public static ScheduledFuture<?> executeOnce(@NonNull Runnable command) {
        return executeOnce(command, 0);
    }
    
    /**
     * 异步执行任务（单次）.
     * @param command 待执行命令
     * @param delayMs 延迟时间（ms）
     * @return 任务句柄；调度失败时返回null
     */
    public static ScheduledFuture<?> executeOnce(@NonNull Runnable command, int delayMs) {
        try {
            Instant startTime = Instant.now().plusMillis(Math.max(delayMs, 0));
            return scheduler().schedule(command, startTime);
        } catch (RuntimeException e) {
            log.error("execute task error. task={}", command, e);
            return null;
        }
    }
    
    /**
     * 异步执行任务（单次）.
     * @param task 待执行任务
     * @return 任务句柄
     * @throws RejectedExecutionException 线程池已关闭，或者无法调度
     */
    public static <T> ScheduledFuture<T> executeOnce(@NonNull Callable<T> task) {
        return executeOnce(task, 0);
    }
    
    /**
     * 异步执行任务（单次）.
     * @param task 待执行任务
     * @param delayMs 延迟时间（ms）
     * @return 任务句柄
     * @throws RejectedExecutionException 线程池已关闭，或者无法调度
     */
    public static <T> ScheduledFuture<T> executeOnce(@NonNull Callable<T> task, int delayMs) {
        return scheduler().getScheduledExecutor().schedule(task, Math.max(delayMs, 0), TimeUnit.MILLISECONDS);
    }
    
    /**
     * 异步执行任务（循环）.
     * @param command 待执行命令
     * @param period  循环频率（s）
     * @return 任务句柄；调度失败时返回null
     * @throws IllegalArgumentException 循环频率不合法
     */
    public static ScheduledFuture<?> execute(@NonNull Runnable command, int period) {
        return execute(command, period, 0);
    }
    
    /**
     * 异步执行任务（循环）.<br>
     * 从上一次任务结束时开始计时.
     * @param command 待执行命令
     * @param period  循环频率（s，必须大于0）
     * @param delayMs 延迟时间（ms）
     * @return 任务句柄；调度失败时返回null
     * @throws IllegalArgumentException 循环频率不合法
     */
    public static ScheduledFuture<?> execute(@NonNull Runnable command, int period, int delayMs) {
        if (period <= 0) {
            log.error("task period must be greater than 0. period={}", period);
            throw new IllegalArgumentException("循环频率必须大于0");
        }
        try {
            Instant startTime = Instant.now().plusMillis(Math.max(delayMs, 0));
            Duration duration = Duration.ofSeconds(period);
            //scheduleAtFixedRate(command, startTime, duration);  // 从上一任务开始时计时
            return scheduler().scheduleWithFixedDelay(command, startTime, duration); // 从上一任务结束时计时
        } catch (RuntimeException e) {
            log.error("execute task error. task={}", command, e);
            return null;
        }
    }
    
    /**
     * 异步执行任务（循环）.
     * @param command 待执行命令
     * @param cron    循环频率（cron表达式）
     * @return 任务句柄；调度失败时返回null
     * @throws IllegalArgumentException cron表达式不合法
     */
    public static ScheduledFuture<?> execute(@NonNull Runnable command, @NonNull String cron) {
        return execute(command, cron, 0);
    }
    
    /**
     * 异步执行任务（循环）.
     * @param command 待执行命令
     * @param cron    循环频率（cron表达式）
     * @param delayMs 延迟时间（ms）：从当前时间延迟后，才开始按cron表达式计算第一次执行时间
     * @return 任务句柄；调度失败时返回null
     * @throws IllegalArgumentException cron表达式不合法
     */
    public static ScheduledFuture<?> execute(@NonNull Runnable command, @NonNull String cron, int delayMs) {
        Trigger trigger;
        try {
            trigger = buildTrigger(cron, delayMs);
        } catch (IllegalArgumentException e) {
            log.error("parse task cron error. cron={}", cron, e);
            throw e;
        }
        try {
            return scheduler().schedule(command, trigger);
        } catch (RuntimeException e) {
            log.error("execute task error. task={}", command, e);
            return null;
        }
    }

    /**
     * 创建cron触发器. 有延迟时，第一次执行时间从“当前时间 + 延迟”之后开始计算.
     */
    private static Trigger buildTrigger(String cron, int delayMs) {
        final CronTrigger cronTrigger = new CronTrigger(cron);
        if (delayMs <= 0) {
            return cronTrigger;
        }
        final Instant start = Instant.now().plusMillis(delayMs);
        return triggerContext -> {
            if (triggerContext.lastCompletion() == null) { // 第一次执行
                return cronTrigger.nextExecution(new SimpleTriggerContext(start, start, start));
            }
            return cronTrigger.nextExecution(triggerContext);
        };
    }
    
    /**
     * 停止线程池. <br>
     * 等待正在执行的任务完成（最长5秒），之后线程池不可再使用（再次调用{@link #initScheduler()}可重新启用）.
     */
    public static void shutdown() {
        ThreadPoolTaskScheduler scheduler;
        synchronized (LOCK) {
            closed = true;
            scheduler = taskScheduler;
            taskScheduler = null;
        }
        if (scheduler != null) {
            log.info("stopping thread pool util.");
            ScheduledExecutorService executor = scheduler.getScheduledExecutor();
            scheduler.shutdown(); // 等待正在执行的任务完成（最长5秒），可能需要等待，不放在锁内
            if (!executor.isTerminated()) {
                // 超时仍未结束（任务执行时间过长，或者还有未到执行时间的延迟任务）：中断并丢弃，避免线程一直不释放，导致JVM无法退出
                List<Runnable> dropped = executor.shutdownNow();
                log.warn("thread pool util did not terminate in time, forced to shut down. dropped tasks={}", dropped.size());
            }
            log.info("stopped thread pool util.");
        }
    }
    
    /**
     * 初始化线程池（循环执行）. <br>
     * 一般无需调用，首次提交任务时会自动初始化；关闭（{@link #shutdown()}）之后，调用该方法可重新启用.
     */
    public static void initScheduler() {
        synchronized (LOCK) {
            closed = false;
            if (taskScheduler == null) {
                taskScheduler = createScheduler();
            }
        }
    }

    /**
     * 获取线程池（未初始化则自动初始化；已关闭则拒绝）.
     */
    private static ThreadPoolTaskScheduler scheduler() {
        ThreadPoolTaskScheduler scheduler = taskScheduler;
        if (scheduler != null) {
            return scheduler;
        }
        synchronized (LOCK) {
            if (closed) {
                throw new RejectedExecutionException("thread pool util has been shut down.");
            }
            if (taskScheduler == null) {
                taskScheduler = createScheduler();
            }
            return taskScheduler;
        }
    }

    /**
     * 创建线程池. 初始化完成后才对外使用（调用方负责赋值给静态字段）.
     */
    private static ThreadPoolTaskScheduler createScheduler() {
        // 如果不用spring，则可以用Executors.newScheduledThreadPool(10);
        log.info("initialize thread pool util.");
        int maxSize = Config.getInt("app.task.pool.max-size", 10);
        maxSize = (maxSize < 1) ? 10 : maxSize;

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(maxSize);
        scheduler.setThreadNamePrefix("task-pool-");
        /* 
         * 线程池对拒绝任务的处理策略（调度线程池的队列无界，只有线程池关闭后才会拒绝新任务）
         * ThreadPoolExecutor.AbortPolicy         : 处理程序遭到拒绝时丢弃，将抛出 RejectedExecutionException。（默认）
         * ThreadPoolExecutor.CallerRunsPolicy    : 处理程序遭到拒绝时，自动重复直到成功，或者调用者放弃执行. 
         * ThreadPoolExecutor.DiscardPolicy       : 处理程序遭到拒绝时丢弃（不抛出异常）. 
         * ThreadPoolExecutor.DiscardOldestPolicy : 如果执行程序尚未关闭，则位于工作队列头部的任务将被删除，然后重试执行程序（如果再次失败，则重复此过程）.
         */
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        scheduler.setRemoveOnCancelPolicy(true);             // 当任务取消时，从当前调度器移除
        scheduler.setAwaitTerminationSeconds(5);             // 停机时等待所有线程执行完毕的最大等待等待时间
        scheduler.setWaitForTasksToCompleteOnShutdown(true); // 停机时等待所有线程执行完毕（默认false）
        scheduler.initialize();
        return scheduler;
    }
}
