package com.agentplatform.modules.tool.infrastructure.persistence;

import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpServerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpServerJpaRepository extends JpaRepository<McpServerEntity, String> {
}
