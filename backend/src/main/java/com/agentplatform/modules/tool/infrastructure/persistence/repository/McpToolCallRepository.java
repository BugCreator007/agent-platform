package com.agentplatform.modules.tool.infrastructure.persistence.repository;

import com.agentplatform.modules.tool.infrastructure.persistence.mapper.McpToolCallMapper;

import com.agentplatform.modules.tool.domain.model.McpToolCall;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpToolCallEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class McpToolCallRepository implements com.agentplatform.modules.tool.domain.repository.McpToolCallRepository {
    private final McpToolCallMapper mapper;
    public McpToolCallRepository(McpToolCallMapper mapper) { this.mapper = mapper; }
    @Override public void save(McpToolCall call) { mapper.insertOrUpdate(McpToolCallEntity.from(call)); }
    @Override public List<McpToolCall> recent(String serverId, int limit) {
        return mapper.findTop50ByServerIdOrderByCreatedAtDesc(serverId).stream().limit(limit).map(McpToolCallEntity::toDomain).toList();
    }
}
