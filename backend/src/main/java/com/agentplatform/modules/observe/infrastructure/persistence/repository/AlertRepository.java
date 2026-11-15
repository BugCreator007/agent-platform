package com.agentplatform.modules.observe.infrastructure.persistence.repository;

import com.agentplatform.modules.observe.infrastructure.persistence.mapper.AlertMapper;

import com.agentplatform.modules.observe.domain.model.Alert;
import com.agentplatform.modules.observe.infrastructure.persistence.entity.AlertEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 告警仓储适配器（infrastructure 层）。
 *
 * <p>实现 domain 层 {@link com.agentplatform.modules.observe.domain.repository.AlertRepository}
 * 端口，包装 {@link AlertMapper} 并在实体与领域对象之间转换。</p>
 */
@Repository
public class AlertRepository implements com.agentplatform.modules.observe.domain.repository.AlertRepository {
    private final AlertMapper mapper;

    /**
     * 构造仓储。
     *
     * @param mapper 告警表 Mapper
     */
    public AlertRepository(AlertMapper mapper) { this.mapper = mapper; }

    /**
     * 查询全部告警。
     */
    @Override public List<Alert> findAll() { return mapper.selectList(null).stream().map(AlertEntity::toDomain).toList(); }

    /**
     * 按 ID 查询告警。
     */
    @Override public Optional<Alert> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(AlertEntity::toDomain); }

    /**
     * 新增或更新告警。
     */
    @Override public void save(Alert alert) { mapper.insertOrUpdate(AlertEntity.from(alert)); }
}
