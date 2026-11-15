package com.agentplatform.modules.chat.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentplatform.modules.chat.infrastructure.persistence.TraceStepListTypeHandler;

import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.model.TraceStep;

import java.time.Instant;
import java.util.List;

@TableName(value = "chat_sessions", autoResultMap = true)
public class ChatSessionEntity {
    @TableId(type = IdType.INPUT) private String id;
    @TableField(value = "agent_id") private String agentId;
    @TableField(value = "agent_name") private String agentName;
    private String status;
    private int turns;
    private int tools;
    private int tokens;
    private long ms;
    @TableField(value = "time_label") private String timeLabel;
    private String topic;
    @TableField(value = "trace_json", typeHandler = TraceStepListTypeHandler.class) private List<TraceStep> trace;
    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER) private Instant createdAt;

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
