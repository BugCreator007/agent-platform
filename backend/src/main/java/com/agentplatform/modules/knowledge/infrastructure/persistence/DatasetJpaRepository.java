package com.agentplatform.modules.knowledge.infrastructure.persistence;

import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetJpaRepository extends JpaRepository<DatasetEntity, String> {
}
