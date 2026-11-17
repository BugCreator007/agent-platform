package com.agentplatform.modules.insight.infrastructure.persistence.entity;

import com.agentplatform.modules.insight.domain.model.ProductEvent;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * 产品事件表 {@code product_events} 的持久化实体（infrastructure 层）。
 *
 * <p>属性以 JSON 存储；entity 只负责数据库与领域对象互转，
 * 白名单与大小校验在应用层完成。</p>
 */
@TableName("product_events")
public class ProductEventEntity {
    /** 属性 JSON 序列化器。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 自增主键。 */
    @TableId(type = IdType.AUTO) private Long id;
    /** 事件唯一 ID。 */
    private String eventId;
    /** 事件编码。 */
    private String eventName;
    /** 操作人登录名。 */
    private String userId;
    /** 会话 ID。 */
    private String sessionId;
    /** 消息 ID。 */
    private String messageId;
    /** 智能体 ID。 */
    private String agentId;
    /** 结构化属性 JSON。 */
    private String propertiesJson;
    /** 发生时间。 */
    private Instant occurredAt;

    /**
     * 领域对象转持久化实体。
     *
     * @param e 事件
     * @return 对应实体
     */
    public static ProductEventEntity from(ProductEvent e) {
        ProductEventEntity entity = new ProductEventEntity();
        entity.eventId = e.eventId();
        entity.eventName = e.eventName();
        entity.userId = e.userId();
        entity.sessionId = e.sessionId();
        entity.messageId = e.messageId();
        entity.agentId = e.agentId();
        entity.occurredAt = e.occurredAt() == null ? Instant.now() : e.occurredAt();
        try {
            entity.propertiesJson = e.properties().isEmpty() ? null
                    : MAPPER.writeValueAsString(e.properties());
        } catch (Exception ignored) {
            entity.propertiesJson = null;
        }
        return entity;
    }

    /** @return 事件 ID */
    public String getEventId() { return eventId; }
    /** @return 事件编码 */
    public String getEventName() { return eventName; }
    /** @return 操作人 */
    public String getUserId() { return userId; }
    /** @return 会话 ID */
    public String getSessionId() { return sessionId; }
    /** @return 消息 ID */
    public String getMessageId() { return messageId; }
    /** @return 智能体 ID */
    public String getAgentId() { return agentId; }
    /** @return 属性 JSON */
    public String getPropertiesJson() { return propertiesJson; }
    /** @return 发生时间 */
    public Instant getOccurredAt() { return occurredAt; }
}
