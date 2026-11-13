package com.agentplatform.modules.chat.infrastructure.persistence.entity;

import com.agentplatform.modules.chat.domain.model.SessionMessage;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "session_messages")
public class SessionMessageEntity {
    @Id private String id;
    @Column(name = "session_id") private String sessionId;
    private String role;
    @Column(columnDefinition = "LONGTEXT") private String content;
    private int tokens;
    private String model;
    @Column(name = "context_count") private int contextCount;
    @Column(name = "created_at") private Instant createdAt;

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
