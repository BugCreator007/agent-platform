package com.agentplatform.modules.agent.infrastructure.persistence;

import com.agentplatform.modules.agent.infrastructure.persistence.entity.AgentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AgentJpaRepository extends JpaRepository<AgentEntity, String> {
    Optional<AgentEntity> findByCode(String code);
}
