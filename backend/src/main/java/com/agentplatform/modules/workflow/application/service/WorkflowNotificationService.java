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

/**
 * 流程通知服务，供 NOTIFY 节点使用。
 *
 * <p>所属层：application 应用层。支持两种通道：</p>
 * <ul>
 *   <li>{@code wecom}（默认）：企业微信群机器人 Markdown 消息，Webhook 地址来自配置
 *       {@code app.notify.wecom-webhook}；</li>
 *   <li>{@code webhook}：向节点配置的 {@code target}（兼容旧字段 {@code url}）POST JSON 载荷。</li>
 * </ul>
 *
 * <p>消息模板支持占位符 {@code ${workflow}}、{@code ${instanceId}}、{@code ${input}}、
 * {@code ${node}}、{@code ${nodeTitle}} 以及表达式变量表中的任意键。发送失败会抛出异常，
 * 由节点执行器按重试策略处理并最终影响流程状态。</p>
 */
@Service
public class WorkflowNotificationService {
    /** JSON 序列化器。 */
    private final ObjectMapper mapper;
    /** 企业微信机器人 Webhook，空字符串表示未配置。 */
    private final String wecomWebhook;
    /** 复用的 HTTP 客户端，连接超时 8 秒。 */
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    /**
     * 构造通知服务。
     *
     * @param mapper       JSON 序列化器
     * @param wecomWebhook 企业微信机器人 Webhook，未配置时为空
     */
    public WorkflowNotificationService(ObjectMapper mapper,
                                       @Value("${app.notify.wecom-webhook:}") String wecomWebhook) {
        this.mapper = mapper;
        this.wecomWebhook = wecomWebhook == null ? "" : wecomWebhook.trim();
    }

    /**
     * 按节点配置渲染并发送通知。
     *
     * <p>节点配置项：{@code channel}（wecom/webhook）、{@code message}（模板）、
     * {@code target}/{@code url}（webhook 地址）。</p>
     *
     * @param node      当前 NOTIFY 节点定义
     * @param instance  所属流程实例
     * @param variables 表达式变量表（用于模板占位符替换）
     * @return 发送结果标识，{@code wecom:sent} 或 {@code webhook:sent}
     * @throws IllegalStateException 通道不支持、未配置地址或 HTTP 非 2xx 时抛出
     */
    public String send(WorkflowNode node, WorkflowInstance instance, Map<String, Object> variables) {
        Map<String, Object> config = node.config();
        String channel = string(config.get("channel"), "wecom").toLowerCase();
        // 先替换通用占位符，节点占位符单独处理，便于在消息中引用当前节点信息。
        String message = render(string(config.get("message"), "流程节点 " + node.title() + " 执行完成"), instance, variables)
                .replace("${node}", node.key())
                .replace("${nodeTitle}", node.title());
        if ("wecom".equals(channel)) return wecom(message);
        if ("webhook".equals(channel)) return webhook(string(config.get("target"), string(config.get("url"), "")), node, instance, message, variables);
        throw new IllegalStateException("不支持的 NOTIFY 通道: " + channel + "（支持 wecom、webhook）");
    }

    /**
     * 发送企业微信 Markdown 消息。
     *
     * @param message 已渲染的消息内容
     * @return 固定结果标识 {@code wecom:sent}
     * @throws IllegalStateException 未配置 Webhook 或发送失败时抛出
     */
    private String wecom(String message) {
        if (wecomWebhook.isBlank()) throw new IllegalStateException("未配置企微 Webhook（app.notify.wecom-webhook）");
        Map<String, Object> body = Map.of("msgtype", "markdown", "markdown", Map.of("content", message));
        post(wecomWebhook, body, "企微");
        return "wecom:sent";
    }

    /**
     * 发送通用 Webhook 通知，载荷包含流程、实例、节点与变量信息。
     *
     * @param target    目标 URL
     * @param node      当前节点
     * @param instance  所属流程实例
     * @param message   已渲染的消息内容
     * @param variables 表达式变量表
     * @return 固定结果标识 {@code webhook:sent}
     * @throws IllegalStateException 未配置 target 或发送失败时抛出
     */
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

    /**
     * 执行 POST 并校验响应状态码为 2xx。
     *
     * @param url   目标地址
     * @param body  JSON 请求体
     * @param label 通道名称，用于错误信息
     * @throws IllegalStateException 网络异常或非 2xx 响应时抛出
     */
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
            // 状态码错误已是业务异常，直接透传，避免被再次包装。
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(label + " 通知失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), e);
        }
    }

    /**
     * 渲染消息模板：替换流程/实例/输入占位符，再替换变量表中的占位符。
     */
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

    /** 取字符串配置值，空白时返回回退值。 */
    private String string(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }
}
