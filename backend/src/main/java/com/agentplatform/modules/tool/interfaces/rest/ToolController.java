package com.agentplatform.modules.tool.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.tool.application.service.ToolApplicationService;
import com.agentplatform.modules.tool.domain.model.Tool;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工具目录管理 REST 控制器（interfaces 层）。
 *
 * <p>暴露 {@code /api/v1/tools} 端点，统一返回 {@link ApiResponse}。列表对已登录用户
 * 开放；创建、启停与健康检查要求 {@code PLATFORM_ADMIN} 或 {@code DEVELOPER} 角色。
 */
@RestController
@RequestMapping("/api/v1/tools")
public class ToolController {
    /** 工具目录应用服务。 */
    private final ToolApplicationService applicationService;

    /**
     * 构造控制器。
     *
     * @param applicationService 工具目录应用服务
     */
    public ToolController(ToolApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询全部工具。
     *
     * @return 工具列表
     */
    @GetMapping
    public ApiResponse<List<Tool>> list() { return ApiResponse.ok(applicationService.list()); }

    /**
     * 创建工具（请求体使用宽松的 Map，便于前端按需传字段）。
     *
     * @param body 请求体，可包含 {@code name}/{@code type}/{@code owner}/
     *             {@code description}/{@code endpoint}
     * @return 创建后的工具
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping
    public ApiResponse<Tool> create(@RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok(applicationService.create(
                str(body.get("name")), str(body.get("type")), str(body.get("owner")), str(body.get("description")),
                str(body.get("endpoint"))));
    }

    /**
     * 以 MCP 类型创建工具目录条目。
     *
     * <p>类型固定为 {@code MCP}，不接收 endpoint；实际 MCP 工具通常由服务同步自动创建。
     *
     * @param body 请求体，可包含 {@code name}/{@code owner}/{@code description}
     * @return 创建后的工具
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/mcp")
    public ApiResponse<Tool> addMcp(@RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok(applicationService.create(
                str(body.get("name")), "MCP", str(body.get("owner")), str(body.get("description"))));
    }

    /**
     * 切换指定工具的启用状态。
     *
     * @param name 工具名称
     * @return 切换后的工具
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/{name}/toggle")
    public ApiResponse<Tool> toggle(@PathVariable String name) { return ApiResponse.ok(applicationService.toggle(name)); }

    /**
     * 对全部工具执行一次健康检查。
     *
     * @return 检查后的工具列表
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/check")
    public ApiResponse<List<Tool>> check() { return ApiResponse.ok(applicationService.checkAll()); }

    /**
     * 把请求体中的任意值转为字符串（{@code null} 保持为 {@code null}）。
     *
     * <p>配合宽松 Map 请求体，允许前端传数字等类型而不报反序列化错误。
     *
     * @param value 原始值
     * @return 字符串值或 {@code null}
     */
    private static String str(Object value) { return value == null ? null : String.valueOf(value); }
}
