package com.agentplatform.modules.chat.infrastructure.ai;

import com.agentplatform.modules.model.application.query.ModelCatalog;
import com.agentplatform.modules.model.application.query.ModelRoute;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves provider/model bindings and executes the primary route with ordered fallback.
 * A fallback is attempted only when the previous route failed before emitting any token.
 */
@Component
public class ModelGateway {
    private final OpenAiCompatibleClient openAi;
    private final AnthropicClient anthropic;
    private final ModelCatalog catalog;

    public ModelGateway(OpenAiCompatibleClient openAi, AnthropicClient anthropic, ModelCatalog catalog) {
        this.openAi = openAi;
        this.anthropic = anthropic;
        this.catalog = catalog;
    }

    public List<ModelRoute> routes(String providerId, String modelId) {
        return catalog.routes(providerId, modelId);
    }

    public List<ModelRoute> defaultRoutes() {
        return catalog.defaultRoutes();
    }

    public boolean configured(List<ModelRoute> routes) {
        return !candidates(routes).isEmpty();
    }

    public String routeLabel(List<ModelRoute> routes) {
        List<ModelRoute> usable = candidates(routes);
        if (!usable.isEmpty()) return usable.get(0).label();
        return routes == null || routes.isEmpty() ? "未配置模型" : routes.get(0).label();
    }

    public String providerLabel(List<ModelRoute> routes) {
        List<ModelRoute> usable = candidates(routes);
        if (!usable.isEmpty()) return usable.get(0).providerName();
        return routes == null || routes.isEmpty() ? "—" : routes.get(0).providerName();
    }

    public Flux<String> stream(List<ModelRoute> routes, String systemPrompt, List<AiMessage> history) {
        List<ModelRoute> usable = candidates(routes);
        if (usable.isEmpty()) return mockText(history);
        AtomicBoolean emitted = new AtomicBoolean(false);
        return attemptText(usable, 0, systemPrompt, history, emitted);
    }

    public Flux<ChatChunk> streamWithTools(List<ModelRoute> routes, String systemPrompt,
                                           List<AiMessage> history, List<Map<String, Object>> tools) {
        List<ModelRoute> usable = candidates(routes);
        if (usable.isEmpty()) {
            return mockText(history).map(ChatChunk::text).concatWith(Flux.just(ChatChunk.finish()));
        }
        AtomicBoolean emitted = new AtomicBoolean(false);
        return attemptChunks(usable, 0, systemPrompt, history, tools, emitted);
    }

    public String complete(List<ModelRoute> routes, String systemPrompt, String userMessage) {
        List<String> parts = stream(routes, systemPrompt,
                List.of(new AiMessage("user", userMessage))).collectList().block(Duration.ofSeconds(120));
        return parts == null ? "" : String.join("", parts);
    }

    private Flux<String> attemptText(List<ModelRoute> routes, int index, String systemPrompt,
                                     List<AiMessage> history, AtomicBoolean emitted) {
        ModelRoute route = routes.get(index);
        return client(route).stream(route, systemPrompt, history)
                .doOnNext(value -> emitted.set(true))
                .onErrorResume(error -> {
                    if (emitted.get() || index + 1 >= routes.size()) return Flux.error(error);
                    return attemptText(routes, index + 1, systemPrompt, history, emitted);
                });
    }

    private Flux<ChatChunk> attemptChunks(List<ModelRoute> routes, int index, String systemPrompt,
                                          List<AiMessage> history, List<Map<String, Object>> tools,
                                          AtomicBoolean emitted) {
        ModelRoute route = routes.get(index);
        return client(route).streamWithTools(route, systemPrompt, history, tools)
                .doOnNext(chunk -> emitted.set(true))
                .onErrorResume(error -> {
                    if (emitted.get() || index + 1 >= routes.size()) return Flux.error(error);
                    return attemptChunks(routes, index + 1, systemPrompt, history, tools, emitted);
                });
    }

    private ChatModelClient client(ModelRoute route) {
        return "anthropic".equalsIgnoreCase(route.protocol()) ? anthropic : openAi;
    }

    private List<ModelRoute> candidates(List<ModelRoute> routes) {
        if (routes == null) return List.of();
        Map<String, ModelRoute> unique = new LinkedHashMap<>();
        for (ModelRoute route : routes) {
            if (route != null && route.available()) {
                unique.putIfAbsent(route.providerId() + ":" + route.modelId(), route);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private Flux<String> mockText(List<AiMessage> history) {
        String question = history == null || history.isEmpty() ? "你的问题" : history.get(history.size() - 1).content();
        String answer = "已收到：" + question + "。\n\n当前没有可用的模型 API Key，运行在本地演示模式。" +
                "请在「模型厂商」中为 DeepSeek、Kimi、千问或 Anthropic 配置密钥后重试。";
        String[] parts = answer.split("(?<=。)");
        return Flux.fromArray(parts).delayElements(Duration.ofMillis(80));
    }
}
