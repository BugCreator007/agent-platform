package com.agentplatform.modules.model.infrastructure.persistence.repository;

import com.agentplatform.modules.model.infrastructure.persistence.mapper.RoutingPolicyMapper;

import com.agentplatform.modules.model.infrastructure.persistence.entity.RoutingPolicyEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class RoutingPolicyRepository {
    private final RoutingPolicyMapper mapper;

    public RoutingPolicyRepository(RoutingPolicyMapper mapper) { this.mapper = mapper; }

    public Optional<RoutingPolicyEntity> findById(String id) { return Optional.ofNullable(mapper.selectById(id)); }
    public void save(RoutingPolicyEntity policy) { mapper.insertOrUpdate(policy); }
}
