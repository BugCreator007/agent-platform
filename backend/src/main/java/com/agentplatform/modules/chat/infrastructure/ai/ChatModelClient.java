package com.agentplatform.modules.chat.infrastructure.ai;

import com.agentplatform.modules.model.application.query.ModelRoute;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

public interface ChatModelClient {
    Flux<String> stream(ModelRoute route, String systemPrompt, List<AiMessage> history);

    Flux<ChatChunk> streamWithTools(ModelRoute route, String systemPrompt,
                                    List<AiMessage> history, List<Map<String, Object>> tools);
}
