package com.agentplatform.modules.chat.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.chat.application.service.SessionApplicationService;
import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.model.SessionMessage;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {
    private final SessionApplicationService applicationService;

    public SessionController(SessionApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ApiResponse<List<ChatSession>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String agentId,
            @RequestParam(required = false, name = "q") String q) {
        return ApiResponse.ok(applicationService.list(status, agentId, q));
    }

    @GetMapping("/{id}")
    public ApiResponse<ChatSession> get(@PathVariable String id) {
        return ApiResponse.ok(applicationService.get(id));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        applicationService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<SessionMessage>> messages(@PathVariable String id) {
        return ApiResponse.ok(applicationService.messages(id));
    }
}
