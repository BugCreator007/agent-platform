package com.agentplatform.modules.tool.infrastructure.persistence;

import com.agentplatform.modules.tool.infrastructure.persistence.entity.ToolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolJpaRepository extends JpaRepository<ToolEntity, String> {
}
