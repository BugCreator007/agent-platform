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
 * <p>基础路径 {@code /api/v1/user}。该控制器不声明 {@code @PreAuthorize}，
 * 接口仅要求登录（由 SecurityConfig 的 {@code anyRequest().authenticated()} 统一约束），
 * 并通过状态白名单限制可访问的智能体范围。</p>
 */
@RestController
@RequestMapping("/api/v1/user")
public class UserPortalController {
    /** 用户端可用状态白名单：运行中与灰度中的智能体；草稿/暂停不可用。 */
    private static final Set<String> USABLE = Set.of("running", "grayscale");

    private final AgentApplicationService agents;
    private final AgentRuntimeService runtime;

    /**
     * @param agents  智能体应用服务（agent 模块公开 API）
     * @param runtime 对话运行时服务（chat 模块公开 API）
     */
    public UserPortalController(AgentApplicationService agents, AgentRuntimeService runtime) {
        this.agents = agents;
        this.runtime = runtime;
    }

    /**
     * 查询用户端可用的智能体目录。
     * <p>先取全量智能体，再按状态白名单过滤，并裁剪为用户端视图
     * （隐藏运营指标、提示词全文与知识库编码）。</p>
     *
     * @return 可用智能体列表
     */
    @GetMapping("/agents")
    public ApiResponse<List<UserAgentView>> agents() {
        return ApiResponse.ok(agents.list(null, null).stream()
                .filter(agent -> USABLE.contains(agent.status()))
                .map(UserAgentView::from)
                .toList());
    }

    /**
     * 与指定智能体进行 SSE 流式对话。
     * <p>校验顺序：消息非空 -> 智能体存在 -> 状态可用；任一不满足直接返回业务异常，
     * 不会建立 SSE 连接。校验通过后交给 chat 模块的运行时服务处理流式输出。</p>
     *
     * @param request 对话请求，包含 agentId 与 message 等字段
     * @return SSE 事件流
     * @throws BusinessException 消息为空（VALIDATION_ERROR）、智能体不存在或未开放（NOT_FOUND）
     */
    @PostMapping(value = "/chat:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody RuntimeChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请输入内容");
        }
        AgentSummary agent = agents.get(request.agentId());
        // 草稿/暂停的智能体不对外开放，统一按“未找到”处理，避免暴露内部状态
        if (!USABLE.contains(agent.status())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该智能体暂未开放");
        }
        return runtime.stream(request);
    }
}
