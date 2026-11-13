package com.agentplatform.modules.tool.infrastructure.persistence;

import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpToolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface McpToolJpaRepository extends JpaRepository<McpToolEntity, String> {
    List<McpToolEntity> findByServerId(String serverId);
    List<McpToolEntity> findByToolKeyIn(List<String> keys);
    McpToolEntity findByToolKey(String toolKey);
    void deleteByServerId(String serverId);
}
