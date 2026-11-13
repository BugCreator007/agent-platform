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

@Service
public class ProviderProbeService {
    private final ModelRepository repository;
    private final ProviderCredentialResolver credentials;
    private final ObjectMapper mapper;

    public ProviderProbeService(ModelRepository repository, ProviderCredentialResolver credentials,
                                ObjectMapper mapper) {
        this.repository = repository;
        this.credentials = credentials;
        this.mapper = mapper;
    }

    public ProviderTestResult test(Provider provider) {
        String key = credentials.resolve(provider);
        if (key == null || key.isBlank()) {
            provider.markFailed("—");
            repository.saveProvider(provider);
            return new ProviderTestResult(false, provider.status(), "—", "未配置 API Key");
        }
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
                url = endpoint(provider.endpoint(), "/messages");
                body.put("model", model.upstreamModel());
                body.put("max_tokens", 1);
                body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
                request = HttpRequest.newBuilder().uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("x-api-key", key)
                        .header("anthropic-version", "2023-06-01");
            } else {
                url = endpoint(provider.endpoint(), "/chat/completions");
                body.put("model", model.upstreamModel());
                body.put("max_tokens", 1);
                body.put("stream", false);
                body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
                request = HttpRequest.newBuilder().uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + key);
            }
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

    private String endpoint(String base, String path) {
        if (base == null || base.isBlank()) throw new IllegalArgumentException("Endpoint 不能为空");
        String value = base.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.endsWith(path)) return value;
        return value + path;
    }

    private String trim(String value) {
        if (value == null) return "";
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() > 240 ? text.substring(0, 240) + "…" : text;
    }
}
