package com.agentplatform.modules.chat.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.chat.domain.model.SessionMessage;

import java.time.Instant;

/**
 * 会话消息表 {@code session_messages} 的持久化实体（infrastructure 层）。
 *
 * <p>一条记录对应一轮对话中的一条消息（user 或 assistant），按 {@code created_at} 升序读取。</p>
 */
@TableName(value = "session_messages", autoResultMap = true)
public class SessionMessageEntity {
    /** 消息 ID（主键，由应用侧生成，不使用数据库自增）。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 所属会话 ID。 */
    @TableField(value = "session_id") private String sessionId;
    /** 消息角色：user / assistant。 */
    private String role;
    /** 消息正文。 */
    private String content;
    /** 本条消息的 token 估算值。 */
    private int tokens;
    /** 生成该消息使用的模型名；用户消息为 null。 */
    private String model;
    /** 生成该消息时注入的 RAG 上下文条数。 */
    @TableField(value = "context_count") private int contextCount;
    /** 创建时间。 */
    @TableField(value = "created_at") private Instant createdAt;

    /**
     * 领域模型转持久化实体；创建时间为空时补当前时间。
     *
     * @param m 领域消息
     * @return 对应实体
     */
    public static SessionMessageEntity from(SessionMessage m) {
        SessionMessageEntity e = new SessionMessageEntity();
        e.id=m.id(); e.sessionId=m.sessionId(); e.role=m.role(); e.content=m.content();
        e.tokens=m.tokens(); e.model=m.model(); e.contextCount=m.contextCount();
        e.createdAt=m.createdAt() == null ? Instant.now() : m.createdAt(); return e;
    }
    /**
     * 持久化实体转领域模型。
     *
     * @return 领域消息
     */
    public SessionMessage toDomain() {
        return new SessionMessage(id, sessionId, role, content, tokens, model, contextCount, createdAt);
    }
}
