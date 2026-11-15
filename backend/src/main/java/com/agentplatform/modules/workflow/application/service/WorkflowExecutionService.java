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

/**
 * 流程执行引擎。
 *
 * <p>所属层：application 应用层。负责流程实例的启动、按波次（wave）推进节点、节点级重试、
 * 条件分支剪枝、审批/定时器/子流程等待与恢复，以及失败时的逆序补偿。</p>
 *
 * <p>执行模型：</p>
 * <ol>
 *   <li>把流程定义的节点列表构建为有向图（显式 {@code next/trueNext/falseNext} 或按顺序推断默认边）；</li>
 *   <li>每轮收集“所有有效入边来源均已完成”的节点作为就绪集合，并行提交到
 *       {@code workflowExecutor} 执行；</li>
 *   <li>将已完成节点、输出、失效边、补偿栈序列化写入实例的 {@code state_json}；</li>
 *   <li>遇到等待节点时持久化等待态并退出循环，由 {@link WorkflowResumeScheduler}
 *       轮询恢复（审批由用户调用 {@link #approve(String, String)} 恢复）。</li>
 * </ol>
 *
 * <p>关键约束：</p>
 * <ul>
 *   <li>状态必须先落库再异步执行，恢复时从 node_run 回填，保证进程重启后可继续；</li>
 *   <li>同一并行波次最多允许一个等待节点，避免多个等待态争用实例的单一 {@code currentNode}；</li>
 *   <li>节点失败按 {@code app.workflow.max-attempts}（默认 2）重试，重试耗尽后流程失败并触发补偿；</li>
 *   <li>本类未使用显式事务：每次 save 独立提交，执行引擎以“状态 JSON + 节点记录”实现最终一致。</li>
 * </ul>
 */
