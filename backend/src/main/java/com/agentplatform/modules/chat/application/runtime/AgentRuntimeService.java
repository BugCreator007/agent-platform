package com.agentplatform.modules.chat.application.runtime;

import com.agentplatform.modules.agent.application.query.AgentSummary;
import com.agentplatform.modules.agent.application.service.AgentApplicationService;
import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.model.MessageCitation;
import com.agentplatform.modules.chat.domain.model.SessionMessage;
import com.agentplatform.modules.chat.domain.model.TraceStep;
import com.agentplatform.modules.chat.domain.repository.SessionMessageRepository;
import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import com.agentplatform.modules.chat.infrastructure.ai.AiMessage;
import com.agentplatform.modules.chat.infrastructure.ai.ChatChunk;
import com.agentplatform.modules.chat.infrastructure.ai.ModelGateway;
import com.agentplatform.modules.chat.infrastructure.ai.ToolCall;
import com.agentplatform.modules.tool.application.query.McpCallOutcome;
import com.agentplatform.modules.tool.application.query.McpToolBinding;
import com.agentplatform.modules.tool.application.service.McpRuntimeService;
import com.agentplatform.modules.knowledge.application.query.RetrievalHit;
import com.agentplatform.modules.knowledge.application.query.RetrievalResult;
import com.agentplatform.modules.knowledge.application.service.KnowledgeApplicationService;
import com.agentplatform.modules.tool.application.service.ToolExecutionService;
import com.agentplatform.modules.model.application.query.ModelRoute;
import io.micrometer.core.instrument.MeterRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.time.Duration;

/**
 * 会话运行时应用服务：编排一次流式对话的完整生命周期（application 层）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>解析智能体配置（模型路由、知识库、工具）与模型可用性；</li>
 *   <li>复用或创建会话，截取最近历史消息作为模型上下文；</li>
 *   <li>执行 RAG 检索与本地工具预取，拼装系统提示词；</li>
 *   <li>通过 SSE 推送 meta / delta / tool / tool-result / done / error 事件；</li>
 *   <li>流结束后持久化助手消息、会话汇总与执行 trace，并记录 Micrometer 指标。</li>
 * </ul>
 *
 * <p>关键约束：SSE 连接超时 180 秒；单次模型 HTTP 请求超时 120 秒；
 * 历史消息只取最近 12 条；MCP 工具调用循环最多 4 轮。
 * 知识库检索或本地工具预取失败时降级为无上下文，不阻断主流程。</p>
 */
