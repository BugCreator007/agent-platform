package com.agentplatform.modules.tool.domain.repository;

import com.agentplatform.modules.tool.domain.model.McpToolCall;

import java.util.List;

/**
 * MCP 调用审计仓储端口（domain 层定义，infrastructure 层实现）。
 *
 * <p>只提供追加写入与按服务查询最近记录；审计记录不支持更新或删除。
 */
public interface McpToolCallRepository {
    /**
     * 追加一条调用记录。
     *
     * @param call 调用记录
     */
    void save(McpToolCall call);

    /**
     * 查询某 MCP 服务最近的调用记录，按创建时间倒序。
     *
     * @param serverId 服务主键
     * @param limit    最多返回条数
     * @return 调用记录列表，按时间倒序
     */
    List<McpToolCall> recent(String serverId, int limit);
}
