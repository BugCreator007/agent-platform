package com.agentplatform.modules.agent.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.agent.application.query.AgentSummary;
import com.agentplatform.modules.agent.domain.model.Agent;
import com.agentplatform.modules.agent.domain.repository.AgentRepository;
import com.agentplatform.modules.agent.interfaces.dto.CreateAgentRequest;
import com.agentplatform.modules.agent.interfaces.dto.UpdateAgentRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AgentApplicationService {
    private final AgentRepository repository;

    public AgentApplicationService(AgentRepository repository) {
        this.repository = repository;
    }

    public List<AgentSummary> list(String status, String q) {
        return repository.findAll().stream()
                .filter(a -> status == null || status.isBlank() || a.status().name().equalsIgnoreCase(status))
                .filter(a -> q == null || q.isBlank()
                        || a.name().contains(q) || a.code().contains(q) || a.owner().contains(q)
                        || a.team().contains(q))
                .map(AgentSummary::from)
                .toList();
    }

    public AgentSummary get(String id) {
        return AgentSummary.from(require(id));
    }

    public AgentSummary create(CreateAgentRequest request) {
        if (repository.findByCode(request.code()).isPresent()) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "智能体标识已存在");
        }
        String id = "a_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Agent agent = Agent.draft(id, request.code(), request.name());
        agent.configure(
                request.model() == null ? "Claude Sonnet 5.5" : request.model(),
                request.owner() == null ? "顾先路" : request.owner(),
                request.team() == null ? "客服平台" : request.team(),
                request.prompt() == null ? "你是企业的智能助手。" : request.prompt()
        );
        if (request.providerId() != null && !request.providerId().isBlank()) {
            agent.bindModel(request.providerId(), request.modelId(), request.model());
        }
        agent.update(null, null, null, null, null, request.tools(), request.kb());
        repository.save(agent);
        return AgentSummary.from(agent);
    }

    public AgentSummary update(String id, UpdateAgentRequest request) {
        Agent agent = require(id);
        agent.update(request.name(), request.model(), request.providerId(), request.modelId(),
                request.owner(), request.team(), request.prompt(), request.tools(), request.kb());
        repository.save(agent);
        return AgentSummary.from(agent);
    }

    public AgentSummary publish(String id) {
        Agent agent = require(id);
        agent.publish();
        repository.save(agent);
        return AgentSummary.from(agent);
    }

    public AgentSummary pause(String id) {
        Agent agent = require(id);
        agent.pause();
        repository.save(agent);
        return AgentSummary.from(agent);
    }

    public void delete(String id) {
        require(id);
        repository.deleteById(id);
    }

    public AgentSummary resume(String id) {
        Agent agent = require(id);
        agent.resume();
        repository.save(agent);
        return AgentSummary.from(agent);
    }

    private Agent require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "智能体不存在: " + id));
    }
}
