/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */

package com.uoquo.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jspecify.annotations.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * 描述：线程池工具类. <br>
 * 日期：2018-06-15 16:31 <br>
 * 变更：
 * <pre>
 * Version      Date           ModifiedBy       Content
 * --------     ----------     ------------     -----------------------
 * 1.0          2018-06-15     xuhz.           创建
 * </pre>
 * @since   JDK 1.8
 * @version 1.0
 * @author  uoquo team
 */
public class ThreadPoolUtil {
    /**
     * 日志对象.
     */
    private static final Logger log = LoggerFactory.getLogger(ThreadPoolUtil.class);
    
    /**
     * 执行器.
     */
    private static volatile ThreadPoolTaskScheduler taskScheduler = null;
    
    /**
     * 异步执行任务（单次）.
     * @param command 待执行命令
     */
    public static void executeOnce(@NonNull Runnable command) {
        executeOnce(command, 0);
    }
    
    /**
     * 异步执行任务（单次）.
     * @param command 待执行命令
     * @param delayMs 延迟时间（ms）
     */
    public static void executeOnce(@NonNull Runnable command, int delayMs) {
        // 初始化线程池
        initScheduler();
        // 执行任务
        try {
            Instant startTime = Instant.now().plusMillis(delayMs);
            taskScheduler.schedule(command, startTime);
        } catch (Throwable e) {
            log.error("execute task error.", e);
        }
    }
    
    /**
     * 异步执行任务（单次）.
     * @param task 待执行任务
     */
    public static <T> Future<T> executeOnce(Callable<T> task) {
        return executeOnce(task, 0);
    }
    
    /**
     * 异步执行任务（单次）.
     * @param task 待执行任务
     * @param delayMs 延迟时间（ms）
     */
    public static <T> Future<T> executeOnce(@NonNull Callable<T> task, int delayMs) {
        // 初始化线程池
        initScheduler();
        // 执行任务
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (Exception e) {
                log.error("befor execute task sleep {}ms error.", delayMs, e);
            }
        }
        return taskScheduler.submit(task);
    }
    
    /**
     * 异步执行任务（循环）.
     * @param command 待执行命令
     * @param period  循环频率（s）
     */
    public static void execute(@NonNull Runnable command, int period) {
        execute(command, period, 0);
    }
    
    /**
     * 异步执行任务（循环）.
     * @param command 待执行命令
     * @param period  循环频率（s）
     * @param delayMs 延迟时间（ms）
     */
    public static void execute(@NonNull Runnable command, int period, int delayMs) {
        // 初始化线程池
        initScheduler();
        // 执行任务
        try {
            if (period <= 0) {
                throw new IllegalArgumentException("循环频率不能小于0");
            }
            Instant startTime = Instant.now().plusMillis(delayMs);
            Duration duration = Duration.ofMillis(period * 1000L);
            //taskScheduler.scheduleAtFixedRate(command, ca.getTime(), period * 1000);  // 从上一任务开始时计时
            taskScheduler.scheduleWithFixedDelay(command, startTime, duration); // 从上一任务结束时计时
        } catch (IllegalArgumentException e) {
            log.error("parse task cron error. period={}", period, e);
            throw e;
        } catch (Throwable e) {
            log.error("execute task error.", e);
        }
    }
    
    /**
     * 异步执行任务（循环）.
     * @param command 待执行命令
     * @param cron    循环频率（cron表达式）
     */
    public static void execute(@NonNull Runnable command, @NonNull String cron) {
        execute(command, cron, 0);
    }
    
    /**
     * 异步执行任务（循环）.
     * @param command 待执行命令
     * @param cron    循环频率（cron表达式）
     * @param delayMs 延迟时间（ms）
     */
    public static void execute(@NonNull Runnable command, @NonNull String cron, int delayMs) {
        // 初始化线程池
        initScheduler();
        // 执行任务
        try {
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (Exception e) {
                    log.error("before execute task sleep {}ms error.", delayMs, e);
                }
            }
            taskScheduler.schedule(command, new CronTrigger(cron));
        } catch (IllegalArgumentException e) {
            log.error("parse task cron error. cron={}", cron, e);
            throw e;
        } catch (Throwable e) {
            log.error("execute task error.", e);
        }
    }
    
    /**
     * 停止线程.
     */
    public static void shutdown() {
        if (taskScheduler != null) {
            log.info("stopping thread pool util.");
            taskScheduler.shutdown();
            log.info("stopped thread pool util.");
        }
    }
    
    /**
     * 初始化线程池（循环执行）.
     */
    public static void initScheduler() {
        if (taskScheduler == null) {
            synchronized (ThreadPoolUtil.class) {
                // 如果不用spring，则可以用Executors.newScheduledThreadPool(10);
                if (taskScheduler == null) {
                    log.info("initialize thread pool util.");
                    int maxSize = Config.getInt("app.task.pool.max-size", 10);
                    maxSize = (maxSize < 1) ? 10 : maxSize;
                    
                    taskScheduler = new ThreadPoolTaskScheduler();
                    taskScheduler.setPoolSize(maxSize);
                    taskScheduler.setThreadNamePrefix("task-pool-");
                    /* 
                     * 线程池对拒绝任务(无线程可用)的处理策略
                     * ThreadPoolExecutor.AbortPolicy         : 处理程序遭到拒绝时丢弃，将抛出 RejectedExecutionException。（默认）
                     * ThreadPoolExecutor.CallerRunsPolicy    : 处理程序遭到拒绝时，自动重复直到成功，或者调用者放弃执行. 
                     * ThreadPoolExecutor.DiscardPolicy       : 处理程序遭到拒绝时丢弃（不抛出异常）. 
                     * ThreadPoolExecutor.DiscardOldestPolicy : 如果执行程序尚未关闭，则位于工作队列头部的任务将被删除，然后重试执行程序（如果再次失败，则重复此过程）.
                     */
                    taskScheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
                    taskScheduler.setRemoveOnCancelPolicy(true);             // 当任务取消时，从当前调度器移除
                    taskScheduler.setAwaitTerminationSeconds(5);             // 停机时等待所有线程执行完毕的最大等待等待时间
                    taskScheduler.setWaitForTasksToCompleteOnShutdown(true); // 停机时等待所有线程执行完毕（默认false）
                    taskScheduler.initialize();
                }
            }
        }
    }
}
