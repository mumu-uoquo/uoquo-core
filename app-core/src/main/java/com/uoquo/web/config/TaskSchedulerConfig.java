/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.config;

import com.uoquo.utils.Config;
import com.uoquo.utils.json.JsonUtil;
import com.uoquo.web.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.jspecify.annotations.NonNull;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync      // 异步执行任务（单次），类似于Thread
@EnableScheduling // 定时执行任务（循环）
@AutoConfigureBefore(ServiceConfig.class)
public class TaskSchedulerConfig implements SchedulingConfigurer {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    @PostConstruct
    public void initConfiguration(){
        log.debug("TaskSchedulerConfig init ...");
    }

    /* ******************* 定时任务线程池 ******************* */
    /* 如果不配置线程池，默认只有一个线程，多任务时只能顺次串行.   */
    /* scheduler：调度器（@EnableScheduling  @Scheduled）.   */
    /* executor ：执行器（@EnableAsyn        @Async）.       */
    /* *************************************************** */
    @Override
    public void configureTasks(@NonNull ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskScheduler());
    }

    /**
     * 定时任务调度池.
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler taskScheduler() {
        log.debug("Use ThreadPoolTaskScheduler");
        int taskMaxSize  = Config.getInt("app.task.pool.max-size", 10);
        taskMaxSize = (taskMaxSize < 1) ? 10 : taskMaxSize;

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(taskMaxSize);
        scheduler.setThreadNamePrefix("task-scheduler-");
        /*
         * 线程池对拒绝任务(无线程可用)的处理策略
         * ThreadPoolExecutor.AbortPolicy         : 处理程序遭到拒绝时丢弃，将抛出 RejectedExecutionException。（默认）
         * ThreadPoolExecutor.CallerRunsPolicy    : 处理程序遭到拒绝时，自动重复直到成功，或者调用者放弃执行.
         * ThreadPoolExecutor.DiscardPolicy       : 处理程序遭到拒绝时丢弃（不抛出异常）.
         * ThreadPoolExecutor.DiscardOldestPolicy : 如果执行程序尚未关闭，则位于工作队列头部的任务将被删除，然后重试执行程序（如果再次失败，则重复此过程）.
         */
        scheduler.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        scheduler.setRemoveOnCancelPolicy(true);             // 当任务取消时，从当前调度器移除
        scheduler.setAwaitTerminationSeconds(10);            // 停机时等待所有线程执行完毕的最大等待等待时间
        scheduler.setWaitForTasksToCompleteOnShutdown(true); // 停机时等待所有的线程执行完毕（默认false）
        return scheduler;
    }

    /**
     * 异步任务执行池.
     * Bean 名为 taskExecutor，Spring Boot 3.5+ 自动将其作为 @Async 的默认执行器.
     */
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        log.debug("Use ThreadPoolTaskExecutor");
        int taskCoreSize  = Config.getInt("app.task.pool.core-size", 10);
        int taskMaxSize   = Config.getInt("app.task.pool.max-size", 10);
        int taskQueueSize = Config.getInt("app.task.pool.queue-size", 10);
        taskCoreSize  = (taskCoreSize  < 1) ? 10  : taskCoreSize;
        taskMaxSize   = (taskMaxSize   < 1) ? 50  : taskMaxSize;
        taskQueueSize = (taskQueueSize < 1) ? 100 : taskQueueSize;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(taskCoreSize);   // 设置核心线程数（默认1）
        executor.setMaxPoolSize(taskMaxSize);     // 设置最大线程数（默认Integer.MAX_VALUE）
        executor.setQueueCapacity(taskQueueSize); // 设置队列的容量（默认Integer.MAX_VALUE）
        executor.setKeepAliveSeconds(60);         // 设置线程活跃时间（秒，默认60）
        executor.setThreadNamePrefix("task-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setAwaitTerminationSeconds(10);            // 停机时等待所有线程执行完毕的最大等待等待时间
        executor.setWaitForTasksToCompleteOnShutdown(true); // 等待所有任务结束后再关闭线程池（默认false）
        return executor;
    }

    /**
     * Async注解的方法发生异常时的处理
     */
    @Bean
    public AsyncUncaughtExceptionHandler asyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("Unexpected exception occurred invoking async method: {} , params: {}",
                    method, JsonUtil.serialize(params), ex);
        };
    }
}
