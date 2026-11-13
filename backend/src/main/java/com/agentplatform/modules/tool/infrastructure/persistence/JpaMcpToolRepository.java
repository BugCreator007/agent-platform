package com.agentplatform.modules.tool.infrastructure.persistence;

import com.agentplatform.modules.tool.domain.model.McpTool;
import com.agentplatform.modules.tool.domain.repository.McpToolRepository;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpToolEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaMcpToolRepository implements McpToolRepository {
    private final McpToolJpaRepository jpa;
    public JpaMcpToolRepository(McpToolJpaRepository jpa) { this.jpa=jpa; }
    @Override public List<McpTool> findByServer(String serverId) { return jpa.findByServerId(serverId).stream().map(McpToolEntity::toDomain).toList(); }
    @Override public List<McpTool> findByKeys(List<String> keys) { return keys.isEmpty() ? List.of() : jpa.findByToolKeyIn(keys).stream().map(McpToolEntity::toDomain).toList(); }
    @Override public Optional<McpTool> findByKey(String toolKey) { return Optional.ofNullable(jpa.findByToolKey(toolKey)).map(McpToolEntity::toDomain); }
    @Override public Optional<McpTool> findById(String id) { return jpa.findById(id).map(McpToolEntity::toDomain); }
    @Override public void save(McpTool tool) { jpa.save(McpToolEntity.from(tool)); }
    @Override public void deleteByServer(String serverId) { jpa.deleteByServerId(serverId); }
    @Override public void deleteById(String id) { jpa.deleteById(id); }
}
