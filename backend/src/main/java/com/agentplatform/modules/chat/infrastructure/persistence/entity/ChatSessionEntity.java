package com.agentplatform.modules.chat.infrastructure.persistence.entity;

import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.model.TraceStep;
import com.agentplatform.modules.chat.infrastructure.persistence.TraceStepListConverter;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "chat_sessions")
public class ChatSessionEntity {
    @Id private String id;
    @Column(name = "agent_id") private String agentId;
    @Column(name = "agent_name") private String agentName;
    private String status;
    private int turns;
    private int tools;
    private int tokens;
    private long ms;
    @Column(name = "time_label") private String timeLabel;
    @Column(columnDefinition = "TEXT") private String topic;
    @Convert(converter = TraceStepListConverter.class)
    @Column(name = "trace_json", columnDefinition = "TEXT") private List<TraceStep> trace;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;

    public static ChatSessionEntity from(ChatSession s) {
        ChatSessionEntity e = new ChatSessionEntity();
        e.id = s.id(); e.agentId = s.agentId(); e.agentName = s.agentName(); e.status = s.status();
        e.turns = s.turns(); e.tools = s.tools(); e.tokens = s.tokens(); e.ms = s.ms(); e.timeLabel = s.time();
        e.topic = s.topic(); e.trace = s.trace();
        return e;
    }

    public ChatSession toDomain() {
        return new ChatSession(id, agentId, agentName, status, turns, tools, tokens, ms,
                timeLabel, topic, trace == null ? List.of() : trace);
    }

    public String getAgentId() { return agentId; }
    public String getAgentName() { return agentName; }
    public String getStatus() { return status; }
    public int getTools() { return tools; }
    public int getTokens() { return tokens; }
    public long getMs() { return ms; }
    public Instant getCreatedAt() { return createdAt; }
}
