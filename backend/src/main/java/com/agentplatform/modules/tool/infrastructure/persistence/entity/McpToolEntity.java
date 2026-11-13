package com.agentplatform.modules.tool.infrastructure.persistence.entity;

import com.agentplatform.modules.tool.domain.model.McpTool;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "mcp_tools")
public class McpToolEntity {
    @Id private String id;
    @Column(name = "server_id") private String serverId;
    @Column(name = "tool_key") private String toolKey;
    private String name;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "input_schema", columnDefinition = "LONGTEXT") private String inputSchema;
    private boolean enabled;
    @Column(name = "risk_level") private String riskLevel;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    public static McpToolEntity from(McpTool t) {
        McpToolEntity e = new McpToolEntity();
        e.id=t.id(); e.serverId=t.serverId(); e.toolKey=t.toolKey(); e.name=t.name();
        e.description=t.description(); e.inputSchema=t.inputSchema(); e.enabled=t.enabled(); e.riskLevel=t.riskLevel();
        e.createdAt=Instant.now(); e.updatedAt=Instant.now(); return e;
    }
    public McpTool toDomain() { return new McpTool(id, serverId, toolKey, name, description, inputSchema, enabled, riskLevel); }
}
