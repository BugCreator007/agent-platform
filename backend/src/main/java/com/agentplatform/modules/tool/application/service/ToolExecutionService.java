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

@Service
public class ToolExecutionService {
    private final ToolRepository tools;
    private final WebClient.Builder builder;
    private final McpRuntimeService mcp;
    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public ToolExecutionService(ToolRepository tools, WebClient.Builder builder, McpRuntimeService mcp,
                                ObjectMapper mapper) {
        this.tools = tools; this.builder = builder; this.mcp = mcp; this.mapper = mapper;
    }

    public String execute(String name, String input) {
        Tool tool = tools.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("工具不存在: " + name));
        if (!tool.enabled()) throw new IllegalStateException("工具已停用: " + name);
        if ("MCP".equalsIgnoreCase(tool.type())) {
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

    private String cleanCity(String value) {
        if (value == null) return "";
        String city = value.replaceAll("(?i)天气.*$", "").replaceAll("[?？].*$", "").trim();
        return city.length() > 40 ? city.substring(0, 40) : city;
    }
}
