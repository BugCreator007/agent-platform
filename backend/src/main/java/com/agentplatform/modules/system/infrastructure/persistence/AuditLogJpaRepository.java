package com.agentplatform.modules.system.infrastructure.persistence;

import com.agentplatform.modules.system.infrastructure.persistence.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {
}
