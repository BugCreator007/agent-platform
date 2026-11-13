package com.agentplatform.modules.model.infrastructure.persistence;

import com.agentplatform.modules.model.infrastructure.persistence.entity.ModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelJpaRepository extends JpaRepository<ModelEntity, String> {
    void deleteByProviderId(String providerId);
}
