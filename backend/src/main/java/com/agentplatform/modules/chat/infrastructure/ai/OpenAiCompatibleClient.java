package com.agentplatform.modules.chat.infrastructure.ai;

import com.agentplatform.modules.model.application.query.ModelRoute;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * OpenAI-compatible chat completions transport. DeepSeek, Qwen/DashScope, Kimi,
 * OpenAI and Zhipu all expose this protocol; only endpoint/model/auth differ.
 *
 * <p>OpenAI-compatible 协议客户端（infrastructure 层）：DeepSeek、千问/DashScope、
 * Kimi、OpenAI、智谱均复用该实现，差异仅在 endpoint、模型名与鉴权方式。
 * 请求通过 JDK HttpClient 以 SSE 流式读取，响应行解析为 JSON 后映射为文本或工具调用片段。</p>
 */
@Component
public class OpenAiCompatibleClient implements ChatModelClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public OpenAiCompatibleClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 纯文本流式补全：只提取 {@code choices[0].delta.content}。
     *
     * @param route        模型路由
     * @param systemPrompt 系统提示词
     * @param history      对话历史
     * @return 非空文本增量流
     */
    @Override
    public Flux<String> stream(ModelRoute route, String systemPrompt, List<AiMessage> history) {
        Map<String, Object> body = requestBody(route, systemPrompt, history, null);
        return streamRequest(route, body, false).mapNotNull(node -> {
            JsonNode content = node.path("choices").path(0).path("delta").path("content");
            return content.isMissingNode() || content.isNull() || content.asText().isEmpty() ? null : content.asText();
        });
    }

    /**
     * 支持工具调用的流式补全：把每个 SSE 事件同时解析为文本增量与 tool_calls 增量。
     *
     * @param route        模型路由
     * @param systemPrompt 系统提示词
     * @param history      对话历史
     * @param tools        OpenAI 风格工具定义
     * @return 文本/工具调用片段流，末尾追加 {@link ChatChunk#finish()}
     */
    @Override
    public Flux<ChatChunk> streamWithTools(ModelRoute route, String systemPrompt,
                                           List<AiMessage> history, List<Map<String, Object>> tools) {
        Map<String, Object> body = requestBody(route, systemPrompt, history, tools);
        return streamRequest(route, body, true).flatMap(node -> {
            List<ChatChunk> chunks = new ArrayList<>();
            JsonNode delta = node.path("choices").path(0).path("delta");
            JsonNode content = delta.path("content");
            if (!content.isMissingNode() && !content.isNull() && !content.asText().isEmpty()) {
                chunks.add(ChatChunk.text(content.asText()));
            }
            JsonNode toolCalls = delta.path("tool_calls");
            if (toolCalls.isArray()) {
                List<ToolCallDelta> deltas = new ArrayList<>();
                for (JsonNode call : toolCalls) {
                    JsonNode fn = call.path("function");
                    deltas.add(new ToolCallDelta(call.path("index").asInt(0),
                            call.path("id").asText(""), fn.path("name").asText(""),
                            fn.path("arguments").asText("")));
                }
                if (!deltas.isEmpty()) chunks.add(new ChatChunk("", false, deltas));
            }
            return Flux.fromIterable(chunks);
        }).concatWith(Flux.just(ChatChunk.finish()));
    }

    /**
     * 构造 chat completions 请求体。
     *
     * @param route        模型路由
     * @param systemPrompt 系统提示词
     * @param history      对话历史
     * @param tools        工具定义；为空表示不启用工具
     * @return 请求体 Map
     */
    private Map<String, Object> requestBody(ModelRoute route, String systemPrompt,
                                            List<AiMessage> history, List<Map<String, Object>> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", route.upstreamModel());
        body.put("messages", buildMessages(systemPrompt, history));
        body.put("stream", true);
        body.put("temperature", 0.3);
        // DeepSeek 推理模型默认可能输出 thinking，业务对话显式关闭以减少时延和无关内容。
        if ("deepseek".equalsIgnoreCase(route.providerId())) {
            body.put("thinking", Map.of("type", "disabled"));
        }
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        return body;
    }

    /**
     * 发起 HTTP 请求并按 SSE 行解析 JSON 事件。
     *
     * <p>使用 {@link CompletableFuture} 把阻塞式 HttpClient 调用移出调用线程，
     * 通过 {@link Flux#create} 手动把事件推入 sink。</p>
     *
     * @param route     模型路由
     * @param body      请求体
     * @param withTools 是否启用工具调用（仅用于语义区分，请求体已包含 tools）
     * @return JSON 事件流
     */
    private Flux<JsonNode> streamRequest(ModelRoute route, Map<String, Object> body, boolean withTools) {
        return Flux.create(sink -> CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(chatCompletionsUrl(route.baseUrl())))
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + route.apiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                        .build();
                HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
                // 非 2xx 时只取前 12 行错误体，避免把大段 HTML/日志塞进异常消息。
                if (response.statusCode() / 100 != 2) {
                    String detail = response.body().limit(12).reduce("", (a, b) -> a + b + "\n");
                    sink.error(new IllegalStateException(route.label() + " HTTP " + response.statusCode()
                            + ": " + trim(detail)));
                    return;
                }
                response.body().forEach(line -> {
                    // 兼容 "data: {...}" 与裸 JSON 两种行格式；[DONE] 与空行直接跳过。
                    String payload = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
                    if (payload.isBlank() || "[DONE]".equals(payload)) return;
                    try {
                        sink.next(objectMapper.readTree(payload));
                    } catch (Exception ignored) { } // 单个心跳/非 JSON 行解析失败不影响后续事件。
                });
                sink.complete();
            } catch (Exception e) {
                log.warn("{} stream failed: {}", route.label(), e.getMessage());
                sink.error(new IllegalStateException(route.label() + " 调用失败: " + trim(e.getMessage()), e));
            }
        }));
    }

    /**
     * 将通用消息转换为 OpenAI 协议消息：system 置顶，assistant 消息附带 tool_calls，
     * tool 结果消息附带 tool_call_id。
     *
     * @param systemPrompt 系统提示词
     * @param history      对话历史
     * @return OpenAI messages 数组
     */
    private List<Map<String, Object>> buildMessages(String systemPrompt, List<AiMessage> history) {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("role", "system");
        system.put("content", systemPrompt);
        messages.add(system);
        for (AiMessage m : history) {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", m.role());
            message.put("content", m.content() == null ? "" : m.content());
            if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                List<Map<String, Object>> calls = new ArrayList<>();
                for (ToolCall call : m.toolCalls()) {
                    calls.add(Map.of("id", call.id(), "type", "function",
                            "function", Map.of("name", call.name(), "arguments", call.arguments())));
                }
                message.put("tool_calls", calls);
            }
            if (m.toolCallId() != null && !m.toolCallId().isBlank()) {
                message.put("tool_call_id", m.toolCallId());
            }
            messages.add(message);
        }
        return messages;
    }

    /**
     * 归一化 chat completions URL。
     *
     * <p>兼容用户填写的多种 baseUrl：已含 /chat/completions 时直接使用；
     * DashScope 原生地址自动补 /compatible-mode/v1；其余统一追加 /chat/completions。</p>
     *
     * @param baseUrl 模型服务基础地址
     * @return 完整 chat completions 地址
     * @throws IllegalArgumentException baseUrl 为空时抛出
     */
    private String chatCompletionsUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("模型 Endpoint 不能为空");
        }
        String url = baseUrl.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (url.endsWith("/chat/completions")) return url;
        if (url.contains("dashscope.aliyuncs.com") && !url.contains("/compatible-mode") && !url.matches(".*/v\\d+$")) {
            url += "/compatible-mode/v1";
        }
        return url + "/chat/completions";
    }

    /**
     * 压缩错误信息：合并空白并截断到 300 字符，便于写入日志与 SSE error 事件。
     *
     * @param value 原始错误文本
     * @return 归一化后的短文本；null 转为空字符串
     */
    private String trim(String value) {
        if (value == null) return "";
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
