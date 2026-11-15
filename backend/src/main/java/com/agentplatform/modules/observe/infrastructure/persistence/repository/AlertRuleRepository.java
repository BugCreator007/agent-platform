package com.agentplatform.modules.observe.infrastructure.persistence.repository;

import com.agentplatform.modules.observe.infrastructure.persistence.mapper.AlertRuleMapper;

import com.agentplatform.modules.observe.domain.model.AlertRule;
import com.agentplatform.modules.observe.infrastructure.persistence.entity.AlertRuleEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AlertRuleRepository implements com.agentplatform.modules.observe.domain.repository.AlertRuleRepository {
    private final AlertRuleMapper mapper;
    public AlertRuleRepository(AlertRuleMapper mapper) { this.mapper = mapper; }
    @Override public List<AlertRule> findAll() { return mapper.selectList(null).stream().map(AlertRuleEntity::toDomain).toList(); }
    @Override public Optional<AlertRule> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(AlertRuleEntity::toDomain); }
    @Override public void save(AlertRule rule) { mapper.insertOrUpdate(AlertRuleEntity.from(rule)); }
}
