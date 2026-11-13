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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class WorkflowExecutionService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionService.class);
    private final WorkflowRepository workflows;
    private final WorkflowInstanceRepository instances;
    private final KnowledgeApplicationService knowledge;
    private final ChatCompletionService chat;
    private final ToolExecutionService tools;
    private final WorkflowNotificationService notifications;
    private final WorkflowExpressionService expressions;
    private final ObjectMapper mapper;
    private final TaskExecutor executor;
    private final int maxAttempts;

    public WorkflowExecutionService(WorkflowRepository workflows, WorkflowInstanceRepository instances,
                                    KnowledgeApplicationService knowledge, ChatCompletionService chat,
                                    ToolExecutionService tools, WorkflowNotificationService notifications,
                                    WorkflowExpressionService expressions, ObjectMapper mapper,
                                    @Qualifier("workflowExecutor") TaskExecutor workflowExecutor,
                                    @Value("${app.workflow.max-attempts:2}") int maxAttempts) {
        this.workflows = workflows; this.instances = instances; this.knowledge = knowledge; this.chat = chat;
        this.tools = tools; this.notifications = notifications; this.expressions = expressions;
        this.mapper = mapper; this.executor = workflowExecutor; this.maxAttempts = Math.max(1, maxAttempts);
    }

    public WorkflowInstance start(String workflowId, String input) {
        Workflow workflow = workflows.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("流程不存在: " + workflowId));
        WorkflowInstance instance = new WorkflowInstance(
                "wfrun_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                workflow.id(), workflow.name(), "RUNNING", "manual", input, null, null,
                Instant.now(), null, null, null, null);
        instances.save(instance);
        executor.execute(() -> execute(instance.id()));
        return instance;
    }

    public WorkflowInstance approve(String instanceId, String comment) {
        WorkflowInstance instance = instances.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("实例不存在: " + instanceId));
        if (!"WAITING_APPROVAL".equals(instance.status())) {
            throw new IllegalStateException("当前实例不在待审批状态");
        }
        WorkflowRunState state = loadState(instance);
        String nodeKey = instance.currentNode();
        WorkflowNodeRun run = waitingRun(instanceId, nodeKey, "WAITING_APPROVAL");
        if (run != null) {
            run.succeed(comment == null ? "{\"approved\":true}" : json(Map.of("approved", true, "comment", comment)));
            instances.saveNodeRun(run);
        }
        state.completed.add(nodeKey);
        state.outputs.put(nodeKey, json(Map.of("approved", true,
                "comment", comment == null ? "" : comment)));
        state.waitingNode = null;
        instance.running(nodeKey);
        persist(instance, state);
        executor.execute(() -> execute(instanceId));
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
    public List<WorkflowNodeRun> nodeRuns(String id) { return instances.findNodeRuns(id); }

    /**
     * Called by the scheduler. Resumes persisted timers whose deadline has passed.
     */
    public void resumeTimers() {
        Instant now = Instant.now();
        for (WorkflowInstance instance : instances.findAll()) {
            if (!"WAITING_TIMER".equals(instance.status())) continue;
            if (instance.resumeAt() == null || instance.resumeAt().isAfter(now)) continue;
            WorkflowRunState state = loadState(instance);
            String nodeKey = instance.currentNode();
            WorkflowNodeRun run = waitingRun(instance.id(), nodeKey, "WAITING_TIMER");
            if (run == null) continue;
            String output = json(Map.of("timer", "fired", "resumedAt", now.toString()));
            run.succeed(output);
            instances.saveNodeRun(run);
            state.completed.add(nodeKey);
            state.outputs.put(nodeKey, output);
            state.waitingNode = null;
            instance.running(nodeKey);
            persist(instance, state);
            executor.execute(() -> execute(instance.id()));
            log.info("workflow timer resumed: {} node={}", instance.id(), nodeKey);
        }
    }

    /**
     * Called by the scheduler. Resumes a parent once its persisted subflow finishes.
     */
    public void resumeSubflows() {
        for (WorkflowInstance parent : instances.findAll()) {
            if (!"WAITING_SUBFLOW".equals(parent.status())) continue;
            WorkflowRunState state = loadState(parent);
            String childId = state.subflowInstanceId;
            if (childId == null || childId.isBlank()) continue;
            WorkflowInstance child = instances.findById(childId).orElse(null);
            if (child == null) {
                fail(parent, state, "子流程实例不存在: " + childId);
                continue;
            }
            if (Set.of("FAILED", "CANCELLED").contains(child.status())) {
                fail(parent, state, "子流程失败: " + child.status());
                continue;
            }
            if (!"SUCCEEDED".equals(child.status())) continue;

            String nodeKey = parent.currentNode();
            WorkflowNodeRun run = waitingRun(parent.id(), nodeKey, "WAITING_SUBFLOW");
            if (run == null) continue;
            String output = child.outputJson() == null ? json(Map.of("status", "SUCCEEDED")) : child.outputJson();
            run.succeed(output);
            instances.saveNodeRun(run);
            state.completed.add(nodeKey);
            state.outputs.put(nodeKey, output);
            state.waitingNode = null;
            state.subflowInstanceId = null;
            parent.running(nodeKey);
            persist(parent, state);
            executor.execute(() -> execute(parent.id()));
            log.info("workflow subflow resumed: {} child={}", parent.id(), childId);
        }
    }

    private void execute(String instanceId) {
        WorkflowInstance instance = instances.findById(instanceId).orElse(null);
        if (instance == null || !"RUNNING".equals(instance.status())) return;
        Workflow workflow = workflows.findById(instance.workflowId()).orElse(null);
        if (workflow == null) {
            instance.fail("流程定义不存在");
            instances.save(instance);
            return;
        }
        Graph graph = graph(workflow);
        WorkflowRunState state = loadState(instance);
        hydrate(state, instanceId);
        try {
            runLoop(instance, workflow, graph, state);
        } catch (Exception e) {
            log.error("workflow execution failed: {}", instanceId, e);
            fail(instance, state, e.getMessage() == null ? "流程执行失败" : e.getMessage());
        }
    }

    private void runLoop(WorkflowInstance instance, Workflow workflow, Graph graph, WorkflowRunState state) {
        while (true) {
            if (!"RUNNING".equals(instance.status())) return;

            List<WorkflowNode> ready = new ArrayList<>();
            List<WorkflowNode> skipped = new ArrayList<>();
            for (WorkflowNode node : graph.nodes.values()) {
                if (state.completed.contains(node.key()) || state.skipped.contains(node.key())) continue;
                List<Edge> incoming = graph.incoming.getOrDefault(node.key(), List.of());
                if (incoming.isEmpty()) {
                    ready.add(node);
                    continue;
                }
                boolean pending = false;
                int active = 0;
                for (Edge edge : incoming) {
                    if (state.inactiveEdges.contains(edge.key())) continue;
                    if (state.skipped.contains(edge.from())) continue;
                    if (state.completed.contains(edge.from())) active++;
                    else pending = true;
                }
                if (pending) continue;
                if (active == 0) skipped.add(node);
                else ready.add(node);
            }

            if (!skipped.isEmpty()) {
                for (WorkflowNode node : skipped) {
                    state.skipped.add(node.key());
                    WorkflowNodeRun run = newNodeRun(instance.id(), node, "SKIPPED",
                            json(Map.of("node", node.title(), "status", "skipped")));
                    run.skip("{\"reason\":\"没有可用的入边\"}");
                    instances.saveNodeRun(run);
                }
                persist(instance, state);
                continue;
            }

            if (ready.isEmpty()) {
                if (allResolved(graph, state)) {
                    succeed(instance, workflow, state);
                } else {
                    fail(instance, state, "流程存在无法到达的节点或环，无法继续执行");
                }
                return;
            }

            String current = ready.stream().map(WorkflowNode::key).reduce((a, b) -> a + "," + b).orElse("");
            instance.running(current.length() > 64 ? current.substring(0, 64) : current);
            instances.save(instance);

            List<CompletableFuture<NodeOutcome>> futures = new ArrayList<>();
            for (WorkflowNode node : ready) {
                futures.add(CompletableFuture.supplyAsync(() -> executeNode(instance, node, state), executor));
            }
            NodeOutcome waiting = null;
            for (CompletableFuture<NodeOutcome> future : futures) {
                NodeOutcome outcome;
                try {
                    outcome = future.get();
                } catch (Exception e) {
                    fail(instance, state, "节点执行异常: " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
                    return;
                }
                WorkflowNode node = graph.nodes.get(outcome.nodeKey());
                if ("FAILED".equals(outcome.status())) {
                    fail(instance, state, outcome.error() == null ? "节点执行失败" : outcome.error());
                    return;
                }
                if (Set.of("WAITING_APPROVAL", "WAITING_TIMER", "WAITING_SUBFLOW").contains(outcome.status())) {
                    if (waiting != null) {
                        fail(instance, state, "同一并行波次暂不支持多个同时等待节点");
                        return;
                    }
                    waiting = outcome;
                    continue;
                }
                state.completed.add(node.key());
                state.outputs.put(node.key(), outcome.output());
                if (outcome.compensation() != null && !outcome.compensation().isBlank()) {
                    state.compensations.add(new WorkflowRunState.CompensationRecord(
                            node.key(), outcome.compensation(), outcome.output()));
                }
                applyCondition(state, graph, node, outcome.output());
            }
            if (waiting != null) {
                WorkflowNode node = graph.nodes.get(waiting.nodeKey());
                state.waitingNode = node.key();
                if ("WAITING_APPROVAL".equals(waiting.status())) {
                    instance.waitApproval(node.key());
                } else if ("WAITING_TIMER".equals(waiting.status())) {
                    instance.waitTimer(node.key(), waiting.resumeAt());
                } else {
                    state.subflowInstanceId = waiting.subflowInstanceId();
                    instance.waitSubflow(node.key());
                }
                persist(instance, state);
                return;
            }
            persist(instance, state);
        }
    }

    private NodeOutcome executeNode(WorkflowInstance instance, WorkflowNode node, WorkflowRunState state) {
        String type = normalize(node.type());
        if ("APPROVAL".equals(type)) {
            WorkflowNodeRun run = newNodeRun(instance.id(), node, "WAITING_APPROVAL", "{\"status\":\"waiting_approval\"}");
            run.waitApproval();
            instances.saveNodeRun(run);
            return new NodeOutcome(node.key(), "WAITING_APPROVAL", null, null, null, null, null);
        }
        if ("TIMER".equals(type)) {
            int seconds = integer(node.config().get("delaySeconds"), 5);
            Instant resumeAt = Instant.now().plusSeconds(Math.max(1, seconds));
            WorkflowNodeRun run = newNodeRun(instance.id(), node, "WAITING_TIMER",
                    json(Map.of("delaySeconds", seconds, "resumeAt", resumeAt.toString())));
            run.waitTimer();
            instances.saveNodeRun(run);
            return new NodeOutcome(node.key(), "WAITING_TIMER", null, null, resumeAt, null, null);
        }
        if ("SUBFLOW".equals(type)) {
            String childId = string(node.config().get("workflowId"), "");
            if (childId.isBlank()) throw new IllegalStateException("SUBFLOW 节点未配置 workflowId");
            if (childId.equals(instance.workflowId())) throw new IllegalStateException("子流程不能引用自身");
            WorkflowInstance child = start(childId, instance.inputJson());
            WorkflowNodeRun run = newNodeRun(instance.id(), node, "WAITING_SUBFLOW",
                    json(Map.of("subflowInstanceId", child.id(), "workflowId", childId)));
            run.waitSubflow();
            instances.saveNodeRun(run);
            return new NodeOutcome(node.key(), "WAITING_SUBFLOW", null, null, null, child.id(), null);
        }

        WorkflowNodeRun run = newNodeRun(instance.id(), node, "RUNNING", instance.inputJson());
        instances.saveNodeRun(run);
        String lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String output = executeOnce(instance, node, type, state);
                run.succeed(output);
                instances.saveNodeRun(run);
                return new NodeOutcome(node.key(), "SUCCESS", output, null, null, null,
                        compensation(node));
            } catch (Exception e) {
                lastError = e.getMessage() == null ? "节点执行失败" : e.getMessage();
                log.warn("workflow node {} attempt {} failed: {}", node.key(), attempt, lastError);
            }
        }
        run.fail(lastError);
        instances.saveNodeRun(run);
        return new NodeOutcome(node.key(), "FAILED", null, lastError, null, null, null);
    }

    private String executeOnce(WorkflowInstance instance, WorkflowNode node, String type, WorkflowRunState state) throws Exception {
        return switch (type) {
            case "START", "TRIGGER", "END", "JOIN" -> json(Map.of("node", node.title(), "status", "ok"));
            case "KNOWLEDGE" -> {
                String datasetId = string(node.config().get("datasetId"), firstToken(node.meta()));
                if (datasetId.isBlank()) yield json(Map.of("node", node.title(), "status", "skipped"));
                RetrievalResult result = knowledge.retrieve(datasetId, instance.inputJson() == null ? "" : instance.inputJson());
                yield json(Map.of("node", node.title(), "hits", result.hits().size(),
                        "context", result.hits().stream().map(h -> h.text()).limit(3).toList()));
            }
            case "MODEL" -> {
                String system = string(node.config().get("systemPrompt"),
                        "你是流程节点执行器。节点：" + node.title() + "，配置：" + node.meta());
                String user = string(node.config().get("input"), instance.inputJson() == null ? "" : instance.inputJson());
                yield json(Map.of("node", node.title(), "output", chat.complete(system, user)));
            }
            case "TOOL" -> {
                String tool = string(node.config().get("tool"), firstToken(node.meta()));
                if (tool.isBlank()) throw new IllegalStateException("TOOL 节点未配置 tool");
                String input = string(node.config().get("input"), instance.inputJson() == null ? "" : instance.inputJson());
                yield json(Map.of("node", node.title(), "tool", tool, "output", tools.execute(tool, input)));
            }
            case "CONDITION" -> {
                String expression = string(node.config().get("expression"), node.meta());
                boolean value = expressions.evaluate(expression, instance.inputJson(), state.outputs);
                yield json(Map.of("node", node.title(), "expression", expression, "condition", value));
            }
            case "NOTIFY" -> {
                String output = notifications.send(node, instance, expressions.variables(instance.inputJson(), state.outputs));
                yield json(Map.of("node", node.title(), "status", "sent", "messageId", output));
            }
            default -> json(Map.of("node", node.title(), "status", "ok", "meta", node.meta() == null ? "" : node.meta()));
        };
    }

    private void applyCondition(WorkflowRunState state, Graph graph, WorkflowNode node, String output) {
        if (!"CONDITION".equals(normalize(node.type()))) return;
        List<Edge> outgoing = graph.outgoing.getOrDefault(node.key(), List.of());
        boolean labeled = outgoing.stream().anyMatch(e -> e.label() != null);
        if (!labeled) return;
        boolean result;
        try {
            result = mapper.readTree(output).path("condition").asBoolean(false);
        } catch (Exception e) {
            result = false;
        }
        for (Edge edge : outgoing) {
            if (edge.label() != null && !edge.label().equalsIgnoreCase(String.valueOf(result))) {
                state.inactiveEdges.add(edge.key());
            }
        }
    }

    private void succeed(WorkflowInstance instance, Workflow workflow, WorkflowRunState state) {
        instance.succeed(json(Map.of("status", "SUCCEEDED", "outputs", state.outputs)));
        instance.saveState(writeState(state));
        instances.save(instance);
        workflow.recordRun(true);
        workflows.save(workflow);
    }

    private void fail(WorkflowInstance instance, WorkflowRunState state, String error) {
        Workflow workflow = workflows.findById(instance.workflowId()).orElse(null);
        String compensationError = compensate(instance, state);
        String reason = error + (compensationError.isBlank() ? "" : "；补偿异常: " + compensationError);
        instance.fail(reason);
        instance.saveState(writeState(state));
        instances.save(instance);
        if (workflow != null) {
            workflow.recordRun(false);
            workflows.save(workflow);
        }
    }

    private String compensate(WorkflowInstance instance, WorkflowRunState state) {
        List<String> errors = new ArrayList<>();
        for (int i = state.compensations.size() - 1; i >= 0; i--) {
            WorkflowRunState.CompensationRecord record = state.compensations.get(i);
            if (record.tool == null || record.tool.isBlank()) continue;
            WorkflowNodeRun run = new WorkflowNodeRun(
                    "noderun_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                    instance.id(), "compensation:" + record.nodeKey, "COMPENSATION", "补偿 · " + record.nodeKey,
                    "RUNNING", record.inputJson, null, 1, Instant.now(), null, null);
            instances.saveNodeRun(run);
            try {
                String output = tools.execute(record.tool, record.inputJson);
                run.succeed(json(Map.of("compensation", record.tool, "output", output)));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "补偿失败" : e.getMessage();
                run.fail(message);
                errors.add(record.nodeKey + ": " + message);
            }
            instances.saveNodeRun(run);
        }
        state.compensations.clear();
        return String.join("; ", errors);
    }

    private void persist(WorkflowInstance instance, WorkflowRunState state) {
        instance.saveState(writeState(state));
        instances.save(instance);
    }

    private WorkflowRunState loadState(WorkflowInstance instance) {
        if (instance.stateJson() == null || instance.stateJson().isBlank()) return new WorkflowRunState();
        try {
            return mapper.readValue(instance.stateJson(), WorkflowRunState.class);
        } catch (Exception e) {
            return new WorkflowRunState();
        }
    }

    private void hydrate(WorkflowRunState state, String instanceId) {
        for (WorkflowNodeRun run : instances.findNodeRuns(instanceId)) {
            if ("SUCCESS".equals(run.status())) {
                state.completed.add(run.nodeKey());
                if (run.outputJson() != null) state.outputs.putIfAbsent(run.nodeKey(), run.outputJson());
            } else if ("SKIPPED".equals(run.status())) {
                state.skipped.add(run.nodeKey());
            }
        }
    }

    private String writeState(WorkflowRunState state) {
        try {
            return mapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("workflow state serialize failed", e);
        }
    }

    private WorkflowNodeRun newNodeRun(String instanceId, WorkflowNode node, String status, String input) {
        return new WorkflowNodeRun(
                "noderun_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                instanceId, node.key(), node.type(), node.title(), status, input, null, 1,
                Instant.now(), null, null);
    }

    private WorkflowNodeRun waitingRun(String instanceId, String nodeKey, String status) {
        return instances.findNodeRuns(instanceId).stream()
                .filter(r -> nodeKey != null && nodeKey.equals(r.nodeKey()) && status.equals(r.status()))
                .reduce((a, b) -> b)
                .orElse(null);
    }

    private boolean allResolved(Graph graph, WorkflowRunState state) {
        return graph.nodes.keySet().stream().allMatch(k -> state.completed.contains(k) || state.skipped.contains(k));
    }

    private Graph graph(Workflow workflow) {
        Map<String, WorkflowNode> nodes = new LinkedHashMap<>();
        List<WorkflowNode> list = workflow.nodes();
        for (WorkflowNode node : list) nodes.put(node.key(), node);
        Map<String, List<Edge>> outgoing = new LinkedHashMap<>();
        Map<String, List<Edge>> incoming = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            WorkflowNode node = list.get(i);
            List<Edge> edges = new ArrayList<>();
            String type = normalize(node.type());
            String trueNext = string(node.config().get("trueNext"), "");
            String falseNext = string(node.config().get("falseNext"), "");
            if ("CONDITION".equals(type) && (!trueNext.isBlank() || !falseNext.isBlank())) {
                if (!trueNext.isBlank()) edges.add(new Edge(node.key(), trueNext, "true"));
                if (!falseNext.isBlank()) edges.add(new Edge(node.key(), falseNext, "false"));
            } else {
                List<String> targets = targets(node);
                if (targets.isEmpty() && !"END".equals(type) && i + 1 < list.size()) {
                    targets = List.of(list.get(i + 1).key());
                }
                for (String target : targets) {
                    if (nodes.containsKey(target)) edges.add(new Edge(node.key(), target, null));
                }
            }
            outgoing.put(node.key(), edges);
            for (Edge edge : edges) incoming.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge);
        }
        // Nodes with no incoming edge are treated as entry points.
        return new Graph(nodes, outgoing, incoming);
    }

    private List<String> targets(WorkflowNode node) {
        Object next = node.config().get("next");
        if (next instanceof List<?> list) {
            return list.stream().filter(v -> v != null && !String.valueOf(v).isBlank()).map(String::valueOf).toList();
        }
        if (next != null && !String.valueOf(next).isBlank()) return List.of(String.valueOf(next));
        return List.of();
    }

    private String compensation(WorkflowNode node) {
        return string(node.config().get("compensation"), "");
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { return "{}"; }
    }

    private String normalize(String type) {
        return type == null ? "TOOL" : type.toUpperCase();
    }

    private String firstToken(String value) {
        if (value == null) return "";
        String token = value.trim().split("[\\s·]")[0];
        return token == null ? "" : token;
    }

    private String string(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return fallback; }
    }

    private record Edge(String from, String to, String label) {
        String key() { return from + "->" + to; }
    }

    private record Graph(Map<String, WorkflowNode> nodes,
                         Map<String, List<Edge>> outgoing,
                         Map<String, List<Edge>> incoming) {
    }

    private record NodeOutcome(String nodeKey, String status, String output, String error,
                               Instant resumeAt, String subflowInstanceId, String compensation) {
    }
}
