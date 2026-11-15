package com.agentplatform.modules.model.application.service;

import com.agentplatform.modules.model.domain.model.Provider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 厂商凭据解析器（应用层组件）。
 *
 * <p>统一决定调用厂商时使用哪个 API Key：优先使用数据库中保存的密钥，
 * 数据库未配置时对内置 DeepSeek 回退到 {@code app.ai.deepseek.api-key}
 * 环境变量/配置项，便于本地开发与首次部署。</p>
 */
@Component
public class ProviderCredentialResolver {
    private final String deepSeekKey;

    /**
     * 构造解析器。
     *
     * @param deepSeekKey 来自配置的 DeepSeek API Key，未配置时为空串
     */
    public ProviderCredentialResolver(@Value("${app.ai.deepseek.api-key:}") String deepSeekKey) {
        this.deepSeekKey = deepSeekKey == null ? "" : deepSeekKey.trim();
    }

    /**
     * 解析厂商实际使用的 API Key。
     *
     * @param provider 厂商领域对象，可为 null
     * @return 数据库中的密钥；若无且为 deepseek 厂商则返回配置值；否则返回空串
     */
    public String resolve(Provider provider) {
        if (provider == null) return "";
        if (provider.apiKey() != null && !provider.apiKey().isBlank()) return provider.apiKey();
        // 数据库未保存密钥时，DeepSeek 回退到环境变量，保证已有本地安装无需重新配置
        if ("deepseek".equals(provider.id())) return deepSeekKey;
        return "";
    }
}
