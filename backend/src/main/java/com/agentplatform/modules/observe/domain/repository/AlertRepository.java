package com.agentplatform.modules.observe.domain.repository;

import com.agentplatform.modules.observe.domain.model.Alert;

import java.util.List;
import java.util.Optional;

/**
 * 告警仓储端口（domain 层），由基础设施层 MyBatis 适配器实现。
 */
public interface AlertRepository {
    /**
     * 查询全部告警。
     *
     * @return 告警列表
     */
    List<Alert> findAll();

    /**
     * 按 ID 查询告警。
     *
     * @param id 告警 ID
     * @return 告警（可能为空）
     */
    Optional<Alert> findById(String id);

    /**
     * 保存（新增或更新）告警。
     *
     * @param alert 告警
     */
    void save(Alert alert);
}
