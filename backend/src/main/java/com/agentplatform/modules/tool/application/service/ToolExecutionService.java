package com.agentplatform.modules.tool.application.service;

import com.agentplatform.modules.tool.domain.model.Tool;
import com.agentplatform.modules.tool.domain.repository.ToolRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具执行服务（application 层）。
 *
 * <p>按工具类型分派执行：
 * <ul>
 *   <li>{@code MCP} 类型委托给 {@link McpRuntimeService}；</li>
 *   <li>配置了 {@code endpoint} 的工具走 HTTP POST（20 秒超时）；</li>
 *   <li>其余工具走内置模拟/演示实现（其中 {@code weather.query} 调用 wttr.in）。</li>
 * </ul>
 * 关键约束：工具不存在或已停用必须快速失败，不发起任何外部调用。
 */
@Service
public class ToolExecutionService {
    /** 工具目录仓储。 */
    private final ToolRepository tools;
    /** WebClient 构建器，用于 HTTP 工具调用。 */
    private final WebClient.Builder builder;
    /** MCP 运行时服务。 */
    private final McpRuntimeService mcp;
    /** JSON 序列化器。 */
    private final ObjectMapper mapper;
    /** 原生 HttpClient，连接超时 8 秒，供天气接口使用。 */
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    /**
     * 构造执行服务。
     *
     * @param tools   工具目录仓储
     * @param builder WebClient 构建器
     * @param mcp     MCP 运行时服务
     * @param mapper  JSON 序列化器
     */
    public ToolExecutionService(ToolRepository tools, WebClient.Builder builder, McpRuntimeService mcp,
                                ObjectMapper mapper) {
        this.tools = tools; this.builder = builder; this.mcp = mcp; this.mapper = mapper;
    }

    /**
     * 按工具名称执行工具。
     *
     * <p>分派顺序：MCP → 配置了 endpoint 的 HTTP 调用 → 内置模拟实现。
     * 除模拟实现外，错误统一包装为 {@link IllegalStateException} 抛出。
     *
     * @param name  工具名称
     * @param input 输入文本；可为 {@code null}，按空字符串处理
     * @return 工具输出文本（通常为 JSON 字符串）
     * @throws IllegalArgumentException 工具不存在或输入不满足要求
     * @throws IllegalStateException    工具已停用，或执行/网络调用失败
     */
    public String execute(String name, String input) {
        Tool tool = tools.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("工具不存在: " + name));
        if (!tool.enabled()) throw new IllegalStateException("工具已停用: " + name);
        if ("MCP".equalsIgnoreCase(tool.type())) {
            // MCP 工具键与目录名称一致，参数统一包成 {"input": "..."} 透传。
            var outcome = mcp.execute(name, java.util.Map.of("input", input == null ? "" : input));
            if (outcome.error()) throw new IllegalStateException(outcome.text());
            return outcome.text();
        }
        if (tool.endpoint() != null && !tool.endpoint().isBlank()) {
            try {
                String response = builder.build().post().uri(tool.endpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of("input", input == null ? "" : input))
                        .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(20));
                return response == null ? "工具调用完成" : response;
            } catch (Exception e) {
                throw new IllegalStateException("工具调用失败: " + e.getMessage(), e);
            }
        }
        // 未配置 endpoint 的内置工具：返回稳定的演示数据，便于本地联调与工作流演示。
        return switch (name) {
            case "order.query" -> "{\"orderId\":\"202611130097\",\"status\":\"已签收\",\"amount\":268.00}";
            case "risk.score" -> "{\"riskScore\":72,\"level\":\"低风险\"}";
            case "kb.search" -> "{\"hits\":3,\"topScore\":0.91}";
            case "refund.apply" -> "{\"preCheck\":\"passed\",\"eta\":\"1-3 工作日\"}";
            case "bi.query" -> "{\"metric\":\"gmv\",\"value\":\"1,204,880\"}";
            case "notify.send" -> "{\"sent\":true,\"channel\":\"wecom\"}";
            case "weather.query" -> weather(input);
            default -> "{\"tool\":\"" + name + "\",\"status\":\"simulated\"}";
        };
    }

    /**
     * 调用 wttr.in 查询实时天气，并把 JSON:1 响应裁剪为稳定的业务字段。
     *
     * <p>城市名经 URL 编码后拼入路径；请求超时 20 秒，非 2xx 响应视为失败。
     *
     * @param input 原始输入（JSON 或纯文本），由 {@link #extractCity(String)} 解析城市
     * @return 天气结果 JSON 字符串
     * @throws IllegalArgumentException 无法从输入中解析出城市
     * @throws IllegalStateException    天气接口调用失败或返回非 2xx
     */
    private String weather(String input) {
        String city = extractCity(input);
        if (city.isBlank()) throw new IllegalArgumentException("weather.query 需要 city、address 或 text 输入");
        try {
            String url = "https://wttr.in/" + URLEncoder.encode(city, StandardCharsets.UTF_8) + "?format=j1&lang=zh";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "agent-platform-workflow/1.0")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("天气接口 HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode current = root.path("current_condition").path(0);
            JsonNode area = root.path("nearest_area").path(0);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("city", area.path("areaName").path(0).path("value").asText(city));
            result.put("temperature", current.path("temp_C").asText("") + "°C");
            result.put("feelsLike", current.path("FeelsLikeC").asText("") + "°C");
            result.put("weather", current.path("weatherDesc").path(0).path("value").asText(""));
            result.put("humidity", current.path("humidity").asText("") + "%");
            result.put("windSpeed", current.path("windspeedKmph").asText("") + " km/h");
            result.put("observationTime", current.path("observation_time").asText(""));
            result.put("source", "wttr.in");
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException("天气接口调用失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
    }

    /**
     * 从输入中解析城市名。
     *
     * <p>优先按 JSON 解析并依次尝试 {@code city}/{@code address}/{@code query}/{@code text}
     * 字段；解析失败时把整段输入当作城市文本处理。
     *
     * @param input 原始输入，可为 {@code null}
     * @return 清洗后的城市名；无法解析时返回空字符串
     */
    private String extractCity(String input) {
        if (input == null) return "";
        try {
            JsonNode node = mapper.readTree(input);
            for (String field : List.of("city", "address", "query", "text")) {
                if (node.hasNonNull(field) && !node.path(field).asText().isBlank()) {
                    return cleanCity(node.path(field).asText());
                }
            }
        } catch (Exception ignored) { }
        return cleanCity(input);
    }

    /**
     * 清洗城市文本：去掉“天气”及其后内容、问号及其后内容，并限制长度。
     *
     * @param value 原始城市文本，可为 {@code null}
     * @return 清洗后的城市名，最长 40 字符
     */
    private String cleanCity(String value) {
        if (value == null) return "";
        String city = value.replaceAll("(?i)天气.*$", "").replaceAll("[?？].*$", "").trim();
        return city.length() > 40 ? city.substring(0, 40) : city;
    }
}
