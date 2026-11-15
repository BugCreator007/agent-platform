package com.agentplatform.modules.agent.infrastructure.persistence.repository;

import com.agentplatform.modules.agent.infrastructure.persistence.mapper.AgentMapper;

import com.agentplatform.modules.agent.domain.model.Agent;
import com.agentplatform.modules.agent.infrastructure.persistence.entity.AgentEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AgentRepository implements com.agentplatform.modules.agent.domain.repository.AgentRepository {
    private final AgentMapper mapper;

    public AgentRepository(AgentMapper mapper) { this.mapper = mapper; }

    @Override public Optional<Agent> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(AgentEntity::toDomain); }
    @Override public Optional<Agent> findByCode(String code) { return Optional.ofNullable(mapper.findByCode(code)).map(AgentEntity::toDomain); }
    @Override public List<Agent> findAll() { return mapper.selectList(null).stream().map(AgentEntity::toDomain).toList(); }
    @Override public void save(Agent agent) { mapper.insertOrUpdate(AgentEntity.from(agent)); }
    @Override public void deleteById(String id) { mapper.deleteById(id); }
}
