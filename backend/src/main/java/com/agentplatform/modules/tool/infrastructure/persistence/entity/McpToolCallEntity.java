package com.agentplatform.modules.tool.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.tool.domain.model.McpToolCall;

import java.time.Instant;

/**
 * {@code mcp_tool_calls} 表实体（infrastructure 层）。
 *
 * <p>保存 MCP 工具调用审计记录（请求/响应 JSON、状态、耗时）。表结构由 Flyway 管理，
 * 记录只追加不更新；{@code createdAt} 为空时由 {@link #from(McpToolCall)} 补当前时间。
 */
@TableName(value = "mcp_tool_calls", autoResultMap = true)
public class McpToolCallEntity {
    /** 主键，由应用生成（{@code call_xxx}）。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 所属 MCP 服务 ID。 */
    @TableField(value = "server_id") private String serverId;
    /** 平台工具键。 */
    @TableField(value = "tool_key") private String toolKey;
    /** 调用时传给服务端的工具名。 */
    @TableField(value = "tool_name") private String toolName;
    /** 调用参数 JSON。 */
    @TableField(value = "request_json") private String requestJson;
    /** 原始响应 JSON，传输失败时可为空。 */
    @TableField(value = "response_json") private String responseJson;
    /** 调用状态：{@code SUCCESS}/{@code ERROR}/{@code FAILED}。 */
    private String status;
    /** 调用耗时（毫秒）。 */
    @TableField(value = "duration_ms") private long durationMs;
    /** 调用发生时间。 */
    @TableField(value = "created_at") private Instant createdAt;

    /**
     * 由领域对象构建数据库实体。
     *
     * @param c 调用记录领域对象
     * @return 可持久化的实体；缺少创建时间时补当前时间
     */
    public static McpToolCallEntity from(McpToolCall c) {
        McpToolCallEntity e = new McpToolCallEntity();
        e.id=c.id(); e.serverId=c.serverId(); e.toolKey=c.toolKey(); e.toolName=c.toolName();
        e.requestJson=c.requestJson(); e.responseJson=c.responseJson(); e.status=c.status();
        e.durationMs=c.durationMs(); e.createdAt=c.createdAt() == null ? Instant.now() : c.createdAt(); return e;
    }

    /**
     * 转换为领域对象。
     *
     * @return 调用记录领域对象
     */
    public McpToolCall toDomain() { return new McpToolCall(id, serverId, toolKey, toolName, requestJson, responseJson, status, durationMs, createdAt); }
}
