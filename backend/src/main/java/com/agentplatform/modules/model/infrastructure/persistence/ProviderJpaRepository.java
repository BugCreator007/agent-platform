package com.agentplatform.modules.model.infrastructure.persistence;

import com.agentplatform.modules.model.infrastructure.persistence.entity.ProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderJpaRepository extends JpaRepository<ProviderEntity, String> {
}
