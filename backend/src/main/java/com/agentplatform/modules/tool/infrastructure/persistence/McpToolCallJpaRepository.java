package com.agentplatform.modules.tool.infrastructure.persistence;

import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpToolCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface McpToolCallJpaRepository extends JpaRepository<McpToolCallEntity, String> {
    List<McpToolCallEntity> findTop50ByServerIdOrderByCreatedAtDesc(String serverId);
}
