package com.agentplatform.modules.workflow.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.workflow.domain.model.Workflow;
import com.agentplatform.modules.workflow.domain.repository.WorkflowInstanceRepository;
import com.agentplatform.modules.workflow.domain.repository.WorkflowRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowApplicationService {
    private final WorkflowRepository repository;
    private final WorkflowInstanceRepository instances;

    public WorkflowApplicationService(WorkflowRepository repository, WorkflowInstanceRepository instances) {
        this.repository = repository;
        this.instances = instances;
    }

    public List<Workflow> list() { return repository.findAll(); }
    public Workflow get(String id) { return require(id); }

    public Workflow create(String name, String trigger, List<com.agentplatform.modules.workflow.domain.model.WorkflowNode> nodes) {
        String id = "wf-" + UUID.randomUUID().toString().substring(0, 8);
        Workflow workflow = new Workflow(id, name == null || name.isBlank() ? "未命名流程" : name,
                "draft", trigger == null || trigger.isBlank() ? "手动触发" : trigger,
                0, 0, "顾先路", "v1", "刚刚", nodes == null ? List.of() : nodes);
        repository.save(workflow);
        return workflow;
    }

    public Workflow update(String id, String name, String trigger, List<com.agentplatform.modules.workflow.domain.model.WorkflowNode> nodes) {
        Workflow current = require(id);
        Workflow next = new Workflow(current.id(), name == null || name.isBlank() ? current.name() : name,
                current.status(), trigger == null || trigger.isBlank() ? current.trigger() : trigger,
                current.runs(), current.success(), current.owner(), current.version(), "刚刚",
                nodes == null ? current.nodes() : nodes);
        repository.save(next);
        return next;
    }

    public List<Map<String, Object>> runs(String id) {
        Workflow workflow = require(id);
        return instances.findAll().stream()
                .filter(i -> workflow.id().equals(i.workflowId()))
                .sorted((a, b) -> b.startedAt().compareTo(a.startedAt()))
                .limit(20)
                .map(i -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", i.id());
                    row.put("workflowName", i.workflowName());
                    row.put("status", i.status().toLowerCase());
                    row.put("duration", i.finishedAt() == null ? "运行中"
                            : ((i.finishedAt().toEpochMilli() - i.startedAt().toEpochMilli()) + " ms"));
                    row.put("time", i.startedAt().toString());
                    return row;
                })
                .toList();
    }

    public Workflow test(String id) {
        Workflow workflow = require(id);
        return workflow;
    }

    public Workflow publish(String id) {
        Workflow workflow = require(id);
        workflow.publish();
        repository.save(workflow);
        return workflow;
    }

    private Workflow require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "流程不存在: " + id));
    }
}
