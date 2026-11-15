package com.agentplatform.common.domain;

import java.time.Instant;

/**
 * 领域事件基础契约。
 *
 * <p>位于共享内核（common）层，各限界上下文的领域事件实现该接口，
 * 便于统一携带事件标识与发生时间，供审计、消息投递等基础设施消费。</p>
 */
public interface DomainEvent {
    /**
     * 事件唯一标识，用于幂等消费与链路追踪。
     *
     * @return 事件 ID
     */
    String eventId();

    /**
     * 事件发生时间。
     *
     * @return 发生时刻
     */
    Instant occurredAt();
}
