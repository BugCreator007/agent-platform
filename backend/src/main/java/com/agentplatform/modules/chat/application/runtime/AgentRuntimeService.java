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
import com.agentplatform.modules.knowledge.application.query.RetrievalHit;
import com.agentplatform.modules.knowledge.application.query.RetrievalResult;
import com.agentplatform.modules.knowledge.application.service.KnowledgeApplicationService;
import com.agentplatform.modules.tool.application.service.ToolExecutionService;
import io.micrometer.core.instrument.MeterRegistry;
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

@Service
public class AgentRuntimeService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final AgentApplicationService agents;
    private final SessionRepository sessions;
    private final SessionMessageRepository messages;
    private final DeepSeekClient model;
    private final KnowledgeApplicationService knowledge;
    private final ToolExecutionService tools;
    private final MeterRegistry meters;

    public AgentRuntimeService(AgentApplicationService agents, SessionRepository sessions,
                               SessionMessageRepository messages, DeepSeekClient model,
                               KnowledgeApplicationService knowledge,
                               ToolExecutionService tools, MeterRegistry meters) {
        this.agents = agents;
        this.sessions = sessions;
        this.messages = messages;
        this.model = model;
        this.knowledge = knowledge;
        this.tools = tools;
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
