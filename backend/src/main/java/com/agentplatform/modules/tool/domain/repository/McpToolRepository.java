package com.agentplatform.modules.tool.domain.repository;

import com.agentplatform.modules.tool.domain.model.McpTool;

import java.util.List;
import java.util.Optional;

public interface McpToolRepository {
    List<McpTool> findByServer(String serverId);
    List<McpTool> findByKeys(List<String> keys);
    Optional<McpTool> findByKey(String toolKey);
    Optional<McpTool> findById(String id);
    void save(McpTool tool);
    void deleteByServer(String serverId);
    void deleteById(String id);
}
