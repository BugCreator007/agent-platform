package com.agentplatform.modules.observe.domain.repository;

import com.agentplatform.modules.observe.domain.model.AlertRule;

import java.util.List;
import java.util.Optional;

/**
 * 告警规则仓储端口（domain 层），由基础设施层 MyBatis 适配器实现。
 */
public interface AlertRuleRepository {
    /**
     * 查询全部告警规则。
     *
     * @return 规则列表
     */
    List<AlertRule> findAll();

    /**
     * 按 ID 查询告警规则。
     *
     * @param id 规则 ID
     * @return 规则（可能为空）
     */
    Optional<AlertRule> findById(String id);

    /**
     * 保存（新增或更新）告警规则。
     *
     * @param rule 规则
     */
    void save(AlertRule rule);
}
