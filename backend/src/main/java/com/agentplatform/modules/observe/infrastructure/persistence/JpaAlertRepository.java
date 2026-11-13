package com.agentplatform.modules.observe.infrastructure.persistence;

import com.agentplatform.modules.observe.domain.model.Alert;
import com.agentplatform.modules.observe.domain.repository.AlertRepository;
import com.agentplatform.modules.observe.infrastructure.persistence.entity.AlertEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
public class JpaAlertRepository implements AlertRepository {
    private final AlertJpaRepository jpa;
    public JpaAlertRepository(AlertJpaRepository jpa) { this.jpa = jpa; }
    @Override public List<Alert> findAll() { return jpa.findAll().stream().map(AlertEntity::toDomain).toList(); }
    @Override public Optional<Alert> findById(String id) { return jpa.findById(id).map(AlertEntity::toDomain); }
    @Override public void save(Alert alert) { jpa.save(AlertEntity.from(alert)); }
}
