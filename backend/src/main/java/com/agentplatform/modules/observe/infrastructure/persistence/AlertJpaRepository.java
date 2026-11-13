package com.agentplatform.modules.observe.infrastructure.persistence;

import com.agentplatform.modules.observe.infrastructure.persistence.entity.AlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertJpaRepository extends JpaRepository<AlertEntity, String> {
}
