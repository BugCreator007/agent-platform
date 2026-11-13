package com.agentplatform.modules.system.infrastructure.persistence;

import com.agentplatform.modules.system.infrastructure.persistence.entity.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyJpaRepository extends JpaRepository<ApiKeyEntity, String> {
}
