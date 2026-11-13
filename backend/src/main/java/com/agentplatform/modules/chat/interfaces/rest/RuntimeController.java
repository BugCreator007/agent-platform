package com.agentplatform.modules.chat.interfaces.rest;

import com.agentplatform.modules.chat.application.runtime.AgentRuntimeService;
import com.agentplatform.modules.chat.application.runtime.RuntimeChatRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/runtime")
public class RuntimeController {
    private final AgentRuntimeService runtime;

    public RuntimeController(AgentRuntimeService runtime) { this.runtime = runtime; }

    @PostMapping(value = "/sessions:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody RuntimeChatRequest request) {
        return runtime.stream(request);
    }
}
