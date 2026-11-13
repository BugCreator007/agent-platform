package com.agentplatform.modules.tool.infrastructure.persistence.entity;

import com.agentplatform.modules.tool.domain.model.McpToolCall;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "mcp_tool_calls")
public class McpToolCallEntity {
    @Id private String id;
    @Column(name = "server_id") private String serverId;
    @Column(name = "tool_key") private String toolKey;
    @Column(name = "tool_name") private String toolName;
    @Column(name = "request_json", columnDefinition = "LONGTEXT") private String requestJson;
    @Column(name = "response_json", columnDefinition = "LONGTEXT") private String responseJson;
    private String status;
    @Column(name = "duration_ms") private long durationMs;
    @Column(name = "created_at") private Instant createdAt;
    public static McpToolCallEntity from(McpToolCall c) {
        McpToolCallEntity e = new McpToolCallEntity();
        e.id=c.id(); e.serverId=c.serverId(); e.toolKey=c.toolKey(); e.toolName=c.toolName();
        e.requestJson=c.requestJson(); e.responseJson=c.responseJson(); e.status=c.status();
        e.durationMs=c.durationMs(); e.createdAt=c.createdAt() == null ? Instant.now() : c.createdAt(); return e;
    }
    public McpToolCall toDomain() { return new McpToolCall(id, serverId, toolKey, toolName, requestJson, responseJson, status, durationMs, createdAt); }
}
