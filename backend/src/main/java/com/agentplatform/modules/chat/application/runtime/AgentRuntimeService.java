package com.agentplatform.modules.chat.application.runtime;

import com.agentplatform.modules.agent.application.query.AgentSummary;
import com.agentplatform.modules.agent.application.service.AgentApplicationService;
import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.model.SessionMessage;
import com.agentplatform.modules.chat.domain.model.TraceStep;
import com.agentplatform.modules.chat.domain.repository.SessionMessageRepository;
import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import com.agentplatform.modules.chat.infrastructure.ai.AiMessage;
import com.agentplatform.modules.chat.infrastructure.ai.DeepSeekClient;
import com.agentplatform.modules.chat.infrastructure.ai.ChatChunk;
import com.agentplatform.modules.chat.infrastructure.ai.ToolCall;
import com.agentplatform.modules.tool.application.query.McpCallOutcome;
import com.agentplatform.modules.tool.application.query.McpToolBinding;
import com.agentplatform.modules.tool.application.service.McpRuntimeService;
import com.agentplatform.modules.knowledge.application.query.RetrievalHit;
import com.agentplatform.modules.knowledge.application.query.RetrievalResult;
import com.agentplatform.modules.knowledge.application.service.KnowledgeApplicationService;
import com.agentplatform.modules.tool.application.service.ToolExecutionService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.time.Duration;

