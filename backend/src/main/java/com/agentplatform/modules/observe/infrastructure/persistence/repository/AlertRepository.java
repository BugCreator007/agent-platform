package com.agentplatform.modules.observe.infrastructure.persistence.repository;

import com.agentplatform.modules.observe.infrastructure.persistence.mapper.AlertMapper;

import com.agentplatform.modules.observe.domain.model.Alert;
import com.agentplatform.modules.observe.infrastructure.persistence.entity.AlertEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AlertRepository implements com.agentplatform.modules.observe.domain.repository.AlertRepository {
    private final AlertMapper mapper;
    public AlertRepository(AlertMapper mapper) { this.mapper = mapper; }
    @Override public List<Alert> findAll() { return mapper.selectList(null).stream().map(AlertEntity::toDomain).toList(); }
    @Override public Optional<Alert> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(AlertEntity::toDomain); }
    @Override public void save(Alert alert) { mapper.insertOrUpdate(AlertEntity.from(alert)); }
}
