package com.agentplatform.modules.model.infrastructure.persistence;

import com.agentplatform.modules.model.infrastructure.persistence.entity.RoutingPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutingJpaRepository extends JpaRepository<RoutingPolicyEntity, String> {
}