@Service
public class WorkflowExecutionService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionService.class);
    /** 流程定义仓储端口。 */
    private final WorkflowRepository workflows;
    /** 流程实例与节点运行记录仓储端口。 */
    private final WorkflowInstanceRepository instances;
    /** 知识库应用服务，供 KNOWLEDGE 节点检索。 */
    private final KnowledgeApplicationService knowledge;
    /** 对话补全服务，供 MODEL 节点调用模型。 */
    private final ChatCompletionService chat;
    /** 工具执行服务，供 TOOL 节点与补偿动作调用。 */
    private final ToolExecutionService tools;
    /** 通知服务，供 NOTIFY 节点使用。 */
    private final WorkflowNotificationService notifications;
    /** 表达式服务，供 CONDITION 节点与通知模板变量使用。 */
    private final WorkflowExpressionService expressions;
    /** JSON 序列化器，用于执行状态与节点输出。 */
    private final ObjectMapper mapper;
    /** 流程专用线程池，用于异步启动实例与并行执行节点。 */
    private final TaskExecutor executor;
    /** 单个节点的最大尝试次数（至少为 1）。 */
    private final int maxAttempts;

    /**
     * 构造执行引擎。
     *
     * @param workflows       流程定义仓储端口
     * @param instances       流程实例仓储端口
     * @param knowledge       知识库应用服务
     * @param chat            对话补全服务
     * @param tools           工具执行服务
     * @param notifications   通知服务
     * @param expressions     表达式服务
     * @param mapper          JSON 序列化器
     * @param workflowExecutor 流程专用线程池
     * @param maxAttempts     节点最大尝试次数，配置 {@code app.workflow.max-attempts}，默认 2
     */
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

    /**
     * 启动一次流程实例并异步执行。
     *
     * <p>先持久化实例再提交异步任务，避免执行线程先于实例落库读取不到数据；返回的实例可能尚未开始执行。</p>
     *
     * @param workflowId 流程定义 ID
     * @param input      启动输入 JSON，作为节点默认输入
     * @return 新建的流程实例（状态 RUNNING）
     * @throws IllegalArgumentException 流程定义不存在时抛出
     */
    public WorkflowInstance start(String workflowId, String input) {
        Workflow workflow = workflows.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("流程不存在: " + workflowId));
        WorkflowInstance instance = new WorkflowInstance(
                "wfrun_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                workflow.id(), workflow.name(), "RUNNING", "manual", input, null, null,
                Instant.now(), null, null, null, null);
        // 先落库再异步执行，恢复/调度线程随时可以读取到该实例。
        instances.save(instance);
        executor.execute(() -> execute(instance.id()));
        return instance;
    }

    /**
     * 审批通过当前等待中的审批节点，并异步恢复流程。
     *
     * <p>处理步骤：校验实例处于 WAITING_APPROVAL → 将对应等待记录置为成功 →
     * 把节点完成状态与审批结果写入执行游标 → 实例回到 RUNNING → 重新提交执行。</p>
     *
     * @param instanceId 流程实例 ID
     * @param comment    审批意见，可为空；为空时输出仅包含 {@code approved=true}
     * @return 恢复后的流程实例（状态 RUNNING，异步继续执行）
     * @throws IllegalArgumentException 实例不存在时抛出
     * @throws IllegalStateException    实例不在待审批状态时抛出
     */
    public WorkflowInstance approve(String instanceId, String comment) {
        WorkflowInstance instance = instances.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("实例不存在: " + instanceId));
        if (!"WAITING_APPROVAL".equals(instance.status())) {
            throw new IllegalStateException("当前实例不在待审批状态");
        }
        WorkflowRunState state = loadState(instance);
        String nodeKey = instance.currentNode();
        WorkflowNodeRun run = waitingRun(instanceId, nodeKey, "WAITING_APPROVAL");
        // 等待记录可能因异常缺失，存在时才更新，避免审批因历史数据不完整而失败。
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

    /**
     * 取消流程实例。
     *
     * <p>仅修改实例状态，不中断已在执行线程中的节点；执行线程下一轮检查到状态非 RUNNING 会退出。</p>
     *
     * @param instanceId 流程实例 ID
     * @return 取消后的流程实例
     * @throws java.util.NoSuchElementException 实例不存在时抛出
     */
    public WorkflowInstance cancel(String instanceId) {
        WorkflowInstance instance = instances.findById(instanceId).orElseThrow();
        instance.cancel();
        instances.save(instance);
        return instance;
    }

    /**
     * 查询全部流程实例。
     *
     * @return 流程实例列表
     */
    public List<WorkflowInstance> list() { return instances.findAll(); }

    /**
     * 按 ID 查询流程实例。
     *
     * @param id 流程实例 ID
     * @return 流程实例
     * @throws IllegalArgumentException 实例不存在时抛出
     */
    public WorkflowInstance get(String id) {
        return instances.findById(id).orElseThrow(() -> new IllegalArgumentException("实例不存在: " + id));
    }

    /**
     * 查询实例的全部节点运行记录。
     *
     * @param id 流程实例 ID
     * @return 节点运行记录列表（按开始时间升序）
     */
    public List<WorkflowNodeRun> nodeRuns(String id) { return instances.findNodeRuns(id); }

    /**
     * 恢复到期的定时器节点。
     *
     * <p>Called by the scheduler. Resumes persisted timers whose deadline has passed.</p>
     *
     * <p>扫描全部实例（当前仓储无按状态过滤能力），仅处理 WAITING_TIMER 且
     * {@code resumeAt <= now} 的实例；将等待记录置为成功、写入恢复输出后重新提交执行。</p>
     */
    public void resumeTimers() {
        Instant now = Instant.now();
        for (WorkflowInstance instance : instances.findAll()) {
            if (!"WAITING_TIMER".equals(instance.status())) continue;
            // 未到期的定时器保持等待，由下一轮扫描处理。
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
     * 恢复等待中的子流程节点。
     *
     * <p>Called by the scheduler. Resumes a parent once its persisted subflow finishes.</p>
     *
     * <p>根据子实例状态决定父流程走向：子实例不存在或 FAILED/CANCELLED 时父流程失败；
     * SUCCEEDED 时把子实例输出作为节点输出并继续；其余状态（运行中/等待中）保持等待。</p>
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
            // 子流程失败或取消视为整个子流程节点失败，父流程进入失败并触发补偿。
            if (Set.of("FAILED", "CANCELLED").contains(child.status())) {
                fail(parent, state, "子流程失败: " + child.status());
                continue;
            }
            // 子流程尚未结束，等待下一轮扫描。
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

    /**
     * 单次执行入口：载入实例、流程定义与执行游标，回填已完成节点后推进执行循环。
     *
     * <p>状态非 RUNNING 时直接返回，起到幂等保护作用（重复调度、取消或已完成实例不会重跑）。
     * 任何未捕获异常都会使流程失败并触发补偿。</p>
     *
     * @param instanceId 流程实例 ID
     */
    private void execute(String instanceId) {
        WorkflowInstance instance = instances.findById(instanceId).orElse(null);
        // 双重检查状态：实例可能在提交任务后被取消，或已被其他线程推进。
        if (instance == null || !"RUNNING".equals(instance.status())) return;
        Workflow workflow = workflows.findById(instance.workflowId()).orElse(null);
        if (workflow == null) {
            instance.fail("流程定义不存在");
            instances.save(instance);
            return;
        }
        Graph graph = graph(workflow);
        WorkflowRunState state = loadState(instance);
        // 从节点运行记录回填状态，覆盖“状态 JSON 未及时保存”或进程重启的场景。
        hydrate(state, instanceId);
        try {
            runLoop(instance, workflow, graph, state);
        } catch (Exception e) {
            log.error("workflow execution failed: {}", instanceId, e);
            fail(instance, state, e.getMessage() == null ? "流程执行失败" : e.getMessage());
        }
    }

    /**
     * 按波次推进流程，直到结束、失败或进入等待态。
     *
     * <p>每轮先计算就绪/跳过节点，再并行执行就绪节点；条件节点执行后会剪枝未选中的出边，
     * 使未选路径上的节点在后续轮次被判定为不可达而跳过。</p>
     */
    private void runLoop(WorkflowInstance instance, Workflow workflow, Graph graph, WorkflowRunState state) {
        while (true) {
            // 取消或状态被外部改变时立即停止推进。
            if (!"RUNNING".equals(instance.status())) return;

            List<WorkflowNode> ready = new ArrayList<>();
            List<WorkflowNode> skipped = new ArrayList<>();
            for (WorkflowNode node : graph.nodes.values()) {
                if (state.completed.contains(node.key()) || state.skipped.contains(node.key())) continue;
                List<Edge> incoming = graph.incoming.getOrDefault(node.key(), List.of());
                // 没有入边的节点视为入口节点，直接进入就绪集合。
                if (incoming.isEmpty()) {
                    ready.add(node);
                    continue;
                }
                boolean pending = false;
                int active = 0;
                for (Edge edge : incoming) {
                    // 条件分支未选中的边不参与就绪判断。
                    if (state.inactiveEdges.contains(edge.key())) continue;
                    // 来源节点已被跳过时，这条边视为不存在。
                    if (state.skipped.contains(edge.from())) continue;
                    if (state.completed.contains(edge.from())) active++;
                    else pending = true;
                }
                // 仍有来源未完成时继续等待；所有有效来源完成但无一完成时，该节点不可达。
                if (pending) continue;
                if (active == 0) skipped.add(node);
                else ready.add(node);
            }

            if (!skipped.isEmpty()) {
                // 先把本轮所有不可达节点标记为 SKIPPED 并持久化，再重新计算就绪集合。
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
                    // 既无就绪节点又存在未解析节点，说明图中有环或引用了不存在的节点。
                    fail(instance, state, "流程存在无法到达的节点或环，无法继续执行");
                }
                return;
            }

            String current = ready.stream().map(WorkflowNode::key).reduce((a, b) -> a + "," + b).orElse("");
            // currentNode 仅用于展示/等待定位，长度截断避免超出数据库列长度。
            instance.running(current.length() > 64 ? current.substring(0, 64) : current);
            instances.save(instance);

            List<CompletableFuture<NodeOutcome>> futures = new ArrayList<>();
            for (WorkflowNode node : ready) {
                // 同一波次节点并行执行，共享本波次开始时的执行状态快照（只读使用）。
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
                    // 实例只有单一 currentNode 与 resumeAt，因此同一波次只允许一个等待节点。
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
                    // 注册补偿动作，流程后续失败时按逆序调用。
                    state.compensations.add(new WorkflowRunState.CompensationRecord(
                            node.key(), outcome.compensation(), outcome.output()));
                }
                applyCondition(state, graph, node, outcome.output());
            }
            if (waiting != null) {
                // 持久化等待态后退出循环；恢复由审批接口或恢复调度器触发。
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

    /**
     * 执行单个节点，返回节点结果。
     *
     * <p>APPROVAL/TIMER/SUBFLOW 是等待型节点：只创建等待记录并返回等待结果，不执行业务逻辑；
     * 其余节点进入最多 {@code maxAttempts} 次的重试循环。子流程节点会立即启动子实例，
     * 但父节点保持 WAITING_SUBFLOW 直到调度器发现子实例结束。</p>
     */
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
            // 至少延迟 1 秒，避免 0/负数导致立即恢复从而空转。
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
            // 禁止自引用，避免无限递归启动自身。
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
                // 节点的 compensation 配置随成功结果返回，由上层决定是否入补偿栈。
                return new NodeOutcome(node.key(), "SUCCESS", output, null, null, null,
                        compensation(node));
            } catch (Exception e) {
                // 记录最后一次错误，重试耗尽后作为节点失败原因。
                lastError = e.getMessage() == null ? "节点执行失败" : e.getMessage();
                log.warn("workflow node {} attempt {} failed: {}", node.key(), attempt, lastError);
            }
        }
        run.fail(lastError);
        instances.saveNodeRun(run);
        return new NodeOutcome(node.key(), "FAILED", null, lastError, null, null, null);
    }

    /**
     * 按节点类型执行一次具体业务逻辑，返回输出 JSON。
     *
     * <p>节点类型与实现：START/TRIGGER/END/JOIN 直接成功；KNOWLEDGE 检索知识库；
     * MODEL 调用大模型；TOOL 调用工具；CONDITION 求值 SpEL；NOTIFY 发送通知；
     * 未知类型按成功处理并回显 meta，保持前向兼容。</p>
     *
     * @param instance 所属流程实例，提供启动输入
     * @param node     节点定义
     * @param type     归一化后的节点类型（大写）
     * @param state    执行游标（提供已完成节点输出给表达式）
     * @return 节点输出 JSON
     * @throws Exception 知识库、模型、工具、通知等下游调用失败时抛出，由重试逻辑捕获
     */
    private String executeOnce(WorkflowInstance instance, WorkflowNode node, String type, WorkflowRunState state) throws Exception {
        return switch (type) {
            case "START", "TRIGGER", "END", "JOIN" -> json(Map.of("node", node.title(), "status", "ok"));
            case "KNOWLEDGE" -> {
                // datasetId 缺省时回退到 meta 的第一个词（如“kb-refund · Top 4”取 kb-refund）。
                String datasetId = string(node.config().get("datasetId"), firstToken(node.meta()));
                if (datasetId.isBlank()) yield json(Map.of("node", node.title(), "status", "skipped"));
                RetrievalResult result = knowledge.retrieve(datasetId, instance.inputJson() == null ? "" : instance.inputJson());
                yield json(Map.of("node", node.title(), "hits", result.hits().size(),
                        "context", result.hits().stream().map(h -> h.text()).limit(3).toList()));
            }
            case "MODEL" -> {
                // 未配置系统提示词时，用节点标题与 meta 组成默认执行器提示。
                String system = string(node.config().get("systemPrompt"),
                        "你是流程节点执行器。节点：" + node.title() + "，配置：" + node.meta());
                String user = string(node.config().get("input"), instance.inputJson() == null ? "" : instance.inputJson());
                yield json(Map.of("node", node.title(), "output", chat.complete(system, user)));
            }
            case "TOOL" -> {
                // 工具名缺省时回退到 meta 的第一个词（如“refund.apply”）。
                String tool = string(node.config().get("tool"), firstToken(node.meta()));
                if (tool.isBlank()) throw new IllegalStateException("TOOL 节点未配置 tool");
                String input = string(node.config().get("input"), instance.inputJson() == null ? "" : instance.inputJson());
                yield json(Map.of("node", node.title(), "tool", tool, "output", tools.execute(tool, input)));
            }
            case "CONDITION" -> {
                // 表达式缺省时回退到 meta（种子数据中 meta 即为表达式文本）。
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

    /**
     * 条件节点执行成功后，剪枝未被选中的出边。
     *
     * <p>只有带 {@code true/false} 标签的出边参与剪枝；没有标签的边（普通 next）不受影响。
     * 输出解析失败时按 false 分支处理，与表达式服务的容错策略保持一致。</p>
     */
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
            // 与条件结果不一致的分支边加入失效集合，其目标节点将因无有效入边而被跳过。
            if (edge.label() != null && !edge.label().equalsIgnoreCase(String.valueOf(result))) {
                state.inactiveEdges.add(edge.key());
            }
        }
    }

    /**
     * 标记流程成功，保存最终状态，并更新流程定义的运行统计。
     */
    private void succeed(WorkflowInstance instance, Workflow workflow, WorkflowRunState state) {
        instance.succeed(json(Map.of("status", "SUCCEEDED", "outputs", state.outputs)));
        instance.saveState(writeState(state));
        instances.save(instance);
        workflow.recordRun(true);
        workflows.save(workflow);
    }

    /**
     * 标记流程失败：先逆序执行补偿，再写入失败原因与最终状态，并更新运行统计。
     *
     * <p>补偿异常不会覆盖原始失败原因，而是追加在错误信息中。</p>
     */
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

    /**
     * 逆序执行补偿栈中的工具调用，并为每次补偿写入一条独立的节点运行记录。
     *
     * <p>逆序是 Saga 补偿的语义要求：后完成的副作用先回滚。单个补偿失败不中断其余补偿，
     * 错误汇总后返回；无论成功与否，补偿栈最终清空。</p>
     *
     * @return 补偿错误汇总（以分号分隔），全部成功时为空字符串
     */
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

    /**
     * 持久化执行游标与实例状态（先写 stateJson 再保存实例）。
     */
    private void persist(WorkflowInstance instance, WorkflowRunState state) {
        instance.saveState(writeState(state));
        instances.save(instance);
    }

    /**
     * 从实例的 stateJson 反序列化执行游标；缺失或损坏时返回全新状态。
     *
     * <p>损坏时降级为空状态而非抛异常，后续可由 node_run 回填已完成节点。</p>
     */
    private WorkflowRunState loadState(WorkflowInstance instance) {
        if (instance.stateJson() == null || instance.stateJson().isBlank()) return new WorkflowRunState();
        try {
            return mapper.readValue(instance.stateJson(), WorkflowRunState.class);
        } catch (Exception e) {
            return new WorkflowRunState();
        }
    }

    /**
     * 用已持久化的节点运行记录回填执行状态，作为状态 JSON 的兜底。
     *
     * <p>SUCCESS 记录补入 completed 与 outputs（putIfAbsent，状态 JSON 优先），
     * SKIPPED 记录补入 skipped。等待型记录不在此处理，由对应的恢复流程负责。</p>
     */
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

    /**
     * 序列化执行游标；失败视为不可恢复的状态错误，直接抛出终止执行。
     */
    private String writeState(WorkflowRunState state) {
        try {
            return mapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("workflow state serialize failed", e);
        }
    }

    /**
     * 创建一条节点运行记录（ID 随机生成，尝试次数固定为 1）。
     */
    private WorkflowNodeRun newNodeRun(String instanceId, WorkflowNode node, String status, String input) {
        return new WorkflowNodeRun(
                "noderun_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                instanceId, node.key(), node.type(), node.title(), status, input, null, 1,
                Instant.now(), null, null);
    }

    /**
     * 查找实例下指定节点、指定状态的最新一条等待记录（同一节点可能有多次运行）。
     *
     * @return 最新等待记录，不存在时返回 {@code null}
     */
    private WorkflowNodeRun waitingRun(String instanceId, String nodeKey, String status) {
        return instances.findNodeRuns(instanceId).stream()
                .filter(r -> nodeKey != null && nodeKey.equals(r.nodeKey()) && status.equals(r.status()))
                .reduce((a, b) -> b)
                .orElse(null);
    }

    /**
     * 判断图中所有节点是否均已解析（完成或跳过）。
     */
    private boolean allResolved(Graph graph, WorkflowRunState state) {
        return graph.nodes.keySet().stream().allMatch(k -> state.completed.contains(k) || state.skipped.contains(k));
    }

    /**
     * 把有序节点列表构建为执行图。
     *
     * <p>边的来源优先级：</p>
     * <ol>
     *   <li>CONDITION 节点使用 {@code trueNext/falseNext} 生成带标签的边；</li>
     *   <li>其他节点使用 {@code config.next}（单个值或列表）；</li>
     *   <li>未配置 next 且非 END 节点时，按节点列表顺序连接到下一个节点；</li>
     *   <li>指向不存在节点的目标被忽略，防止脏配置中断执行。</li>
     * </ol>
     *
     * <p>没有入边的节点在后续调度中作为入口节点处理。</p>
     */
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
                // 顺序兜底：未配置 next 时连接列表中的下一个节点，兼容早期的线性流程定义。
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

    /**
     * 读取节点的 {@code next} 配置，支持字符串或字符串列表。
     */
    private List<String> targets(WorkflowNode node) {
        Object next = node.config().get("next");
        if (next instanceof List<?> list) {
            return list.stream().filter(v -> v != null && !String.valueOf(v).isBlank()).map(String::valueOf).toList();
        }
        if (next != null && !String.valueOf(next).isBlank()) return List.of(String.valueOf(next));
        return List.of();
    }

    /** 读取节点配置的补偿工具名，未配置返回空字符串。 */
    private String compensation(WorkflowNode node) {
        return string(node.config().get("compensation"), "");
    }

    /** 序列化为 JSON；失败时返回空对象，避免影响主执行流程。 */
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { return "{}"; }
    }

    /** 节点类型归一：空值按 TOOL 处理，其余转大写。 */
    private String normalize(String type) {
        return type == null ? "TOOL" : type.toUpperCase();
    }

    /** 取字符串的第一个词（以空白或中点“·”分隔），用于从 meta 摘要中提取工具/数据集名。 */
    private String firstToken(String value) {
        if (value == null) return "";
        String token = value.trim().split("[\\s·]")[0];
        return token == null ? "" : token;
    }

    /** 取字符串值，空白时返回回退值。 */
    private String string(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    /** 取整数值，非数字或解析失败时返回回退值。 */
    private int integer(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return fallback; }
    }

    /**
     * 有向边：{@code label} 为 null 表示普通边，{@code true/false} 表示条件分支边。
     *
     * @param from  来源节点 key
     * @param to    目标节点 key
     * @param label 分支标签
     */
    private record Edge(String from, String to, String label) {
        /** 边的唯一键，用于记录失效边。 */
        String key() { return from + "->" + to; }
    }

    /**
     * 执行图：节点索引与出/入边索引。
     *
     * @param nodes    节点 key → 节点定义
     * @param outgoing 节点 key → 出边列表
     * @param incoming 节点 key → 入边列表
     */
    private record Graph(Map<String, WorkflowNode> nodes,
                         Map<String, List<Edge>> outgoing,
                         Map<String, List<Edge>> incoming) {
    }

    /**
     * 节点执行结果。
     *
     * @param nodeKey           节点 key
     * @param status            SUCCESS/FAILED/WAITING_APPROVAL/WAITING_TIMER/WAITING_SUBFLOW
     * @param output            成功时的输出 JSON
     * @param error             失败原因
     * @param resumeAt          定时器到期时间
     * @param subflowInstanceId 子流程实例 ID
     * @param compensation      补偿工具名
     */
    private record NodeOutcome(String nodeKey, String status, String output, String error,
                               Instant resumeAt, String subflowInstanceId, String compensation) {
    }
}
