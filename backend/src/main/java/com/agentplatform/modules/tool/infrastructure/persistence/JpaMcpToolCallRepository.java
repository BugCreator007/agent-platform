package com.agentplatform.modules.tool.infrastructure.persistence;

import com.agentplatform.modules.tool.domain.model.McpToolCall;
import com.agentplatform.modules.tool.domain.repository.McpToolCallRepository;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpToolCallEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JpaMcpToolCallRepository implements McpToolCallRepository {
    private final McpToolCallJpaRepository jpa;
    public JpaMcpToolCallRepository(McpToolCallJpaRepository jpa) { this.jpa=jpa; }
    @Override public void save(McpToolCall call) { jpa.save(McpToolCallEntity.from(call)); }
    @Override public List<McpToolCall> recent(String serverId, int limit) {
        return jpa.findTop50ByServerIdOrderByCreatedAtDesc(serverId).stream().limit(limit).map(McpToolCallEntity::toDomain).toList();
    }
}
