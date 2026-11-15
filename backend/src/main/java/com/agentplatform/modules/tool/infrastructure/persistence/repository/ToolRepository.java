package com.agentplatform.modules.tool.infrastructure.persistence.repository;

import com.agentplatform.modules.tool.infrastructure.persistence.mapper.ToolMapper;

import com.agentplatform.modules.tool.domain.model.Tool;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.ToolEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ToolRepository implements com.agentplatform.modules.tool.domain.repository.ToolRepository {
    private final ToolMapper mapper;
    public ToolRepository(ToolMapper mapper) { this.mapper = mapper; }
    @Override public List<Tool> findAll() { return mapper.selectList(null).stream().map(ToolEntity::toDomain).toList(); }
    @Override public Optional<Tool> findByName(String name) { return Optional.ofNullable(mapper.selectById(name)).map(ToolEntity::toDomain); }
    @Override public void save(Tool tool) { mapper.insertOrUpdate(ToolEntity.from(tool)); }
    @Override public void deleteByName(String name) { mapper.deleteById(name); }
}
