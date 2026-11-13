package com.agentplatform.modules.chat.infrastructure.ai;

import com.agentplatform.modules.model.application.query.ModelRoute;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * Anthropic Messages API transport, including streaming tool_use blocks.
 */
@Component
public class AnthropicClient implements ChatModelClient {
    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);
    private static final String VERSION = "2023-06-01";
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public AnthropicClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<String> stream(ModelRoute route, String systemPrompt, List<AiMessage> history) {
        return events(route, systemPrompt, history, null).mapNotNull(node -> {
            if (!"content_block_delta".equals(node.path("type").asText())) return null;
            JsonNode delta = node.path("delta");
            return "text_delta".equals(delta.path("type").asText()) ? delta.path("text").asText("") : null;
        });
    }

    @Override
    public Flux<ChatChunk> streamWithTools(ModelRoute route, String systemPrompt,
                                           List<AiMessage> history, List<Map<String, Object>> tools) {
        return Flux.create(sink -> {
            Map<Integer, ToolUse> toolUses = new LinkedHashMap<>();
            CompletableFuture.runAsync(() -> {
                try {
                    events(route, systemPrompt, history, tools).subscribe(node -> {
                        String type = node.path("type").asText();
                        if ("error".equals(type)) {
                            sink.error(new IllegalStateException(route.label() + ": "
                                    + node.path("error").path("message").asText("模型调用失败")));
                            return;
                        }
                        if ("content_block_start".equals(type)) {
                            JsonNode block = node.path("content_block");
                            if ("tool_use".equals(block.path("type").asText())) {
                                toolUses.put(node.path("index").asInt(0), new ToolUse(
                                        block.path("id").asText(""), block.path("name").asText("")));
                            }
                            return;
                        }
                        if ("content_block_delta".equals(type)) {
                            JsonNode delta = node.path("delta");
                            if ("text_delta".equals(delta.path("type").asText())) {
                                String text = delta.path("text").asText("");
                                if (!text.isEmpty()) sink.next(ChatChunk.text(text));
                            } else if ("input_json_delta".equals(delta.path("type").asText())) {
                                ToolUse use = toolUses.get(node.path("index").asInt(0));
                                if (use != null) use.arguments.append(delta.path("partial_json").asText(""));
                            }
                            return;
                        }
                        if ("message_stop".equals(type)) {
                            List<ToolCallDelta> calls = new ArrayList<>();
                            int index = 0;
                            for (ToolUse use : toolUses.values()) {
                                calls.add(new ToolCallDelta(index++, use.id, use.name, use.arguments.toString()));
                            }
                            if (!calls.isEmpty()) sink.next(new ChatChunk("", false, calls));
                            sink.next(ChatChunk.finish());
                            sink.complete();
                        }
                    }, sink::error, () -> {
                        if (!sink.isCancelled()) sink.complete();
                    });
                } catch (Exception e) {
                    sink.error(e);
                }
            });
        });
    }

    private Flux<JsonNode> events(ModelRoute route, String systemPrompt,
                                  List<AiMessage> history, List<Map<String, Object>> tools) {
        return Flux.create(sink -> CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", route.upstreamModel());
                body.put("max_tokens", 4096);
                body.put("system", systemPrompt);
                body.put("messages", buildMessages(history));
                body.put("stream", true);
                if (tools != null && !tools.isEmpty()) {
                    body.put("tools", toolDefinitions(tools));
                    body.put("tool_choice", Map.of("type", "auto"));
                }
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(messagesUrl(route.baseUrl())))
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", route.apiKey())
                        .header("anthropic-version", VERSION)
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

    private List<Map<String, Object>> buildMessages(List<AiMessage> history) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (AiMessage m : history) {
            Map<String, Object> message = new LinkedHashMap<>();
            if ("tool".equals(m.role())) {
                message.put("role", "user");
                message.put("content", List.of(Map.of(
                        "type", "tool_result",
                        "tool_use_id", m.toolCallId() == null ? "" : m.toolCallId(),
                        "content", m.content() == null ? "" : m.content())));
            } else if ("assistant".equals(m.role())) {
                List<Map<String, Object>> content = new ArrayList<>();
                if (m.content() != null && !m.content().isBlank()) {
                    content.add(Map.of("type", "text", "text", m.content()));
                }
                if (m.toolCalls() != null) {
                    for (ToolCall call : m.toolCalls()) {
                        content.add(Map.of("type", "tool_use", "id", call.id(),
                                "name", call.name(), "input", parseArguments(call.arguments())));
                    }
                }
                message.put("role", "assistant");
                message.put("content", content.isEmpty() ? List.of(Map.of("type", "text", "text", "")) : content);
            } else {
                message.put("role", "user");
                message.put("content", m.content() == null ? "" : m.content());
            }
            append(messages, message);
        }
        return messages;
    }

    @SuppressWarnings("unchecked")
    private void append(List<Map<String, Object>> messages, Map<String, Object> message) {
        if (messages.isEmpty() || !messages.get(messages.size() - 1).get("role").equals(message.get("role"))) {
            messages.add(message);
            return;
        }
        Map<String, Object> last = messages.get(messages.size() - 1);
        List<Object> content = new ArrayList<>();
        Object previous = last.get("content");
        if (previous instanceof List<?> list) content.addAll(list);
        else if (previous != null && !String.valueOf(previous).isBlank()) {
            content.add(Map.of("type", "text", "text", String.valueOf(previous)));
        }
        Object next = message.get("content");
        if (next instanceof List<?> list) content.addAll(list);
        else if (next != null && !String.valueOf(next).isBlank()) {
            content.add(Map.of("type", "text", "text", String.valueOf(next)));
        }
        last.put("content", content);
    }

    private List<Map<String, Object>> toolDefinitions(List<Map<String, Object>> tools) {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            Object fn = tool.get("function");
            if (!(fn instanceof Map<?, ?> function)) continue;
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("name", function.get("name"));
            definition.put("description", function.get("description") == null ? "" : function.get("description"));
            definition.put("input_schema", function.get("parameters") == null
                    ? Map.of("type", "object", "properties", Map.of())
                    : function.get("parameters"));
            definitions.add(definition);
        }
        return definitions;
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(arguments, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String messagesUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("模型 Endpoint 不能为空");
        String url = baseUrl.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url.endsWith("/messages") ? url : url + "/messages";
    }

    private String trim(String value) {
        if (value == null) return "";
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

    private static final class ToolUse {
        private final String id;
        private final String name;
        private final StringBuilder arguments = new StringBuilder();

        private ToolUse(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
