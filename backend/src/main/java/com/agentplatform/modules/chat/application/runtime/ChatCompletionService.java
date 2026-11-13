package com.agentplatform.modules.chat.application.runtime;

import com.agentplatform.modules.chat.infrastructure.ai.DeepSeekClient;
import org.springframework.stereotype.Service;

@Service
public class ChatCompletionService {
    private final DeepSeekClient client;
    public ChatCompletionService(DeepSeekClient client) { this.client = client; }
    public String complete(String systemPrompt, String userMessage) {
        return client.complete(systemPrompt, userMessage);
    }
    public boolean configured() { return client.configured(); }
}
