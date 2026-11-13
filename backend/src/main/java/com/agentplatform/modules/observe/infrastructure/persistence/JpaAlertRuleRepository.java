package com.agentplatform.modules.observe.infrastructure.persistence;

import com.agentplatform.modules.observe.domain.model.AlertRule;
import com.agentplatform.modules.observe.domain.repository.AlertRuleRepository;
import com.agentplatform.modules.observe.infrastructure.persistence.entity.AlertRuleEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaAlertRuleRepository implements AlertRuleRepository {
    private final AlertRuleJpaRepository jpa;
    public JpaAlertRuleRepository(AlertRuleJpaRepository jpa) { this.jpa = jpa; }
    @Override public List<AlertRule> findAll() { return jpa.findAll().stream().map(AlertRuleEntity::toDomain).toList(); }
    @Override public Optional<AlertRule> findById(String id) { return jpa.findById(id).map(AlertRuleEntity::toDomain); }
    @Override public void save(AlertRule rule) { jpa.save(AlertRuleEntity.from(rule)); }
}