@Service
public class AgentRuntimeService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final AgentApplicationService agents;
    private final SessionRepository sessions;
    private final SessionMessageRepository messages;
    private final ModelGateway model;
    private final KnowledgeApplicationService knowledge;
    private final ToolExecutionService tools;
    private final McpRuntimeService mcp;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;

    public AgentRuntimeService(AgentApplicationService agents, SessionRepository sessions,
                               SessionMessageRepository messages, ModelGateway model,
                               KnowledgeApplicationService knowledge,
                               ToolExecutionService tools, McpRuntimeService mcp,
                               ObjectMapper objectMapper, MeterRegistry meters) {
        this.agents = agents;
        this.sessions = sessions;
        this.messages = messages;
        this.model = model;
        this.knowledge = knowledge;
        this.tools = tools;
        this.mcp = mcp;
        this.objectMapper = objectMapper;
        this.meters = meters;
    }

    /**
     * 启动一次流式对话并返回 SSE 发射器。
     *
     * <p>方法同步完成会话初始化、RAG 检索与提示词拼装，随后按智能体是否绑定 MCP 工具分流：
     * 有工具时异步执行工具调用循环；无工具时直接订阅模型文本流。</p>
     *
     * @param request 运行时请求，包含智能体 ID、用户消息与可选会话 ID；
     *                会话 ID 为空时自动生成 {@code s_xxxxxxxx}
     * @return 已注册 meta 事件与完成/超时/错误回调的 SSE 发射器
     * @throws RuntimeException 智能体不存在等业务异常由 {@code AgentApplicationService} 抛出
     */
    public SseEmitter stream(RuntimeChatRequest request) {
        AgentSummary agent = agents.get(request.agentId());
        // 未指定会话 ID 时生成新会话，保证前端可以持续续聊同一会话。
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? "s_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) : request.sessionId();
        // SSE 连接最长保持 180 秒；超时回调会释放模型订阅，防止后台流继续占用资源。
        SseEmitter emitter = new SseEmitter(180_000L);
        long start = System.currentTimeMillis();
        meters.counter("agent_runtime_requests_total", "agent", agent.name()).increment();
        StringBuilder answer = new StringBuilder();
        AtomicLong tokens = new AtomicLong();
        ChatSession existingSession = sessions.findById(sessionId).orElse(null);
        // 新会话先落一条 running 记录，前端刷新时即可看到进行中的会话。
        if (existingSession == null) {
            sessions.save(new ChatSession(sessionId, agent.id(), agent.name(), "running", 0, 0, 0,
                    0, LocalTime.now().format(TIME), request.message(), List.of()));
        }
        List<AiMessage> history = new ArrayList<>();
        List<SessionMessage> previous = messages.findBySession(sessionId);
        // 只保留最近 12 条历史，控制上下文长度与模型费用，避免长会话无限膨胀。
        int from = Math.max(0, previous.size() - 12);
        for (int i = from; i < previous.size(); i++) {
            SessionMessage m = previous.get(i);
            // 历史中只回放纯文本对话；工具消息不还原，防止旧工具结果干扰本轮推理。
            if ("user".equals(m.role()) || "assistant".equals(m.role())) {
                history.add(new AiMessage(m.role(), m.content()));
            }
        }
        history.add(new AiMessage("user", request.message()));
        // 用户消息先于模型调用落库，即使后续模型失败也能保留提问记录。
        messages.save(new SessionMessage("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                sessionId, "user", request.message(), 0, null, 0, java.time.Instant.now()));

        List<RetrievalHit> contexts = new ArrayList<>();
        List<MessageCitation> citations = new ArrayList<>();
        Set<String> citationKb = new HashSet<>(agent.citationKb() == null ? List.of() : agent.citationKb());
        Set<String> citedDocumentIds = new HashSet<>();
        for (String kbId : agent.kb()) {
            try {
                RetrievalResult result = knowledge.retrieve(kbId, request.message());
                contexts.addAll(result.hits());
                // 只对智能体开启了“返回文档链接”的知识库收集引用，并按文档去重。
                if (citationKb.contains(kbId)) {
                    for (RetrievalHit hit : result.hits()) {
                        if (hit.documentId() == null || hit.documentId().isBlank()
                                || hit.url() == null || hit.url().isBlank()) continue;
                        if (citedDocumentIds.add(hit.documentId())) {
                            citations.add(new MessageCitation(hit.documentId(), hit.source(),
                                    hit.url(), hit.sourceType()));
                        }
                    }
                }
            } catch (RuntimeException ignored) { } // 单个知识库不可用时降级跳过，不阻断对话。
        }
        final List<MessageCitation> finalCitations = List.copyOf(citations);
        String toolContext = "";
        // 本地工具按关键词做轻量预取；这是演示用启发式，失败时同样静默降级。
        if (agent.tools().contains("order.query")
                && (request.message().contains("订单") || request.message().contains("退款"))) {
            try { toolContext = tools.execute("order.query", request.message()); } catch (RuntimeException ignored) { }
        }
        String systemPrompt = buildSystemPrompt(agent, contexts, toolContext);
        List<ModelRoute> routes = model.routes(agent.providerId(), agent.modelId());
        // 路由列表首项为主路由；没有可用路由时退化为智能体自身配置的模型名。
        String resolvedModel = routes.isEmpty()
                ? (agent.modelId() == null || agent.modelId().isBlank() ? agent.model() : agent.modelId())
                : routes.get(0).modelName();
        String resolvedProvider = model.providerLabel(routes);
        String routeLabel = model.routeLabel(routes);
        // 无可用密钥时网关会返回本地演示文本，这里标记给前端展示 mock 状态。
        boolean mock = !model.configured(routes);
        List<McpToolBinding> mcpBindings;
        try { mcpBindings = mcp.bindings(agent.tools()); } catch (RuntimeException e) { mcpBindings = List.of(); }
        if (!mcpBindings.isEmpty()) {
            // MCP 分支：先推 meta，再异步进入工具调用循环，避免阻塞 Servlet 线程。
            try {
                emitter.send(SseEmitter.event().name("meta").data(
                        meta(sessionId, agent, routes, resolvedModel, contexts.size(), mcpBindings.size())));
            } catch (IOException e) {
                emitter.completeWithError(e);
                return emitter;
            }
            final List<McpToolBinding> bindings = List.copyOf(mcpBindings);
            CompletableFuture.runAsync(() -> runMcpLoop(emitter, agent, request, sessionId, start,
                    contexts.size(), systemPrompt, resolvedModel, resolvedProvider, routeLabel, history, bindings,
                    routes, finalCitations));
            return emitter;
        }
        // 无工具分支：订阅模型文本流，逐片段转发为 SSE delta。
        Disposable disposable = model.stream(routes, systemPrompt, history).subscribe(
                delta -> {
                    answer.append(delta);
                    // 粗略 token 估算：按字符数折半，仅用于展示与成本趋势，不作为计费依据。
                    tokens.addAndGet(Math.max(1, delta.length() / 2));
                    try { emitter.send(SseEmitter.event().name("delta").data(delta, MediaType.TEXT_PLAIN)); }
                    catch (IOException e) { emitter.completeWithError(e); }
                },
                error -> {
                    meters.counter("agent_runtime_errors_total", "agent", agent.name()).increment();
                    try {
                        emitter.send(SseEmitter.event().name("error").data(Map.of("message",
                                error.getMessage() == null ? "模型调用失败" : error.getMessage())));
                    } catch (IOException ignored) { } // 客户端可能已断开，忽略二次发送失败。
                    emitter.completeWithError(error);
                },
                () -> {
                    long elapsed = System.currentTimeMillis() - start;
                    int newTokens = (int) Math.max(1, tokens.get());
                    ChatSession existing = sessions.findById(sessionId).orElse(null);
                    // 轮次与 token 采用“读取-累加-整体覆盖”的方式更新会话汇总。
                    int turns = existing == null ? 1 : existing.turns() + 1;
                    int totalTokens = (existing == null ? 0 : existing.tokens()) + newTokens;
                    String topic = existing == null || existing.topic() == null || existing.topic().isBlank()
                            ? request.message() : existing.topic();
                    messages.save(new SessionMessage("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                            sessionId, "assistant", answer.toString(), newTokens, resolvedModel, contexts.size(),
                            java.time.Instant.now(), finalCitations));
                    ChatSession session = new ChatSession(sessionId, agent.id(), agent.name(), "success",
                            turns, 0, totalTokens, elapsed, LocalTime.now().format(TIME), topic,
                            List.of(new TraceStep("model", "模型流式生成", elapsed,
                                    newTokens, "ok", "路由：" + routeLabel + " · 上下文 " + contexts.size() + " 条")));
                    sessions.save(session);
                    meters.timer("agent_runtime_duration", "agent", agent.name())
                            .record(elapsed, java.util.concurrent.TimeUnit.MILLISECONDS);
                    try {
                        Map<String, Object> done = meta(sessionId, agent, routes, resolvedModel, contexts.size(), 0);
                        done.put("tokens", session.tokens());
                        done.put("ms", elapsed);
                        done.put("mock", mock);
                        done.put("citations", finalCitations);
                        emitter.send(SseEmitter.event().name("done").data(done));
                        emitter.complete();
                    } catch (IOException e) { emitter.completeWithError(e); }
                });
        // 客户端完成或超时后都释放上游模型订阅，防止连接泄漏与无谓的 token 消耗。
        emitter.onCompletion(disposable::dispose);
        emitter.onTimeout(() -> { disposable.dispose(); emitter.complete(); });
        try {
            emitter.send(SseEmitter.event().name("meta").data(
                    meta(sessionId, agent, routes, resolvedModel, contexts.size(), 0)));
        } catch (IOException e) { emitter.completeWithError(e); }
        return emitter;
    }

    /**
     * MCP 工具调用循环：反复请求模型，直到模型不再发起工具调用或达到最大轮次。
     *
     * <p>每轮将模型文本增量实时推送为 delta；模型返回工具调用时，按工具映射执行 MCP 调用，
     * 推送 tool / tool-result 事件，并把结果以 tool 消息回填到对话后继续下一轮。
     * 循环结束后统一持久化助手消息与会话汇总，并推送 done。</p>
     *
     * @param emitter          SSE 发射器
     * @param agent            当前智能体
     * @param request          原始对话请求
     * @param sessionId        会话 ID
     * @param start            本次请求开始时间戳（毫秒）
     * @param contextCount     RAG 注入的上下文条数
     * @param systemPrompt     已拼装的系统提示词
     * @param resolvedModel    解析后的模型名
     * @param resolvedProvider 解析后的提供方名称
     * @param routeLabel       路由展示标签
     * @param history          会话历史消息（含本轮用户消息）
     * @param bindings         智能体可用的 MCP 工具映射
     * @param routes           模型路由候选列表
     */
    private void runMcpLoop(SseEmitter emitter, AgentSummary agent, RuntimeChatRequest request,
                            String sessionId, long start, int contextCount, String systemPrompt,
                            String resolvedModel, String resolvedProvider, String routeLabel,
                            List<AiMessage> history, List<McpToolBinding> bindings, List<ModelRoute> routes,
                            List<MessageCitation> citations) {
        StringBuilder answer = new StringBuilder();
        AtomicLong tokens = new AtomicLong();
        List<AiMessage> chat = new ArrayList<>(history);
        // 将 MCP 工具映射转换为 OpenAI function calling 格式的工具定义。
        List<Map<String, Object>> toolDefinitions = bindings.stream().map(this::toolDefinition).toList();
        try {
            // 最多 4 轮，防止模型与工具互相触发形成无限循环。
            for (int round = 0; round < 4; round++) {
                StringBuilder roundText = new StringBuilder();
                // block 等待本轮完整响应；120 秒与底层 HTTP 超时保持一致。
                List<ChatChunk> chunks = model.streamWithTools(routes, systemPrompt, chat, toolDefinitions)
                        .doOnNext(chunk -> {
                            if (chunk.content() != null && !chunk.content().isEmpty()) {
                                roundText.append(chunk.content());
                                answer.append(chunk.content());
                                // 与同步分支一致的粗略 token 估算。
                                tokens.addAndGet(Math.max(1, chunk.content().length() / 2));
                                try {
                                    emitter.send(SseEmitter.event().name("delta")
                                            .data(chunk.content(), MediaType.TEXT_PLAIN));
                                } catch (IOException e) { throw new RuntimeException(e); }
                            }
                        })
                        .collectList().block(Duration.ofSeconds(120));
                Map<Integer, ToolCall> calls = accumulateToolCalls(chunks == null ? List.of() : chunks);
                // 模型没有请求工具，说明已给出最终回答，结束循环。
                if (calls.isEmpty()) break;
                chat.add(new AiMessage("assistant", roundText.toString(), new ArrayList<>(calls.values()), null));
                for (ToolCall call : calls.values()) {
                    // 模型侧工具名可能被改写（如加前缀），需映射回真实 MCP 工具。
                    McpToolBinding binding = bindings.stream()
                            .filter(b -> b.modelName().equals(call.name())).findFirst().orElse(null);
                    if (binding == null) {
                        chat.add(new AiMessage("tool", "未找到工具映射: " + call.name(), List.of(), call.id()));
                        continue;
                    }
                    Map<String, Object> arguments = parseArguments(call.arguments());
                    // 先通知前端即将调用工具，便于展示调用过程。
                    try {
                        emitter.send(SseEmitter.event().name("tool").data(Map.of(
                                "name", binding.name(), "arguments", arguments)));
                    } catch (IOException ignored) { } // 推送失败不阻断工具执行。
                    McpCallOutcome outcome = mcp.execute(binding.key(), arguments);
                    chat.add(new AiMessage("tool", outcome.text() == null ? "" : outcome.text(), List.of(), call.id()));
                    try {
                        emitter.send(SseEmitter.event().name("tool-result").data(Map.of(
                                "name", binding.name(), "error", outcome.error(),
                                "text", outcome.text() == null ? "" : outcome.text())));
                    } catch (IOException ignored) { }
                }
            }
            int newTokens = (int) Math.max(1, tokens.get());
            saveCompletedSession(agent, request, sessionId, start, contextCount, resolvedModel, resolvedProvider,
                    routeLabel, answer.toString(), newTokens, "MCP 工具链 · " + bindings.size() + " 个工具", citations);
            try {
                Map<String, Object> done = meta(sessionId, agent, routes, resolvedModel, contextCount, bindings.size());
                done.put("tokens", newTokens);
                done.put("ms", System.currentTimeMillis() - start);
                done.put("mock", !model.configured(routes));
                done.put("citations", citations);
                emitter.send(SseEmitter.event().name("done").data(done));
                emitter.complete();
            } catch (IOException e) { emitter.completeWithError(e); }
        } catch (Exception e) {
            meters.counter("agent_runtime_errors_total", "agent", agent.name()).increment();
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of(
                        "message", e.getMessage() == null ? "MCP 工具调用失败" : e.getMessage())));
            } catch (IOException ignored) { }
            emitter.completeWithError(e);
        }
    }

    /**
     * 聚合流式片段中的工具调用：按 index 合并 ID、名称与参数 JSON 分片。
     *
     * <p>供应商只在首个分片下发 id/name，后续分片仅携带参数增量，
     * 因此需要用三个 Map 分别累积后再组装为完整调用。</p>
     *
     * @param chunks 一轮模型响应的全部片段
     * @return 按 index 排序的工具调用；无工具调用时为空 Map
     */
    private Map<Integer, ToolCall> accumulateToolCalls(List<ChatChunk> chunks) {
        Map<Integer, String> ids = new HashMap<>();
        Map<Integer, String> names = new HashMap<>();
        Map<Integer, StringBuilder> arguments = new HashMap<>();
        for (ChatChunk chunk : chunks) {
            if (chunk.toolCalls() == null) continue;
            for (var delta : chunk.toolCalls()) {
                if (delta.id() != null && !delta.id().isBlank()) ids.put(delta.index(), delta.id());
                if (delta.name() != null && !delta.name().isBlank()) names.put(delta.index(), delta.name());
                if (delta.arguments() != null) arguments.computeIfAbsent(delta.index(), k -> new StringBuilder()).append(delta.arguments());
            }
        }
        Map<Integer, ToolCall> calls = new LinkedHashMap<>();
        for (Integer index : names.keySet()) {
            calls.put(index, new ToolCall(ids.getOrDefault(index, "call_" + index), names.get(index),
                    arguments.getOrDefault(index, new StringBuilder()).toString()));
        }
        return calls;
    }

    /**
     * 将模型给出的工具参数 JSON 解析为 Map。
     *
     * @param arguments 工具参数 JSON 字符串
     * @return 解析结果；为空或解析失败时返回空 Map，交由工具侧按缺省参数处理
     */
    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) return Map.of();
        try { return objectMapper.readValue(arguments, new com.fasterxml.jackson.core.type.TypeReference<>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    /**
     * 把 MCP 工具映射转换为模型可识别的 function 工具定义。
     *
     * @param binding MCP 工具绑定
     * @return OpenAI 风格的 {@code {type:function, function:{name,description,parameters}}} 结构
     */
    private Map<String, Object> toolDefinition(McpToolBinding binding) {
        Object schema;
        try { schema = objectMapper.readTree(binding.inputSchema()); }
        catch (Exception e) { schema = Map.of("type", "object"); }
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", binding.modelName());
        fn.put("description", binding.description() == null ? "" : binding.description());
        fn.put("parameters", schema);
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", fn);
        return tool;
    }

    /**
     * 持久化一次已完成的会话：助手消息、会话汇总与 trace，并记录耗时指标。
     *
     * <p>轮次与 token 在已有会话基础上累加；主题只在首次为空时写入，
     * 保证会话主题始终是首条用户消息。</p>
     *
     * @param agent            当前智能体
     * @param request          原始对话请求
     * @param sessionId        会话 ID
     * @param start            请求开始时间戳（毫秒）
     * @param contextCount     RAG 上下文条数
     * @param resolvedModel    解析后的模型名
     * @param resolvedProvider 解析后的提供方名称
     * @param routeLabel       路由展示标签
     * @param answer           完整助手回答
     * @param newTokens        本轮 token 估算值
     * @param detail           trace 明细说明
     */
    private void saveCompletedSession(AgentSummary agent, RuntimeChatRequest request, String sessionId,
                                      long start, int contextCount, String resolvedModel, String resolvedProvider,
                                      String routeLabel, String answer, int newTokens, String detail,
                                      List<MessageCitation> citations) {
        ChatSession existing = sessions.findById(sessionId).orElse(null);
        int turns = existing == null ? 1 : existing.turns() + 1;
        int totalTokens = (existing == null ? 0 : existing.tokens()) + newTokens;
        String topic = existing == null || existing.topic() == null || existing.topic().isBlank()
                ? request.message() : existing.topic();
        messages.save(new SessionMessage("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                sessionId, "assistant", answer, newTokens, resolvedModel, contextCount,
                java.time.Instant.now(), citations));
        long elapsed = System.currentTimeMillis() - start;
        sessions.save(new ChatSession(sessionId, agent.id(), agent.name(), "success", turns, 0, totalTokens,
                elapsed, LocalTime.now().format(TIME), topic,
                List.of(new TraceStep("model", "模型 + MCP 工具链", elapsed, newTokens, "ok",
                        "路由：" + routeLabel + " · 提供方：" + resolvedProvider + " · 模型：" + resolvedModel + " · " + detail))));
        meters.timer("agent_runtime_duration", "agent", agent.name())
                .record(elapsed, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 构造 SSE meta 事件数据：会话、智能体、模型路由、降级候选与上下文/工具数量。
     *
     * @param sessionId    会话 ID
     * @param agent        当前智能体
     * @param routes       模型路由候选
     * @param resolvedModel 实际使用的模型名
     * @param contextCount RAG 上下文条数
     * @param toolCount    可用工具数；为 0 时不下发 tools 字段
     * @return 保持插入顺序、可直接序列化为 JSON 的 meta 数据
     */
    private Map<String, Object> meta(String sessionId, AgentSummary agent, List<ModelRoute> routes,
                                     String resolvedModel, int contextCount, int toolCount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("agent", agent.name());
        data.put("model", resolvedModel);
        data.put("providerId", routes.isEmpty()
                ? (agent.providerId() == null ? "" : agent.providerId())
                : routes.get(0).providerId());
        data.put("modelId", routes.isEmpty()
                ? (agent.modelId() == null ? "" : agent.modelId())
                : routes.get(0).modelId());
        data.put("provider", model.providerLabel(routes));
        data.put("route", model.routeLabel(routes));
        data.put("fallbacks", routes.stream().skip(1).map(ModelRoute::label).toList());
        data.put("mock", !model.configured(routes));
        data.put("contexts", contextCount);
        if (toolCount > 0) data.put("tools", toolCount);
        return data;
    }

    /**
     * 拼装系统提示词：智能体基础提示词 + 可用工具/知识库说明 + 预取工具结果 + RAG 上下文。
     *
     * <p>RAG 片段带序号与来源，便于模型在回答中标注出处；末尾统一追加中文回答与
     * “不确定时不要编造”的约束，降低幻觉风险。</p>
     *
     * @param agent       当前智能体
     * @param contexts    RAG 检索命中的上下文
     * @param toolContext 本地工具预取结果；为空表示未调用工具
     * @return 完整系统提示词
     */
    private String buildSystemPrompt(AgentSummary agent, List<RetrievalHit> contexts, String toolContext) {
        StringBuilder prompt = new StringBuilder(agent.prompt() == null ? "你是企业的智能助手。" : agent.prompt());
        if (!agent.tools().isEmpty()) {
            prompt.append("\n\n可用工具（需要时在回答中说明调用意图）：").append(String.join("、", agent.tools()));
        }
        if (!agent.kb().isEmpty()) {
            prompt.append("\n\n可检索的知识库：").append(String.join("、", agent.kb()));
        }
        if (!toolContext.isBlank()) {
            prompt.append("\n\n工具调用结果：").append(toolContext);
        }
        if (!contexts.isEmpty()) {
            prompt.append("\n\n以下是检索到的企业知识，回答时优先引用，并标注来源：\n");
            int i = 1;
            for (RetrievalHit hit : contexts) {
                prompt.append("[").append(i++).append("] ").append(hit.source())
                        .append(" #").append(hit.chunk()).append("：").append(hit.text()).append("\n");
            }
        }
        prompt.append("\n\n请用中文回答，给出可执行的下一步。不确定时明确说明，不要编造订单或政策。");
        return prompt.toString();
    }
}
