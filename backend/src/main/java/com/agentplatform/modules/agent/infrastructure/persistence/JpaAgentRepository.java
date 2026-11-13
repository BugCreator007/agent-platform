package com.agentplatform.modules.agent.infrastructure.persistence;

import com.agentplatform.modules.agent.domain.model.Agent;
import com.agentplatform.modules.agent.domain.repository.AgentRepository;
import com.agentplatform.modules.agent.infrastructure.persistence.entity.AgentEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
public class JpaAgentRepository implements AgentRepository {
    private final AgentJpaRepository jpa;

    public JpaAgentRepository(AgentJpaRepository jpa) { this.jpa = jpa; }

    @Override public Optional<Agent> findById(String id) { return jpa.findById(id).map(AgentEntity::toDomain); }
    @Override public Optional<Agent> findByCode(String code) { return jpa.findByCode(code).map(AgentEntity::toDomain); }
    @Override public List<Agent> findAll() { return jpa.findAll().stream().map(AgentEntity::toDomain).toList(); }
    @Override public void save(Agent agent) { jpa.save(AgentEntity.from(agent)); }
    @Override public void deleteById(String id) { jpa.deleteById(id); }
}