@Service
public class AgentRuntimeService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final AgentApplicationService agents;
    private final SessionRepository sessions;
    private final SessionMessageRepository messages;
    private final DeepSeekClient model;
    private final KnowledgeApplicationService knowledge;
    private final ToolExecutionService tools;
    private final McpRuntimeService mcp;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;

    public AgentRuntimeService(AgentApplicationService agents, SessionRepository sessions,
                               SessionMessageRepository messages, DeepSeekClient model,
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

    public SseEmitter stream(RuntimeChatRequest request) {
        AgentSummary agent = agents.get(request.agentId());
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? "s_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) : request.sessionId();
        SseEmitter emitter = new SseEmitter(180_000L);
        long start = System.currentTimeMillis();
        meters.counter("agent_runtime_requests_total", "agent", agent.name()).increment();
        StringBuilder answer = new StringBuilder();
        AtomicLong tokens = new AtomicLong();
        ChatSession existingSession = sessions.findById(sessionId).orElse(null);
        if (existingSession == null) {
            sessions.save(new ChatSession(sessionId, agent.id(), agent.name(), "running", 0, 0, 0,
                    0, LocalTime.now().format(TIME), request.message(), List.of()));
        }
        List<AiMessage> history = new ArrayList<>();
        List<SessionMessage> previous = messages.findBySession(sessionId);
        int from = Math.max(0, previous.size() - 12);
        for (int i = from; i < previous.size(); i++) {
            SessionMessage m = previous.get(i);
            if ("user".equals(m.role()) || "assistant".equals(m.role())) {
                history.add(new AiMessage(m.role(), m.content()));
            }
        }
        history.add(new AiMessage("user", request.message()));
        messages.save(new SessionMessage("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                sessionId, "user", request.message(), 0, null, 0, java.time.Instant.now()));

        List<RetrievalHit> contexts = new ArrayList<>();
        for (String kbId : agent.kb()) {
            try {
                RetrievalResult result = knowledge.retrieve(kbId, request.message());
                contexts.addAll(result.hits());
            } catch (RuntimeException ignored) { }
        }
        String toolContext = "";
        if (agent.tools().contains("order.query")
                && (request.message().contains("订单") || request.message().contains("退款"))) {
            try { toolContext = tools.execute("order.query", request.message()); } catch (RuntimeException ignored) { }
        }
        String systemPrompt = buildSystemPrompt(agent, contexts, toolContext);
        String resolvedModel = model.modelName(agent.modelId() == null ? agent.model() : agent.modelId());
        List<McpToolBinding> mcpBindings;
        try { mcpBindings = mcp.bindings(agent.tools()); } catch (RuntimeException e) { mcpBindings = List.of(); }
        if (!mcpBindings.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("meta").data(Map.of(
                        "sessionId", sessionId, "agent", agent.name(), "model", resolvedModel,
                        "providerId", agent.providerId() == null ? "" : agent.providerId(),
                        "modelId", agent.modelId() == null ? "" : agent.modelId(),
                        "mock", !model.configured(), "contexts", contexts.size(),
                        "tools", mcpBindings.size())));
            } catch (IOException e) {
                emitter.completeWithError(e);
                return emitter;
            }
            final List<McpToolBinding> bindings = List.copyOf(mcpBindings);
            CompletableFuture.runAsync(() -> runMcpLoop(emitter, agent, request, sessionId, start,
                    contexts.size(), systemPrompt, resolvedModel, history, bindings));
            return emitter;
        }
        Disposable disposable = model.stream(resolvedModel, systemPrompt, history).subscribe(
                delta -> {
                    answer.append(delta);
                    tokens.addAndGet(Math.max(1, delta.length() / 2));
                    try { emitter.send(SseEmitter.event().name("delta").data(delta, MediaType.TEXT_PLAIN)); }
                    catch (IOException e) { emitter.completeWithError(e); }
                },
                error -> {
                    meters.counter("agent_runtime_errors_total", "agent", agent.name()).increment();
                    try {
                        emitter.send(SseEmitter.event().name("error").data(Map.of("message",
                                error.getMessage() == null ? "模型调用失败" : error.getMessage())));
                    } catch (IOException ignored) { }
                    emitter.completeWithError(error);
                },
                () -> {
                    long elapsed = System.currentTimeMillis() - start;
                    int newTokens = (int) Math.max(1, tokens.get());
                    ChatSession existing = sessions.findById(sessionId).orElse(null);
                    int turns = existing == null ? 1 : existing.turns() + 1;
                    int totalTokens = (existing == null ? 0 : existing.tokens()) + newTokens;
                    String topic = existing == null || existing.topic() == null || existing.topic().isBlank()
                            ? request.message() : existing.topic();
                    messages.save(new SessionMessage("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                            sessionId, "assistant", answer.toString(), newTokens, resolvedModel, contexts.size(),
                            java.time.Instant.now()));
                    ChatSession session = new ChatSession(sessionId, agent.id(), agent.name(), "success",
                            turns, 0, totalTokens, elapsed, LocalTime.now().format(TIME), topic,
                            List.of(new TraceStep("model", "DeepSeek 流式生成", elapsed,
                                    newTokens, "ok", "模型：" + resolvedModel + " · 上下文 " + contexts.size() + " 条")));
                    sessions.save(session);
                    meters.timer("agent_runtime_duration", "agent", agent.name())
                            .record(elapsed, java.util.concurrent.TimeUnit.MILLISECONDS);
                    try {
                        emitter.send(SseEmitter.event().name("done").data(Map.of(
                                "sessionId", sessionId, "tokens", session.tokens(), "ms", elapsed,
                                "model", resolvedModel, "mock", !model.configured(), "contexts", contexts.size())));
                        emitter.complete();
                    } catch (IOException e) { emitter.completeWithError(e); }
                });
        emitter.onCompletion(disposable::dispose);
        emitter.onTimeout(() -> { disposable.dispose(); emitter.complete(); });
        try {
            emitter.send(SseEmitter.event().name("meta").data(Map.of(
                    "sessionId", sessionId, "agent", agent.name(), "model", resolvedModel,
                    "providerId", agent.providerId() == null ? "" : agent.providerId(),
                    "modelId", agent.modelId() == null ? "" : agent.modelId(),
                    "mock", !model.configured(), "contexts", contexts.size())));
        } catch (IOException e) { emitter.completeWithError(e); }
        return emitter;
    }

    private void runMcpLoop(SseEmitter emitter, AgentSummary agent, RuntimeChatRequest request,
                            String sessionId, long start, int contextCount, String systemPrompt,
                            String resolvedModel, List<AiMessage> history, List<McpToolBinding> bindings) {
        StringBuilder answer = new StringBuilder();
        AtomicLong tokens = new AtomicLong();
        List<AiMessage> chat = new ArrayList<>(history);
        List<Map<String, Object>> toolDefinitions = bindings.stream().map(this::toolDefinition).toList();
        try {
            for (int round = 0; round < 4; round++) {
                StringBuilder roundText = new StringBuilder();
                List<ChatChunk> chunks = model.streamWithTools(resolvedModel, systemPrompt, chat, toolDefinitions)
                        .doOnNext(chunk -> {
                            if (chunk.content() != null && !chunk.content().isEmpty()) {
                                roundText.append(chunk.content());
                                answer.append(chunk.content());
                                tokens.addAndGet(Math.max(1, chunk.content().length() / 2));
                                try {
                                    emitter.send(SseEmitter.event().name("delta")
                                            .data(chunk.content(), MediaType.TEXT_PLAIN));
                                } catch (IOException e) { throw new RuntimeException(e); }
                            }
                        })
                        .collectList().block(Duration.ofSeconds(120));
                Map<Integer, ToolCall> calls = accumulateToolCalls(chunks == null ? List.of() : chunks);
                if (calls.isEmpty()) break;
                chat.add(new AiMessage("assistant", roundText.toString(), new ArrayList<>(calls.values()), null));
                for (ToolCall call : calls.values()) {
                    McpToolBinding binding = bindings.stream()
                            .filter(b -> b.modelName().equals(call.name())).findFirst().orElse(null);
                    if (binding == null) {
                        chat.add(new AiMessage("tool", "未找到工具映射: " + call.name(), List.of(), call.id()));
                        continue;
                    }
                    Map<String, Object> arguments = parseArguments(call.arguments());
                    try {
                        emitter.send(SseEmitter.event().name("tool").data(Map.of(
                                "name", binding.name(), "arguments", arguments)));
                    } catch (IOException ignored) { }
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
            saveCompletedSession(agent, request, sessionId, start, contextCount, resolvedModel,
                    answer.toString(), newTokens, "MCP 工具链 · " + bindings.size() + " 个工具");
            try {
                emitter.send(SseEmitter.event().name("done").data(Map.of(
                        "sessionId", sessionId, "tokens", newTokens, "ms", System.currentTimeMillis() - start,
                        "model", resolvedModel, "mock", !model.configured(), "contexts", contextCount,
                        "tools", bindings.size())));
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

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) return Map.of();
        try { return objectMapper.readValue(arguments, new com.fasterxml.jackson.core.type.TypeReference<>() {}); }
        catch (Exception e) { return Map.of(); }
    }

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

    private void saveCompletedSession(AgentSummary agent, RuntimeChatRequest request, String sessionId,
                                      long start, int contextCount, String resolvedModel,
                                      String answer, int newTokens, String detail) {
        ChatSession existing = sessions.findById(sessionId).orElse(null);
        int turns = existing == null ? 1 : existing.turns() + 1;
        int totalTokens = (existing == null ? 0 : existing.tokens()) + newTokens;
        String topic = existing == null || existing.topic() == null || existing.topic().isBlank()
                ? request.message() : existing.topic();
        messages.save(new SessionMessage("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                sessionId, "assistant", answer, newTokens, resolvedModel, contextCount, java.time.Instant.now()));
        long elapsed = System.currentTimeMillis() - start;
        sessions.save(new ChatSession(sessionId, agent.id(), agent.name(), "success", turns, 0, totalTokens,
                elapsed, LocalTime.now().format(TIME), topic,
                List.of(new TraceStep("model", "DeepSeek + MCP 工具链", elapsed, newTokens, "ok",
                        "模型：" + resolvedModel + " · " + detail))));
        meters.timer("agent_runtime_duration", "agent", agent.name())
                .record(elapsed, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

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
