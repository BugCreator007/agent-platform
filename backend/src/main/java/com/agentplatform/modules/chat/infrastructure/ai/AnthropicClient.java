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
 *
 * <p>Anthropic 协议客户端（infrastructure 层）：通过 {@code /v1/messages} SSE 接口流式读取，
 * 支持文本 delta 与 {@code tool_use} 输入参数分片。与 OpenAI 不同，Anthropic 的工具结果
 * 必须作为 user 角色的 {@code tool_result} 内容块回传，消息构造在 {@link #buildMessages} 中完成转换。</p>
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

    /**
     * 纯文本流式补全：只提取 {@code content_block_delta} 中的 {@code text_delta}。
     *
     * @param route        模型路由
     * @param systemPrompt 系统提示词
     * @param history      对话历史
     * @return 非空文本增量流
     */
    @Override
    public Flux<String> stream(ModelRoute route, String systemPrompt, List<AiMessage> history) {
        return events(route, systemPrompt, history, null).mapNotNull(node -> {
            if (!"content_block_delta".equals(node.path("type").asText())) return null;
            JsonNode delta = node.path("delta");
            return "text_delta".equals(delta.path("type").asText()) ? delta.path("text").asText("") : null;
        });
    }

    /**
     * 支持工具调用的流式补全：累积 {@code tool_use} 块及其参数分片，在 {@code message_stop} 时统一下发。
     *
     * <p>Anthropic 会把工具名与 ID 放在 {@code content_block_start}，参数 JSON 通过
     * 多个 {@code input_json_delta} 分片发送；因此在流结束前需按块 index 聚合。</p>
     *
     * @param route        模型路由
     * @param systemPrompt 系统提示词
     * @param history      对话历史
     * @param tools        OpenAI 风格工具定义（内部会转换为 Anthropic input_schema）
     * @return 文本/工具调用片段流，末尾追加 {@link ChatChunk#finish()}
     */
    @Override
    public Flux<ChatChunk> streamWithTools(ModelRoute route, String systemPrompt,
                                           List<AiMessage> history, List<Map<String, Object>> tools) {
        return Flux.create(sink -> {
            Map<Integer, ToolUse> toolUses = new LinkedHashMap<>();
            CompletableFuture.runAsync(() -> {
                try {
                    events(route, systemPrompt, history, tools).subscribe(node -> {
                        String type = node.path("type").asText();
                        // 流内错误事件需要显式转成异常，否则 sink 会静默结束。
                        if ("error".equals(type)) {
                            sink.error(new IllegalStateException(route.label() + ": "
                                    + node.path("error").path("message").asText("模型调用失败")));
                            return;
                        }
                        if ("content_block_start".equals(type)) {
                            // 工具块开始时记录 ID、名称，后续参数分片按 index 归集。
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
                                // 工具参数以 JSON 片段流式下发，需逐片拼接到同一个 StringBuilder。
                                ToolUse use = toolUses.get(node.path("index").asInt(0));
                                if (use != null) use.arguments.append(delta.path("partial_json").asText(""));
                            }
                            return;
                        }
                        if ("message_stop".equals(type)) {
                            // 流结束时一次性下发聚合后的工具调用，再补 finish 标记并关闭流。
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

    /**
     * 发起 Messages API 请求并逐行解析 SSE 事件。
     *
     * <p>使用 {@link CompletableFuture} 包装阻塞式 HttpClient，避免占用订阅线程；
     * 请求超时 120 秒，与运行时 SSE 的整体预算配合。</p>
     *
     * @param route        模型路由
     * @param systemPrompt 系统提示词（作为顶层 {@code system} 字段）
     * @param history      对话历史
     * @param tools        工具定义；为 null 或空时不启用工具
     * @return Anthropic 事件 JSON 流
     */
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
                // 非 2xx 时截取前 12 行错误体，防止大段响应进入异常信息。
                if (response.statusCode() / 100 != 2) {
                    String detail = response.body().limit(12).reduce("", (a, b) -> a + b + "\n");
                    sink.error(new IllegalStateException(route.label() + " HTTP " + response.statusCode()
                            + ": " + trim(detail)));
                    return;
                }
                response.body().forEach(line -> {
                    // Anthropic 使用 "data: {json}" 格式；空行与 [DONE] 不是业务事件。
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

    /**
     * 将通用消息转换为 Anthropic 消息格式。
     *
     * <p>关键差异：tool 结果必须包装为 user 角色的 {@code tool_result} 内容块；
     * assistant 消息的 toolCalls 转换为 {@code tool_use} 内容块；
     * 纯文本 user/assistant 消息保持字符串内容。</p>
     *
     * @param history 对话历史
     * @return Anthropic messages 数组
     */
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

    /**
     * 合并相邻同角色消息。
     *
     * <p>Anthropic 要求 user/assistant 角色交替出现；工具结果与后续用户消息同为 user 时，
     * 需要把内容块合并到同一条消息中，否则请求会被拒绝。</p>
     *
     * @param messages 已构造的消息列表（原地修改）
     * @param message  待追加的消息
     */
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

    /**
     * 将 OpenAI 风格工具定义转换为 Anthropic 格式（{@code parameters} → {@code input_schema}）。
     *
     * @param tools OpenAI 风格工具定义
     * @return Anthropic tools 数组；缺少 function 字段的项会被跳过
     */
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

    /**
     * 解析工具参数 JSON；失败时返回空 Map，避免因模型输出不合法而中断调用。
     *
     * @param arguments 参数 JSON 字符串
     * @return 参数 Map
     */
    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(arguments, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 归一化 Messages API URL：去除末尾斜杠，未以 /messages 结尾时自动追加。
     *
     * @param baseUrl 模型服务基础地址
     * @return 完整 Messages API 地址
     * @throws IllegalArgumentException baseUrl 为空时抛出
     */
    private String messagesUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("模型 Endpoint 不能为空");
        String url = baseUrl.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url.endsWith("/messages") ? url : url + "/messages";
    }

    /**
     * 压缩错误信息：合并空白并截断到 300 字符。
     *
     * @param value 原始错误文本
     * @return 归一化后的短文本
     */
    private String trim(String value) {
        if (value == null) return "";
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

    /**
     * 流式工具调用聚合容器：ID 与名称来自 content_block_start，参数由多个 JSON 分片拼接。
     */
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
