package com.agentplatform.modules.tool.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.tool.domain.model.McpToolCall;

import java.time.Instant;

@TableName(value = "mcp_tool_calls", autoResultMap = true)
public class McpToolCallEntity {
    @TableId(type = IdType.INPUT) private String id;
    @TableField(value = "server_id") private String serverId;
    @TableField(value = "tool_key") private String toolKey;
    @TableField(value = "tool_name") private String toolName;
    @TableField(value = "request_json") private String requestJson;
    @TableField(value = "response_json") private String responseJson;
    private String status;
    @TableField(value = "duration_ms") private long durationMs;
    @TableField(value = "created_at") private Instant createdAt;
    public static McpToolCallEntity from(McpToolCall c) {
        McpToolCallEntity e = new McpToolCallEntity();
        e.id=c.id(); e.serverId=c.serverId(); e.toolKey=c.toolKey(); e.toolName=c.toolName();
        e.requestJson=c.requestJson(); e.responseJson=c.responseJson(); e.status=c.status();
        e.durationMs=c.durationMs(); e.createdAt=c.createdAt() == null ? Instant.now() : c.createdAt(); return e;
    }
    public McpToolCall toDomain() { return new McpToolCall(id, serverId, toolKey, toolName, requestJson, responseJson, status, durationMs, createdAt); }
}
