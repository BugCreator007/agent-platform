package com.agentplatform.modules.workflow.application.service;

import com.agentplatform.modules.workflow.domain.model.WorkflowInstance;
import com.agentplatform.modules.workflow.domain.model.WorkflowNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WorkflowNotificationService {
    private final ObjectMapper mapper;
    private final String wecomWebhook;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public WorkflowNotificationService(ObjectMapper mapper,
                                       @Value("${app.notify.wecom-webhook:}") String wecomWebhook) {
        this.mapper = mapper;
        this.wecomWebhook = wecomWebhook == null ? "" : wecomWebhook.trim();
    }

    public String send(WorkflowNode node, WorkflowInstance instance, Map<String, Object> variables) {
        Map<String, Object> config = node.config();
        String channel = string(config.get("channel"), "wecom").toLowerCase();
        String message = render(string(config.get("message"), "流程节点 " + node.title() + " 执行完成"), instance, variables)
                .replace("${node}", node.key())
                .replace("${nodeTitle}", node.title());
        if ("wecom".equals(channel)) return wecom(message);
        if ("webhook".equals(channel)) return webhook(string(config.get("target"), string(config.get("url"), "")), node, instance, message, variables);
        throw new IllegalStateException("不支持的 NOTIFY 通道: " + channel + "（支持 wecom、webhook）");
    }

    private String wecom(String message) {
        if (wecomWebhook.isBlank()) throw new IllegalStateException("未配置企微 Webhook（app.notify.wecom-webhook）");
        Map<String, Object> body = Map.of("msgtype", "markdown", "markdown", Map.of("content", message));
        post(wecomWebhook, body, "企微");
        return "wecom:sent";
    }

    private String webhook(String target, WorkflowNode node, WorkflowInstance instance,
                           String message, Map<String, Object> variables) {
        if (target == null || target.isBlank()) throw new IllegalStateException("NOTIFY webhook 未配置 target");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workflow", instance.workflowName());
        body.put("workflowId", instance.workflowId());
        body.put("instanceId", instance.id());
        body.put("node", node.key());
        body.put("title", node.title());
        body.put("message", message);
        body.put("variables", variables);
        post(target, body, "Webhook");
        return "webhook:sent";
    }

    private void post(String url, Map<String, Object> body, String label) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(label + " 通知失败 HTTP " + response.statusCode());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(label + " 通知失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
    }

    private String render(String template, WorkflowInstance instance, Map<String, Object> variables) {
        String value = template
                .replace("${workflow}", instance.workflowName())
                .replace("${instanceId}", instance.id())
                .replace("${input}", instance.inputJson() == null ? "" : instance.inputJson());
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            value = value.replace("${" + entry.getKey() + "}", entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return value;
    }

    private String string(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }
}
