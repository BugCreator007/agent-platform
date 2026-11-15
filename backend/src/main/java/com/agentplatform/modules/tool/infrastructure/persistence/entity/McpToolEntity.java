package com.agentplatform.modules.tool.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.tool.domain.model.McpTool;

import java.time.Instant;

@TableName(value = "mcp_tools", autoResultMap = true)
public class McpToolEntity {
    @TableId(type = IdType.INPUT) private String id;
    @TableField(value = "server_id") private String serverId;
    @TableField(value = "tool_key") private String toolKey;
    private String name;
    private String description;
    @TableField(value = "input_schema") private String inputSchema;
    private boolean enabled;
    @TableField(value = "risk_level") private String riskLevel;
    @TableField(value = "created_at") private Instant createdAt;
    @TableField(value = "updated_at") private Instant updatedAt;
    public static McpToolEntity from(McpTool t) {
        McpToolEntity e = new McpToolEntity();
        e.id=t.id(); e.serverId=t.serverId(); e.toolKey=t.toolKey(); e.name=t.name();
        e.description=t.description(); e.inputSchema=t.inputSchema(); e.enabled=t.enabled(); e.riskLevel=t.riskLevel();
        e.createdAt=Instant.now(); e.updatedAt=Instant.now(); return e;
    }
    public McpTool toDomain() { return new McpTool(id, serverId, toolKey, name, description, inputSchema, enabled, riskLevel); }
}
