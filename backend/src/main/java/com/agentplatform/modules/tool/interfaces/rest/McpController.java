package com.agentplatform.modules.tool.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.tool.application.query.McpCallOutcome;
import com.agentplatform.modules.tool.application.query.McpServerView;
import com.agentplatform.modules.tool.application.query.McpToolView;
import com.agentplatform.modules.tool.application.service.McpServerApplicationService;
import com.agentplatform.modules.tool.interfaces.dto.McpServerRequest;
import com.agentplatform.modules.tool.interfaces.dto.McpToolCallRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP 管理与调用 REST 控制器（interfaces 层）。
 *
 * <p>暴露 {@code /api/v1/mcp-servers} 与 {@code /api/v1/mcp-tools} 相关端点，统一返回
 * {@link ApiResponse}。读接口对已登录用户开放；写操作（创建、更新、删除、连接、同步、
 * 启停、调用）要求 {@code PLATFORM_ADMIN} 或 {@code DEVELOPER} 角色。
 * 业务异常由全局异常处理器转换为统一错误码。
 */
@RestController
@RequestMapping("/api/v1")
public class McpController {
    /** MCP 服务管理应用服务。 */
    private final McpServerApplicationService service;

    /**
     * 构造控制器。
     *
     * @param service MCP 服务管理应用服务
     */
    public McpController(McpServerApplicationService service) { this.service = service; }

    /**
     * 查询全部 MCP 服务。
     *
     * @return 服务视图列表
     */
    @GetMapping("/mcp-servers")
    public ApiResponse<List<McpServerView>> list() { return ApiResponse.ok(service.list()); }

    /**
     * 查询单个 MCP 服务。
     *
     * @param id 服务主键
     * @return 服务视图
     */
    @GetMapping("/mcp-servers/{id}")
    public ApiResponse<McpServerView> get(@PathVariable String id) { return ApiResponse.ok(service.get(id)); }

    /**
     * 创建 MCP 服务。
     *
     * @param request 创建请求
     * @return 创建后的服务视图
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/mcp-servers")
    public ApiResponse<McpServerView> create(@RequestBody McpServerRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /**
     * 更新 MCP 服务配置（未提供字段保持原值）。
     *
     * @param id      服务主键
     * @param request 更新请求
     * @return 更新后的服务视图
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/mcp-servers/{id}")
    public ApiResponse<McpServerView> update(@PathVariable String id, @RequestBody McpServerRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    /**
     * 删除 MCP 服务并级联清理其工具与目录条目。
     *
     * @param id 服务主键
     * @return 空数据的成功响应
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @DeleteMapping("/mcp-servers/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) { service.delete(id); return ApiResponse.ok(null); }

    /**
     * 连接 MCP 服务并同步工具目录。
     *
     * @param id 服务主键
     * @return 连接后的服务视图
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/mcp-servers/{id}/connect")
    public ApiResponse<McpServerView> connect(@PathVariable String id) { return ApiResponse.ok(service.connect(id)); }

    /**
     * 重新同步 MCP 工具（等价于重新连接）。
     *
     * @param id 服务主键
     * @return 同步后的服务视图
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/mcp-servers/{id}/sync")
    public ApiResponse<McpServerView> sync(@PathVariable String id) { return ApiResponse.ok(service.sync(id)); }

    /**
     * 查询某服务下已同步的工具。
     *
     * @param id 服务主键
     * @return 工具视图列表
     */
    @GetMapping("/mcp-servers/{id}/tools")
    public ApiResponse<List<McpToolView>> tools(@PathVariable String id) { return ApiResponse.ok(service.tools(id)); }

    /**
     * 查询某服务最近的调用审计记录（最多 50 条）。
     *
     * @param id 服务主键
     * @return 调用记录列表
     */
    @GetMapping("/mcp-servers/{id}/calls")
    public ApiResponse<List<?>> calls(@PathVariable String id) { return ApiResponse.ok(service.recentCalls(id, 50)); }

    /**
     * 切换 MCP 工具启用状态。
     *
     * @param id MCP 工具主键
     * @return 切换后的工具视图
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/mcp-tools/{id}/toggle")
    public ApiResponse<McpToolView> toggle(@PathVariable String id) { return ApiResponse.ok(service.toggleTool(id)); }

    /**
     * 按工具键直接调用 MCP 工具（管理端调试用）。
     *
     * <p>工具级错误通过返回体的 {@code error=true} 表达，而不是 HTTP 错误状态。
     *
     * @param request 调用请求（工具键 + 参数）
     * @return 调用结果
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/mcp-tools:call")
    public ApiResponse<McpCallOutcome> call(@RequestBody McpToolCallRequest request) {
        return ApiResponse.ok(service.call(request.toolKey(), request.arguments()));
    }
}
