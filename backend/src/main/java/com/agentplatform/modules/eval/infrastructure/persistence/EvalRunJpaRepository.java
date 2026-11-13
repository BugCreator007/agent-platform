package com.agentplatform.modules.eval.infrastructure.persistence;

import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalRunJpaRepository extends JpaRepository<EvalRunEntity, String> {
}
