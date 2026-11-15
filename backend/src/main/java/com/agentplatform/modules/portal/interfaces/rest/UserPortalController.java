package com.agentplatform.modules.portal.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.agent.application.query.AgentSummary;
import com.agentplatform.modules.agent.application.service.AgentApplicationService;
import com.agentplatform.modules.chat.application.runtime.AgentRuntimeService;
import com.agentplatform.modules.chat.application.runtime.RuntimeChatRequest;
import com.agentplatform.modules.portal.application.query.UserAgentView;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;

/**
 * 用户端（门户）接口：只暴露已上线的智能体和对话入口。
 */
@RestController
@RequestMapping("/api/v1/user")
public class UserPortalController {
    private static final Set<String> USABLE = Set.of("running", "grayscale");

    private final AgentApplicationService agents;
    private final AgentRuntimeService runtime;

    public UserPortalController(AgentApplicationService agents, AgentRuntimeService runtime) {
        this.agents = agents;
        this.runtime = runtime;
    }

    @GetMapping("/agents")
    public ApiResponse<List<UserAgentView>> agents() {
        return ApiResponse.ok(agents.list(null, null).stream()
                .filter(agent -> USABLE.contains(agent.status()))
                .map(UserAgentView::from)
                .toList());
    }

    @PostMapping(value = "/chat:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody RuntimeChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请输入内容");
        }
        AgentSummary agent = agents.get(request.agentId());
        if (!USABLE.contains(agent.status())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该智能体暂未开放");
        }
        return runtime.stream(request);
    }
}
