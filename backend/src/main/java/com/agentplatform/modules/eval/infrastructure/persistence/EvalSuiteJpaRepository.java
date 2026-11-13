package com.agentplatform.modules.eval.infrastructure.persistence;

import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalSuiteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalSuiteJpaRepository extends JpaRepository<EvalSuiteEntity, String> {
}
