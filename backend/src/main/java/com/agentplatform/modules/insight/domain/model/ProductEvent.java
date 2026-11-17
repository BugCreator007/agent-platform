package com.agentplatform.modules.insight.domain.model;

import java.time.Instant;
import java.util.Map;

/**
 * 产品埋点事件（领域模型）。
 *
 * <p>事件是只增不改的行为日志：只携带 ID、耗时、条数等结构化属性，
 * 不记录问题/回答原文与个人信息。{@code eventId} 唯一，用于重试幂等。</p>
 *
 * @param eventId    事件唯一 ID
 * @param eventName  事件名（小写下划线）
 * @param userId     操作人登录名；匿名事件可为空
 * @param sessionId  会话 ID，可为空
 * @param messageId  消息 ID，可为空
 * @param agentId    智能体 ID，可为空
 * @param properties 结构化属性，仅允许标量值
 * @param occurredAt 发生时间
 */
public record ProductEvent(
        String eventId, String eventName, String userId, String sessionId,
        String messageId, String agentId, Map<String, Object> properties, Instant occurredAt
) {
    /**
     * 规范化属性，避免调用方传入可变 Map 或 null。
     */
    public ProductEvent {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
