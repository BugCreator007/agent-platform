package com.agentplatform.modules.chat.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekClient {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public DeepSeekClient(WebClient.Builder builder, ObjectMapper objectMapper,
                          @Value("${app.ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
                          @Value("${app.ai.deepseek.api-key:}") String apiKey,
                          @Value("${app.ai.deepseek.model:deepseek-flash}") String model) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    public boolean configured() { return !apiKey.isBlank(); }

    public String modelName(String preferred) {
        if (preferred != null) {
            String p = preferred.toLowerCase();
            if (p.contains("pro") || p.contains("reasoner") || p.contains("r1")) return "deepseek-v4-pro";
            if (p.contains("flash") || p.contains("v4")) return "deepseek-flash";
        }
        return model;
    }

    public String complete(String systemPrompt, String userMessage) {
        return complete(model, systemPrompt, userMessage);
    }

    public String complete(String modelName, String systemPrompt, String userMessage) {
        List<String> parts = stream(modelName, systemPrompt, List.of(new AiMessage("user", userMessage)))
                .collectList().block(Duration.ofSeconds(120));
        return parts == null ? "" : String.join("", parts);
    }

    public Flux<String> stream(String systemPrompt, List<AiMessage> history) {
        return stream(model, systemPrompt, history);
    }

    public Flux<String> stream(String modelName, String systemPrompt, List<AiMessage> history) {
        if (!configured()) return mockResponse(history);
        if (modelName == null || modelName.isBlank()) modelName = model;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", buildMessages(systemPrompt, history));
        body.put("stream", true);
        body.put("temperature", 0.3);
        body.put("thinking", Map.of("type", "disabled"));
        return Flux.create(sink -> CompletableFuture.runAsync(() -> {
            try {
                String json = objectMapper.writeValueAsString(body);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/chat/completions"))
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                HttpResponse<Stream<String>> response = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10)).build()
                        .send(request, HttpResponse.BodyHandlers.ofLines());
                if (response.statusCode() / 100 != 2) {
                    String detail = response.body().limit(10).reduce("", (a, b) -> a + b + "\n");
                    sink.error(new IllegalStateException("DeepSeek HTTP " + response.statusCode() + ": " + detail));
                    return;
                }
                response.body().forEach(line -> {
                    String payload = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
                    if (payload.isBlank()) return;
                    if ("[DONE]".equals(payload)) { sink.complete(); return; }
                    try {
                        JsonNode node = objectMapper.readTree(payload);
                        JsonNode delta = node.path("choices").path(0).path("delta");
                        JsonNode content = delta.path("content");
                        if (!content.isMissingNode() && !content.isNull() && !content.asText().isEmpty()) {
                            sink.next(content.asText());
                        }
                    } catch (Exception ignored) { }
                });
                sink.complete();
            } catch (Exception e) {
                log.warn("DeepSeek stream failed: {}", e.getMessage());
                sink.error(e);
            }
        }));
    }

    public Flux<ChatChunk> streamWithTools(String modelName, String systemPrompt,
                                            List<AiMessage> history, List<Map<String, Object>> tools) {
        if (!configured()) {
            return mockResponse(history).map(ChatChunk::text).concatWith(Flux.just(ChatChunk.finish()));
        }
        if (modelName == null || modelName.isBlank()) modelName = model;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", buildMessages(systemPrompt, history));
        body.put("stream", true);
        body.put("temperature", 0.3);
        body.put("thinking", Map.of("type", "disabled"));
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }
        return Flux.create(sink -> CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/chat/completions"))
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                        .build();
                HttpResponse<Stream<String>> response = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10)).build()
                        .send(request, HttpResponse.BodyHandlers.ofLines());
                if (response.statusCode() / 100 != 2) {
                    String detail = response.body().limit(10).reduce("", (a, b) -> a + b + "\n");
                    sink.error(new IllegalStateException("DeepSeek HTTP " + response.statusCode() + ": " + detail));
                    return;
                }
                response.body().forEach(line -> {
                    String payload = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
                    if (payload.isBlank()) return;
                    if ("[DONE]".equals(payload)) { sink.next(ChatChunk.finish()); return; }
                    try {
                        JsonNode node = objectMapper.readTree(payload);
                        JsonNode delta = node.path("choices").path(0).path("delta");
                        JsonNode content = delta.path("content");
                        if (!content.isMissingNode() && !content.isNull() && !content.asText().isEmpty()) {
                            sink.next(ChatChunk.text(content.asText()));
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
                            if (!deltas.isEmpty()) sink.next(new ChatChunk("", false, deltas));
                        }
                    } catch (Exception ignored) { }
                });
                sink.complete();
            } catch (Exception e) {
                log.warn("DeepSeek tool stream failed: {}", e.getMessage());
                sink.error(e);
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

    private Flux<String> mockResponse(List<AiMessage> history) {
        String question = history.isEmpty() ? "你的问题" : history.get(history.size() - 1).content();
        String answer = "已收到：" + question + "。\n\n当前未配置 DEEPSEEK_API_KEY，运行在本地演示模式。" +
                "我会先核实订单与知识库信息，再依据智能体提示词给出回答；接入 DeepSeek 后这里会返回真实模型流式结果。";
        String[] parts = answer.split("(?<=。)");
        return Flux.fromArray(parts).delayElements(Duration.ofMillis(90));
    }

    private String fallbackText(List<AiMessage> history) {
        return history.isEmpty() ? "请稍后重试。" : "关于「" + history.get(history.size() - 1).content() + "」，请稍后重试或联系管理员。";
    }
}
