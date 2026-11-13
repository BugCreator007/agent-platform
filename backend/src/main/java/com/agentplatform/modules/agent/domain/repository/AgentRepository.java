package com.agentplatform.modules.agent.domain.repository;

import com.agentplatform.modules.agent.domain.model.Agent;

import java.util.List;
import java.util.Optional;

public interface AgentRepository {
    Optional<Agent> findById(String id);
    Optional<Agent> findByCode(String code);
    List<Agent> findAll();
    void save(Agent agent);
    void deleteById(String id);
}
