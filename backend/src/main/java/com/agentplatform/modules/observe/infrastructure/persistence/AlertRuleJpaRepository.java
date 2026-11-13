package com.agentplatform.modules.observe.infrastructure.persistence;

import com.agentplatform.modules.observe.infrastructure.persistence.entity.AlertRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRuleJpaRepository extends JpaRepository<AlertRuleEntity, String> {
}
