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

    @Override
    public Flux<String> stream(ModelRoute route, String systemPrompt, List<AiMessage> history) {
        Map<String, Object> body = requestBody(route, systemPrompt, history, null);
        return streamRequest(route, body, false).mapNotNull(node -> {
            JsonNode content = node.path("choices").path(0).path("delta").path("content");
            return content.isMissingNode() || content.isNull() || content.asText().isEmpty() ? null : content.asText();
        });
    }

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

    private Map<String, Object> requestBody(ModelRoute route, String systemPrompt,
                                            List<AiMessage> history, List<Map<String, Object>> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", route.upstreamModel());
        body.put("messages", buildMessages(systemPrompt, history));
        body.put("stream", true);
        body.put("temperature", 0.3);
        if ("deepseek".equalsIgnoreCase(route.providerId())) {
            body.put("thinking", Map.of("type", "disabled"));
        }
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        return body;
    }

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
                if (response.statusCode() / 100 != 2) {
                    String detail = response.body().limit(12).reduce("", (a, b) -> a + b + "\n");
                    sink.error(new IllegalStateException(route.label() + " HTTP " + response.statusCode()
                            + ": " + trim(detail)));
                    return;
                }
                response.body().forEach(line -> {
                    String payload = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
                    if (payload.isBlank() || "[DONE]".equals(payload)) return;
                    try {
                        sink.next(objectMapper.readTree(payload));
                    } catch (Exception ignored) { }
                });
                sink.complete();
            } catch (Exception e) {
                log.warn("{} stream failed: {}", route.label(), e.getMessage());
                sink.error(new IllegalStateException(route.label() + " 调用失败: " + trim(e.getMessage()), e));
            }
        }));
    }

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

    private String trim(String value) {
        if (value == null) return "";
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
