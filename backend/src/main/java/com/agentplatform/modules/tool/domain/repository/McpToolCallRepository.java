package com.agentplatform.modules.tool.domain.repository;

import com.agentplatform.modules.tool.domain.model.McpToolCall;

import java.util.List;

public interface McpToolCallRepository {
    void save(McpToolCall call);
    List<McpToolCall> recent(String serverId, int limit);
}
