package com.agentplatform.modules.workflow.application.service;

import com.agentplatform.modules.chat.application.runtime.ChatCompletionService;
import com.agentplatform.modules.knowledge.application.query.RetrievalResult;
import com.agentplatform.modules.knowledge.application.service.KnowledgeApplicationService;
import com.agentplatform.modules.tool.application.service.ToolExecutionService;
import com.agentplatform.modules.workflow.domain.model.Workflow;
import com.agentplatform.modules.workflow.domain.model.WorkflowInstance;
import com.agentplatform.modules.workflow.domain.model.WorkflowNode;
import com.agentplatform.modules.workflow.domain.model.WorkflowNodeRun;
import com.agentplatform.modules.workflow.domain.repository.WorkflowInstanceRepository;
import com.agentplatform.modules.workflow.domain.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkflowExecutionService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionService.class);
    private final WorkflowRepository workflows;
    private final WorkflowInstanceRepository instances;
    private final KnowledgeApplicationService knowledge;
    private final ChatCompletionService chat;
    private final ToolExecutionService tools;
    private final ObjectMapper mapper;
    private final TaskExecutor executor;
    private final int maxAttempts;

    public WorkflowExecutionService(WorkflowRepository workflows, WorkflowInstanceRepository instances,
                                    KnowledgeApplicationService knowledge, ChatCompletionService chat,
                                    ToolExecutionService tools,
                                    ObjectMapper mapper, @Qualifier("workflowExecutor") TaskExecutor workflowExecutor,
                                    @Value("${app.workflow.max-attempts:2}") int maxAttempts) {
        this.workflows = workflows; this.instances = instances; this.knowledge = knowledge; this.chat = chat;
        this.tools = tools; this.mapper = mapper; this.executor = workflowExecutor; this.maxAttempts = maxAttempts;
    }

    public WorkflowInstance start(String workflowId, String input) {
        Workflow workflow = workflows.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("流程不存在: " + workflowId));
        WorkflowInstance instance = new WorkflowInstance(
                "wfrun_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                workflow.id(), workflow.name(), "RUNNING", "manual", input, null, null, Instant.now(), null, null);
        instances.save(instance);
        executor.execute(() -> execute(instance.id(), 0));
        return instance;
    }

    public WorkflowInstance approve(String instanceId, String comment) {
        WorkflowInstance instance = instances.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("实例不存在: " + instanceId));
        if (!"WAITING_APPROVAL".equals(instance.status())) {
            throw new IllegalStateException("当前实例不在待审批状态");
        }
        List<WorkflowNodeRun> runs = instances.findNodeRuns(instanceId);
        int index = 0;
        Workflow workflow = workflows.findById(instance.workflowId()).orElseThrow();
        for (int i = 0; i < workflow.nodes().size(); i++) {
            if (workflow.nodes().get(i).key().equals(instance.currentNode())) { index = i; break; }
        }
        runs.stream().filter(r -> "WAITING_APPROVAL".equals(r.status())).forEach(r -> {
            r.succeed(comment == null ? "approved" : comment);
            instances.saveNodeRun(r);
        });
        instance.running(instance.currentNode());
        instances.save(instance);
        int next = index + 1;
        executor.execute(() -> execute(instanceId, next));
        return instance;
    }

    public WorkflowInstance cancel(String instanceId) {
        WorkflowInstance instance = instances.findById(instanceId).orElseThrow();
        instance.cancel();
        instances.save(instance);
        return instance;
    }

    public List<WorkflowInstance> list() { return instances.findAll(); }

    public WorkflowInstance get(String id) {
        return instances.findById(id).orElseThrow(() -> new IllegalArgumentException("实例不存在: " + id));
    }

    public List<WorkflowNodeRun> nodeRuns(String id) {
        return instances.findNodeRuns(id);
    }

    private void execute(String instanceId, int startIndex) {
        WorkflowInstance instance = instances.findById(instanceId).orElse(null);
        if (instance == null) return;
        Workflow workflow = workflows.findById(instance.workflowId()).orElse(null);
        if (workflow == null) { instance.fail("流程定义不存在"); instances.save(instance); return; }
        List<WorkflowNode> nodes = workflow.nodes();
        try {
            for (int i = startIndex; i < nodes.size(); i++) {
                WorkflowNode node = nodes.get(i);
                instance.running(node.key());
                instances.save(instance);
                WorkflowNodeRun run = new WorkflowNodeRun("noderun_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                        instance.id(), node.key(), node.type(), node.title(), "RUNNING", instance.inputJson(),
                        null, 1, Instant.now(), null, null);
                instances.saveNodeRun(run);
                try {
                    String output = executeNode(node, instance.inputJson());
                    if ("APPROVAL".equalsIgnoreCase(node.type())) {
                        run.waitApproval();
                        instances.saveNodeRun(run);
                        instance.waitApproval(node.key());
                        instances.save(instance);
                        return;
                    }
                    run.succeed(output);
                    instances.saveNodeRun(run);
                } catch (Exception e) {
                    boolean ok = false;
                    for (int attempt = 2; attempt <= maxAttempts; attempt++) {
                        try {
                            String output = executeNode(node, instance.inputJson());
                            run.succeed(output);
                            instances.saveNodeRun(run);
                            ok = true;
                            break;
                        } catch (Exception retry) { log.warn("workflow node retry {} failed: {}", attempt, retry.getMessage()); }
                    }
                    if (!ok) {
                        run.fail(e.getMessage());
                        instances.saveNodeRun(run);
                        instance.fail("节点 " + node.title() + " 执行失败: " + e.getMessage());
                        instances.save(instance);
                        workflow.recordRun(false);
                        workflows.save(workflow);
                        return;
                    }
                }
            }
            instance.succeed("{\"status\":\"SUCCEEDED\"}");
            instances.save(instance);
            workflow.recordRun(true);
            workflows.save(workflow);
        } catch (Exception e) {
            log.error("workflow execution failed: {}", instanceId, e);
            instance.fail(e.getMessage());
            instances.save(instance);
            workflow.recordRun(false);
            workflows.save(workflow);
        }
    }

    private String executeNode(WorkflowNode node, String input) throws Exception {
        String type = node.type() == null ? "TOOL" : node.type().toUpperCase();
        return switch (type) {
            case "START", "TRIGGER", "END" -> json(Map.of("node", node.title(), "status", "ok"));
            case "KNOWLEDGE" -> {
                String datasetId = node.meta() == null ? "" : node.meta().split("[\\s·]")[0];
                if (datasetId.isBlank()) yield json(Map.of("node", node.title(), "status", "skipped"));
                RetrievalResult result = knowledge.retrieve(datasetId, input == null ? "" : input);
                yield json(Map.of("node", node.title(), "hits", result.hits().size(),
                        "context", result.hits().stream().map(h -> h.text()).limit(3).toList()));
            }
            case "MODEL" -> json(Map.of("node", node.title(),
                    "output", chat.complete("你是流程节点执行器。节点：" + node.title() + "，配置：" + node.meta(), input == null ? "" : input)));
            case "TOOL" -> json(Map.of("node", node.title(), "tool", node.meta(),
                    "output", tools.execute(node.meta() == null ? "" : node.meta().split("[\\s·]")[0], input)));
            case "CONDITION" -> {
                boolean condition = input != null && !input.toLowerCase().contains("false");
                yield json(Map.of("node", node.title(), "condition", condition));
            }
            case "APPROVAL" -> json(Map.of("node", node.title(), "status", "waiting_approval", "assignee", node.meta()));
            case "NOTIFY" -> json(Map.of("node", node.title(), "status", "sent", "channel", node.meta()));
            default -> json(Map.of("node", node.title(), "status", "ok", "meta", node.meta()));
        };
    }

    private String json(Object value) throws Exception { return mapper.writeValueAsString(value); }
}
