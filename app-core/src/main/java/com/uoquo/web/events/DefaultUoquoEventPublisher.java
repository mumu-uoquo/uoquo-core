/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.uoquo.utils.StringUtil;

/**
 * 默认的事件发布器：
 * <ol>
 *   <li>在业务线程中补充发起人信息（{@link EventEnricher}），业务线程的 {@code CurrentUser} 在异步线程中不可用；</li>
 *   <li>按事务状态决定投递时机（见 {@link UoquoEventPublisher}）；</li>
 *   <li>在事件专用线程池（{@link EventExecutor}）中调用 {@link ApplicationEventPublisher#publishEvent(Object)}：
 *       本地监听器与 Spring Cloud Bus（微服务事件）都由 Spring 的事件机制完成，发布失败不影响业务。</li>
 * </ol>
 *
 * @author xuhz
 */
public class DefaultUoquoEventPublisher implements UoquoEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(DefaultUoquoEventPublisher.class);

    private final ApplicationEventPublisher delegate;

    public DefaultUoquoEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publishEvent(Object event) {
        publish(event, false);
    }

    @Override
    public void publishAfterCommit(Object event) {
        publish(event, true);
    }

    private void publish(Object event, boolean onlyAfterCommit) {
        if (event == null) {
            log.warn("publish event data is null !!");
            return;
        }
        // 补充发起人信息（必须在业务线程中完成），并得到本次使用的请求ID；业务类型、操作类型为空时抛出 IllegalArgumentException
        String traceId = EventEnricher.enrich(event);
        String businessId = (event instanceof UoquoEvent) ? ((UoquoEvent) event).getBusinessId() : "";
        String requestId = EventSupport.requestId(traceId, businessId);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        submit(event, requestId);
                    } else if (onlyAfterCommit) {
                        MDC.put("requestId", requestId);
                        try {
                            log.info("the transaction is not committed, event is discarded. {}", EventSupport.toLogString(event));
                        } finally {
                            MDC.remove("requestId");
                        }
                    } else {
                        MDC.put("requestId", requestId);
                        try {
                            log.warn("the event is published in a transaction which is not committed (status={}). {}",
                                    status, EventSupport.toLogString(event));
                        } finally {
                            MDC.remove("requestId");
                        }
                        submit(event, requestId);
                    }
                }
            });
        } else {
            submit(event, requestId);
        }
    }

    /**
     * 提交到事件线程池。任何异常都不能影响调用方（可能是事务收尾阶段）。
     */
    private void submit(Object event, String requestId) {
        try {
            EventExecutor.execute(() -> deliver(event, requestId));
        } catch (Throwable e) {
            log.error("submit event error. {}", EventSupport.toLogString(event), e);
        }
    }

    private void deliver(Object event, String requestId) {
        String oldRequestId = MDC.get("requestId");
        try {
            MDC.put("requestId", requestId);
            if (log.isDebugEnabled()) {
                log.debug("publish event: {}", EventSupport.toLogString(event));
            }
            delegate.publishEvent(event);
        } catch (Throwable e) {
            // 防止处理时覆盖了MDC信息
            MDC.put("requestId", requestId);
            log.error("publish event error. {}", EventSupport.toLogString(event), e);
        } finally {
            if (StringUtil.notNull(oldRequestId)) {
                MDC.put("requestId", oldRequestId);
            } else {
                MDC.remove("requestId");
            }
        }
    }
}
