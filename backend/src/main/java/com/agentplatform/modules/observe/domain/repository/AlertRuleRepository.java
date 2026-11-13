package com.agentplatform.modules.observe.domain.repository;

import com.agentplatform.modules.observe.domain.model.AlertRule;

import java.util.List;
import java.util.Optional;

public interface AlertRuleRepository {
    List<AlertRule> findAll();
    Optional<AlertRule> findById(String id);
    void save(AlertRule rule);
}
