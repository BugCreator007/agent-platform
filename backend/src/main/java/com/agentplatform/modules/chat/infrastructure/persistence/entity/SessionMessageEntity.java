package com.agentplatform.modules.chat.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.chat.domain.model.SessionMessage;

import java.time.Instant;

@TableName(value = "session_messages", autoResultMap = true)
public class SessionMessageEntity {
    @TableId(type = IdType.INPUT) private String id;
    @TableField(value = "session_id") private String sessionId;
    private String role;
    private String content;
    private int tokens;
    private String model;
    @TableField(value = "context_count") private int contextCount;
    @TableField(value = "created_at") private Instant createdAt;

    public static SessionMessageEntity from(SessionMessage m) {
        SessionMessageEntity e = new SessionMessageEntity();
        e.id=m.id(); e.sessionId=m.sessionId(); e.role=m.role(); e.content=m.content();
        e.tokens=m.tokens(); e.model=m.model(); e.contextCount=m.contextCount();
        e.createdAt=m.createdAt() == null ? Instant.now() : m.createdAt(); return e;
    }
    public SessionMessage toDomain() {
        return new SessionMessage(id, sessionId, role, content, tokens, model, contextCount, createdAt);
    }
}
