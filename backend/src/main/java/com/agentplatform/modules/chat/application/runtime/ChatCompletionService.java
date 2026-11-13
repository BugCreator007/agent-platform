package com.agentplatform.modules.chat.application.runtime;

import com.agentplatform.modules.chat.infrastructure.ai.ModelGateway;
import org.springframework.stereotype.Service;

@Service
public class ChatCompletionService {
    private final ModelGateway gateway;

    public ChatCompletionService(ModelGateway gateway) {
        this.gateway = gateway;
    }

    public String complete(String systemPrompt, String userMessage) {
        return gateway.complete(gateway.defaultRoutes(), systemPrompt, userMessage);
    }

    public boolean configured() {
        return gateway.configured(gateway.defaultRoutes());
    }
}
