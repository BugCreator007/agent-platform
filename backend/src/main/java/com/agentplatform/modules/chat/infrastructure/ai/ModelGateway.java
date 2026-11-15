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
 *
 * <p>模型网关（infrastructure 层）：解析可用的模型路由，按顺序执行主路由与降级路由，
 * 并根据协议选择 OpenAI-compatible 或 Anthropic 客户端。没有可用密钥时返回本地演示文本，
 * 保证功能可演示。降级只在当前路由尚未输出任何 token 时触发，避免同一回答拼接不同模型。</p>
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

    /**
     * 解析指定提供方/模型的路由候选列表（主路由在前，降级路由在后）。
     *
     * @param providerId 提供方 ID；为空时由模型目录按默认规则解析
     * @param modelId    模型 ID；为空时取提供方默认模型
     * @return 路由候选列表；无可用配置时可能为空列表
     */
    public List<ModelRoute> routes(String providerId, String modelId) {
        return catalog.routes(providerId, modelId);
    }

    /**
     * 获取平台默认模型路由。
     *
     * @return 默认路由候选列表
     */
    public List<ModelRoute> defaultRoutes() {
        return catalog.defaultRoutes();
    }

    /**
     * 判断给定路由中是否存在真正可用（已配置密钥且启用）的模型。
     *
     * @param routes 路由候选
     * @return 至少一条可用路由时为 true；否则为 false
     */
    public boolean configured(List<ModelRoute> routes) {
        return !candidates(routes).isEmpty();
    }

    /**
     * 返回用于展示的路由标签，优先取首个可用路由。
     *
     * @param routes 路由候选
     * @return 路由标签；无可用路由时返回首个配置的标签或“未配置模型”
     */
    public String routeLabel(List<ModelRoute> routes) {
        List<ModelRoute> usable = candidates(routes);
        if (!usable.isEmpty()) return usable.get(0).label();
        return routes == null || routes.isEmpty() ? "未配置模型" : routes.get(0).label();
    }

    /**
     * 返回用于展示的提供方名称，优先取首个可用路由。
     *
     * @param routes 路由候选
     * @return 提供方名称；无可用路由时返回首个配置的名称或“—”
     */
    public String providerLabel(List<ModelRoute> routes) {
        List<ModelRoute> usable = candidates(routes);
        if (!usable.isEmpty()) return usable.get(0).providerName();
        return routes == null || routes.isEmpty() ? "—" : routes.get(0).providerName();
    }

    /**
     * 执行纯文本流式补全，必要时按顺序降级。
     *
     * @param routes       路由候选
     * @param systemPrompt 系统提示词
     * @param history      对话历史
     * @return 文本增量流；无可用路由时为本地演示文本流
     */
    public Flux<String> stream(List<ModelRoute> routes, String systemPrompt, List<AiMessage> history) {
        List<ModelRoute> usable = candidates(routes);
        if (usable.isEmpty()) return mockText(history);
        AtomicBoolean emitted = new AtomicBoolean(false);
        return attemptText(usable, 0, systemPrompt, history, emitted);
    }

    /**
     * 执行支持工具调用的流式补全，必要时按顺序降级。
     *
     * @param routes       路由候选
     * @param systemPrompt 系统提示词
     * @param history      对话历史
     * @param tools        OpenAI 风格工具定义
     * @return 片段流；无可用路由时把演示文本包装为文本片段并以 finish 结尾
     */
    public Flux<ChatChunk> streamWithTools(List<ModelRoute> routes, String systemPrompt,
                                           List<AiMessage> history, List<Map<String, Object>> tools) {
        List<ModelRoute> usable = candidates(routes);
        if (usable.isEmpty()) {
            return mockText(history).map(ChatChunk::text).concatWith(Flux.just(ChatChunk.finish()));
        }
        AtomicBoolean emitted = new AtomicBoolean(false);
        return attemptChunks(usable, 0, systemPrompt, history, tools, emitted);
    }

    /**
     * 同步补全：把流式结果收集后拼接为完整文本。
     *
     * @param routes       路由候选
     * @param systemPrompt 系统提示词
     * @param userMessage  单条用户消息
     * @return 完整回答；120 秒内未完成或结果为空时返回空字符串
     */
    public String complete(List<ModelRoute> routes, String systemPrompt, String userMessage) {
        List<String> parts = stream(routes, systemPrompt,
                List.of(new AiMessage("user", userMessage))).collectList().block(Duration.ofSeconds(120));
        return parts == null ? "" : String.join("", parts);
    }

    // 递归尝试下一路由：仅当当前路由尚未输出任何 token（emitted=false）且仍有候选时才降级。
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

    // 工具流式调用的降级逻辑与文本流一致，避免半截回答来自不同模型。
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

    // 按协议选择客户端：anthropic 走 Messages API，其余均走 OpenAI-compatible。
    private ChatModelClient client(ModelRoute route) {
        return "anthropic".equalsIgnoreCase(route.protocol()) ? anthropic : openAi;
    }

    // 过滤未启用/无密钥路由，并按 providerId:modelId 去重，保留首次出现的顺序。
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

    // 本地演示流：无密钥时也能展示 SSE 效果；按句拆分并逐段延迟 80ms 模拟打字。
    private Flux<String> mockText(List<AiMessage> history) {
        String question = history == null || history.isEmpty() ? "你的问题" : history.get(history.size() - 1).content();
        String answer = "已收到：" + question + "。\n\n当前没有可用的模型 API Key，运行在本地演示模式。" +
                "请在「模型厂商」中为 DeepSeek、Kimi、千问或 Anthropic 配置密钥后重试。";
        String[] parts = answer.split("(?<=。)");
        return Flux.fromArray(parts).delayElements(Duration.ofMillis(80));
    }
}
