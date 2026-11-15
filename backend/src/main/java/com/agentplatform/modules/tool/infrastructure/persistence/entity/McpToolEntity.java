package com.agentplatform.modules.tool.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.tool.domain.model.McpTool;

import java.time.Instant;

/**
 * {@code mcp_tools} 表实体（infrastructure 层）。
 *
 * <p>保存从 MCP 服务发现并同步的工具元数据。领域模型不包含创建/更新时间，
 * 因此 {@link #from(McpTool)} 每次写入都刷新这两个时间字段；表结构由 Flyway 管理。
 */
@TableName(value = "mcp_tools", autoResultMap = true)
public class McpToolEntity {
    /** 主键，由应用生成（{@code mt_xxx}）。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 所属 MCP 服务 ID。 */
    @TableField(value = "server_id") private String serverId;
    /** 平台工具键（{@code serverId.toolName}）。 */
    @TableField(value = "tool_key") private String toolKey;
    /** 服务端工具名。 */
    private String name;
    /** 工具描述。 */
    private String description;
    /** 入参 JSON Schema。 */
    @TableField(value = "input_schema") private String inputSchema;
    /** 是否启用。 */
    private boolean enabled;
    /** 风险级别：{@code read}/{@code write}。 */
    @TableField(value = "risk_level") private String riskLevel;
    /** 创建时间。 */
    @TableField(value = "created_at") private Instant createdAt;
    /** 更新时间。 */
    @TableField(value = "updated_at") private Instant updatedAt;

    /**
     * 由领域对象构建数据库实体。
     *
     * @param t 工具领域对象
     * @return 可持久化的实体（创建/更新时间均为当前时间）
     */
    public static McpToolEntity from(McpTool t) {
        McpToolEntity e = new McpToolEntity();
        e.id=t.id(); e.serverId=t.serverId(); e.toolKey=t.toolKey(); e.name=t.name();
        e.description=t.description(); e.inputSchema=t.inputSchema(); e.enabled=t.enabled(); e.riskLevel=t.riskLevel();
        e.createdAt=Instant.now(); e.updatedAt=Instant.now(); return e;
    }

    /**
     * 转换为领域对象。
     *
     * @return 工具领域对象
     */
    public McpTool toDomain() { return new McpTool(id, serverId, toolKey, name, description, inputSchema, enabled, riskLevel); }
}
