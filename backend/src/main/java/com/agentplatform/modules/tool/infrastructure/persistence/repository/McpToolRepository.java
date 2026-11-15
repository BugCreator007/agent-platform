package com.agentplatform.modules.tool.infrastructure.persistence.repository;

import com.agentplatform.modules.tool.infrastructure.persistence.mapper.McpToolMapper;

import com.agentplatform.modules.tool.domain.model.McpTool;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpToolEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class McpToolRepository implements com.agentplatform.modules.tool.domain.repository.McpToolRepository {
    private final McpToolMapper mapper;
    public McpToolRepository(McpToolMapper mapper) { this.mapper = mapper; }
    @Override public List<McpTool> findByServer(String serverId) { return mapper.findByServerId(serverId).stream().map(McpToolEntity::toDomain).toList(); }
    @Override public List<McpTool> findByKeys(List<String> keys) { return keys.isEmpty() ? List.of() : mapper.findByToolKeyIn(keys).stream().map(McpToolEntity::toDomain).toList(); }
    @Override public Optional<McpTool> findByKey(String toolKey) { return Optional.ofNullable(mapper.findByToolKey(toolKey)).map(McpToolEntity::toDomain); }
    @Override public Optional<McpTool> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(McpToolEntity::toDomain); }
    @Override public void save(McpTool tool) { mapper.insertOrUpdate(McpToolEntity.from(tool)); }
    @Override public void deleteByServer(String serverId) { mapper.deleteByServerId(serverId); }
    @Override public void deleteById(String id) { mapper.deleteById(id); }
}
