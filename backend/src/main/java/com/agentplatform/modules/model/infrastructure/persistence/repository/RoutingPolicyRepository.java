package com.agentplatform.modules.model.infrastructure.persistence.repository;

import com.agentplatform.modules.model.infrastructure.persistence.mapper.RoutingPolicyMapper;

import com.agentplatform.modules.model.infrastructure.persistence.entity.RoutingPolicyEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 路由策略单表仓储（infrastructure 层），包装 {@link RoutingPolicyMapper}。
 * 全局策略固定使用主键 “default”。
 */
@Repository
public class RoutingPolicyRepository {
    private final RoutingPolicyMapper mapper;

    /**
     * 构造仓储。
     *
     * @param mapper 路由策略表 Mapper
     */
    public RoutingPolicyRepository(RoutingPolicyMapper mapper) { this.mapper = mapper; }

    /**
     * 按主键查询策略实体。
     *
     * @param id 主键（固定 default）
     * @return 实体（可能为空）
     */
    public Optional<RoutingPolicyEntity> findById(String id) { return Optional.ofNullable(mapper.selectById(id)); }

    /**
     * 新增或更新策略实体。
     *
     * @param policy 实体
     */
    public void save(RoutingPolicyEntity policy) { mapper.insertOrUpdate(policy); }
}
