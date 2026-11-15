package com.agentplatform.modules.observe.infrastructure.persistence.repository;

import com.agentplatform.modules.observe.infrastructure.persistence.mapper.AlertRuleMapper;

import com.agentplatform.modules.observe.domain.model.AlertRule;
import com.agentplatform.modules.observe.infrastructure.persistence.entity.AlertRuleEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 告警规则仓储适配器（infrastructure 层）。
 *
 * <p>实现 domain 层 {@link com.agentplatform.modules.observe.domain.repository.AlertRuleRepository}
 * 端口，包装 {@link AlertRuleMapper} 并在实体与领域对象之间转换。</p>
 */
@Repository
public class AlertRuleRepository implements com.agentplatform.modules.observe.domain.repository.AlertRuleRepository {
    private final AlertRuleMapper mapper;

    /**
     * 构造仓储。
     *
     * @param mapper 告警规则表 Mapper
     */
    public AlertRuleRepository(AlertRuleMapper mapper) { this.mapper = mapper; }

    /**
     * 查询全部告警规则。
     */
    @Override public List<AlertRule> findAll() { return mapper.selectList(null).stream().map(AlertRuleEntity::toDomain).toList(); }

    /**
     * 按 ID 查询告警规则。
     */
    @Override public Optional<AlertRule> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(AlertRuleEntity::toDomain); }

    /**
     * 新增或更新告警规则。
     */
    @Override public void save(AlertRule rule) { mapper.insertOrUpdate(AlertRuleEntity.from(rule)); }
}
