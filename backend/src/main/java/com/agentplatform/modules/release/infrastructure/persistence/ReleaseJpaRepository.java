package com.agentplatform.modules.release.infrastructure.persistence;

import com.agentplatform.modules.release.infrastructure.persistence.entity.ReleaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseJpaRepository extends JpaRepository<ReleaseEntity, String> {
}
