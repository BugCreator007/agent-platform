package com.agentplatform.modules.model.application.service;

import com.agentplatform.modules.model.domain.model.Provider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProviderCredentialResolver {
    private final String deepSeekKey;

    public ProviderCredentialResolver(@Value("${app.ai.deepseek.api-key:}") String deepSeekKey) {
        this.deepSeekKey = deepSeekKey == null ? "" : deepSeekKey.trim();
    }

    public String resolve(Provider provider) {
        if (provider == null) return "";
        if (provider.apiKey() != null && !provider.apiKey().isBlank()) return provider.apiKey();
        if ("deepseek".equals(provider.id())) return deepSeekKey;
        return "";
    }
}
