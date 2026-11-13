package com.agentplatform.modules.knowledge.infrastructure.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Primary
@Component
public class OpenAiEmbeddingClient implements EmbeddingClient {
    private final HashEmbeddingClient fallback;
    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final int dimension;

    public OpenAiEmbeddingClient(HashEmbeddingClient fallback, WebClient.Builder builder,
                                 ObjectMapper mapper,
                                 @Value("${app.ai.embedding.base-url:https://api.openai.com/v1}") String baseUrl,
                                 @Value("${app.ai.embedding.api-key:}") String apiKey,
                                 @Value("${app.ai.embedding.model:text-embedding-3-small}") String model,
                                 @Value("${app.ai.embedding.dimension:384}") int dimension) {
        this.fallback = fallback;
        this.webClient = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.dimension = dimension;
    }

    @Override public int dimension() { return apiKey.isBlank() ? fallback.dimension() : dimension; }

    @Override
    public List<Double> embed(String text) {
        if (apiKey.isBlank()) return fallback.embed(text);
        try {
            JsonNode response = webClient.post().uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(apiKey))
                    .bodyValue(Map.of("model", model, "input", text))
                    .retrieve().bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(30));
            JsonNode embedding = response == null ? null : response.path("data").path(0).path("embedding");
            if (embedding == null || !embedding.isArray()) return fallback.embed(text);
            List<Double> vector = new ArrayList<>();
            embedding.forEach(v -> vector.add(v.asDouble()));
            return vector;
        } catch (Exception e) {
            return fallback.embed(text);
        }
    }
}
