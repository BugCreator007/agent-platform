package com.agentplatform.modules.tool.application.service;

import com.agentplatform.modules.tool.domain.model.Tool;
import com.agentplatform.modules.tool.domain.repository.ToolRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Service
public class ToolExecutionService {
    private final ToolRepository tools;
    private final WebClient.Builder builder;
    public ToolExecutionService(ToolRepository tools, WebClient.Builder builder) {
        this.tools = tools; this.builder = builder;
    }

    public String execute(String name, String input) {
        Tool tool = tools.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("工具不存在: " + name));
        if (!tool.enabled()) throw new IllegalStateException("工具已停用: " + name);
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
            default -> "{\"tool\":\"" + name + "\",\"status\":\"simulated\"}";
        };
    }
}
