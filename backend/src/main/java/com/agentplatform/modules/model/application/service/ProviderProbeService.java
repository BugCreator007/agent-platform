package com.agentplatform.modules.model.application.service;

import com.agentplatform.modules.model.application.query.ProviderTestResult;
import com.agentplatform.modules.model.domain.model.ModelInfo;
import com.agentplatform.modules.model.domain.model.Provider;
import com.agentplatform.modules.model.domain.repository.ModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 厂商连通性探测服务（应用层）。
 *
 * <p>发送一次极小的真实请求（max_tokens=1、内容 ping）来验证 endpoint 与密钥是否可用，
 * 并根据结果把厂商状态更新为 healthy/degraded。兼容 OpenAI 兼容协议与 Anthropic 协议。
 * 探测失败不会抛出异常，而是把原因包装进 {@link ProviderTestResult} 返回给控制台。</p>
 */
@Service
public class ProviderProbeService {
    private final ModelRepository repository;
    private final ProviderCredentialResolver credentials;
    private final ObjectMapper mapper;

    /**
     * 构造探测服务。
     *
     * @param repository  模型模块领域仓储端口
     * @param credentials 厂商密钥解析器
     * @param mapper      JSON 序列化器
     */
    public ProviderProbeService(ModelRepository repository, ProviderCredentialResolver credentials,
                                ObjectMapper mapper) {
        this.repository = repository;
        this.credentials = credentials;
        this.mapper = mapper;
    }

    /**
     * 探测指定厂商：选择其下一个未暂停模型发起最小调用并回写状态。
     *
     * @param provider 待探测厂商
     * @return 探测结果；未配置 Key、没有模型、HTTP 非 2xx 或网络异常时 ok=false
     */
    public ProviderTestResult test(Provider provider) {
        String key = credentials.resolve(provider);
        if (key == null || key.isBlank()) {
            provider.markFailed("—");
            repository.saveProvider(provider);
            return new ProviderTestResult(false, provider.status(), "—", "未配置 API Key");
        }
        // 优先用未暂停的模型做探测，避免拿停用模型误判厂商不可用
        ModelInfo model = repository.findModels().stream()
                .filter(m -> provider.id().equals(m.providerId()))
                .filter(m -> !"paused".equalsIgnoreCase(m.status()))
                .findFirst()
                .orElseGet(() -> repository.findModels().stream()
                        .filter(m -> provider.id().equals(m.providerId())).findFirst().orElse(null));
        if (model == null) {
            provider.markFailed("—");
            repository.saveProvider(provider);
            return new ProviderTestResult(false, provider.status(), "—", "该厂商还没有模型，请先添加模型");
        }

        long start = System.currentTimeMillis();
        try {
            String protocol = provider.protocol() == null ? "openai" : provider.protocol().toLowerCase();
            String url;
            Map<String, Object> body = new LinkedHashMap<>();
            HttpRequest.Builder request;
            if ("anthropic".equals(protocol)) {
                // Anthropic 协议：x-api-key + anthropic-version 头，端点 /messages
                url = endpoint(provider.endpoint(), "/messages");
                body.put("model", model.upstreamModel());
                body.put("max_tokens", 1);
                body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
                request = HttpRequest.newBuilder().uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", key)
                        .header("anthropic-version", "2023-06-01");
            } else {
                // 其余按 OpenAI 兼容协议：Bearer 鉴权，端点 /chat/completions
                url = endpoint(provider.endpoint(), "/chat/completions");
                body.put("model", model.upstreamModel());
                body.put("max_tokens", 1);
                body.put("stream", false);
                body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
                request = HttpRequest.newBuilder().uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + key);
            }
            // 连接 8s、请求 20s 双重超时，避免探测任务长时间挂起
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build()
                    .send(request.timeout(Duration.ofSeconds(20))
                            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                            .build(), HttpResponse.BodyHandlers.ofString());
            String latency = (System.currentTimeMillis() - start) + " ms";
            if (response.statusCode() / 100 == 2) {
                provider.markTested(latency);
                repository.saveProvider(provider);
                return new ProviderTestResult(true, provider.status(), latency, "连接成功 · " + model.name());
            }
            provider.markFailed(latency);
            repository.saveProvider(provider);
            return new ProviderTestResult(false, provider.status(), latency,
                    "HTTP " + response.statusCode() + ": " + trim(response.body()));
        } catch (Exception e) {
            provider.markFailed("—");
            repository.saveProvider(provider);
            return new ProviderTestResult(false, provider.status(), "—",
                    e.getMessage() == null ? "连接失败" : trim(e.getMessage()));
        }
    }

    /**
     * 拼接完整请求地址：去掉基地址末尾斜杠，已包含目标路径时不重复拼接。
     *
     * @param base 基地址
     * @param path 目标路径，如 /chat/completions
     * @return 完整 URL
     * @throws IllegalArgumentException 基地址为空时抛出
     */
    private String endpoint(String base, String path) {
        if (base == null || base.isBlank()) throw new IllegalArgumentException("Endpoint 不能为空");
        String value = base.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.endsWith(path)) return value;
        return value + path;
    }

    /**
     * 压缩空白并把错误信息截断到 240 字符，避免把超长响应写入状态/日志。
     *
     * @param value 原始文本
     * @return 规整后的文本
     */
    private String trim(String value) {
        if (value == null) return "";
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() > 240 ? text.substring(0, 240) + "…" : text;
    }
}
