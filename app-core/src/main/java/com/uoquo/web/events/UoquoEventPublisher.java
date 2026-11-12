/**
 * Copyright (c) 2025, www.uoquo.com All Rights Reserved.
 * 注意：本内容仅限于内部传阅，禁止外泄
 */
package com.uoquo.web.events;

import org.springframework.context.ApplicationEvent;

/**
 * 事件发布器，参考 {@link org.springframework.context.ApplicationEventPublisher}。<br>
 * 发布时自动补充发起人信息（会话、请求、操作端、操作人等），并异步投递；
 * 业务代码按“业务处理完毕、返回数据前发布事件”的约定调用即可，无需关心事务提交的时机。
 *
 * <p>事务时机：</p>
 * <ul>
 *   <li>{@link #publishEvent(Object)}：当前线程存在事务时，等事务<b>结束后</b>（提交或回滚）再投递；没有事务则立即投递。
 *       适用于“业务失败”类事件（如登录失败：先发布事件，再抛出异常导致回滚）；</li>
 *   <li>{@link #publishAfterCommit(Object)}：当前线程存在事务时，只在事务<b>提交成功后</b>投递，回滚则丢弃；没有事务则立即投递。
 *       适用于必须与数据变更保持一致的“成功”类事件。</li>
 * </ul>
 * @author xuhz
 */
public interface UoquoEventPublisher {
    default void publishEvent(ApplicationEvent event) {
        publishEvent((Object) event);
    }

    void publishEvent(Object event);

    default void publishAfterCommit(ApplicationEvent event) {
        publishAfterCommit((Object) event);
    }

    /**
     * 事务提交成功后才发布事件（没有事务时立即发布）。<br>
     * 默认实现退化为 {@link #publishEvent(Object)}，由具体的发布器覆盖。
     * @param event 事件
     */
    default void publishAfterCommit(Object event) {
        publishEvent(event);
    }
}
