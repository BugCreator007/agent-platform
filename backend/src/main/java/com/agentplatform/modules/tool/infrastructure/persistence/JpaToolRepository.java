package com.agentplatform.modules.tool.infrastructure.persistence;

import com.agentplatform.modules.tool.domain.model.Tool;
import com.agentplatform.modules.tool.domain.repository.ToolRepository;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.ToolEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
public class JpaToolRepository implements ToolRepository {
    private final ToolJpaRepository jpa;
    public JpaToolRepository(ToolJpaRepository jpa) { this.jpa = jpa; }
    @Override public List<Tool> findAll() { return jpa.findAll().stream().map(ToolEntity::toDomain).toList(); }
    @Override public Optional<Tool> findByName(String name) { return jpa.findById(name).map(ToolEntity::toDomain); }
    @Override public void save(Tool tool) { jpa.save(ToolEntity.from(tool)); }
    @Override public void deleteByName(String name) { jpa.deleteById(name); }
}
