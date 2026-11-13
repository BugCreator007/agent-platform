package com.agentplatform.modules.workflow.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.workflow.domain.model.Workflow;
import com.agentplatform.modules.workflow.domain.repository.WorkflowRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowApplicationService {
    private final WorkflowRepository repository;

    public WorkflowApplicationService(WorkflowRepository repository) {
        this.repository = repository;
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
        require(id);
        return List.of(
                Map.of("id", "run_" + id.substring(Math.max(0, id.length() - 4)) + "_01", "workflowName", get(id).name(), "status", "success", "duration", "2.18s", "time", "2 分钟前"),
                Map.of("id", "run_" + id.substring(Math.max(0, id.length() - 4)) + "_02", "workflowName", get(id).name(), "status", "success", "duration", "1.94s", "time", "18 分钟前"),
                Map.of("id", "run_" + id.substring(Math.max(0, id.length() - 4)) + "_03", "workflowName", get(id).name(), "status", "failed", "duration", "3.02s", "time", "43 分钟前")
        );
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
