package com.agentplatform.modules.chat.interfaces.rest;

import com.agentplatform.framework.security.CurrentUser;
import com.agentplatform.modules.chat.application.runtime.AgentRuntimeService;
import com.agentplatform.modules.chat.application.runtime.RuntimeChatRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话运行时 REST 接口（interfaces 层）。
 *
 * <p>面向用户端提供 SSE 流式对话入口；连接超时、事件格式与运行时会话持久化
 * 均由 {@link AgentRuntimeService} 负责，本控制器只做协议适配。</p>
 */
@RestController
@RequestMapping("/api/v1/runtime")
public class RuntimeController {
    private final AgentRuntimeService runtime;

    public RuntimeController(AgentRuntimeService runtime) { this.runtime = runtime; }

    /**
     * 发起一次流式对话。
     *
     * <p>响应类型为 {@code text/event-stream}，事件包括 meta、delta、tool、tool-result、
     * done 与 error；连接由服务端在完成或超时后关闭。</p>
     *
     * @param request 对话请求（智能体、消息、可选会话 ID）
     * @return SSE 发射器
     */
    @PostMapping(value = "/sessions:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody RuntimeChatRequest request) {
        return runtime.stream(request, CurrentUser.require(), "admin_debug");
    }
}
