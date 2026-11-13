package com.agentplatform.modules.tool.domain.repository;

import com.agentplatform.modules.tool.domain.model.McpServer;

import java.util.List;
import java.util.Optional;

public interface McpServerRepository {
    List<McpServer> findAll();
    Optional<McpServer> findById(String id);
    void save(McpServer server);
    void deleteById(String id);
}
