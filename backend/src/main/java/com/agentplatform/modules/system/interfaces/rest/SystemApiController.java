package com.agentplatform.modules.system.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.system.application.service.SystemApplicationService;
import com.agentplatform.modules.system.domain.model.ApiKey;
import com.agentplatform.modules.system.domain.model.AuditLog;
import com.agentplatform.modules.system.domain.model.Member;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 平台系统管理接口控制器。
 *
 * <p>位于接口层，提供成员、API Key 与审计日志的查询/变更接口。
 * 类级 {@code @PreAuthorize} 要求 {@code PLATFORM_ADMIN} 角色，
 * 写操作另由 {@code AuditLogFilter} 审计。</p>
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class SystemApiController {
    private final SystemApplicationService applicationService;

    /**
     * @param applicationService 平台系统管理用例服务
     */
    public SystemApiController(SystemApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询成员列表。
     *
     * @return 成员列表
     */
    @GetMapping("/members")
    public ApiResponse<List<Member>> members() { return ApiResponse.ok(applicationService.members()); }

    /**
     * 变更成员角色。
     *
     * @param name 成员姓名（路径参数）
     * @param body 请求体，字段 {@code role}；缺省为「只读成员」
     * @return 更新后的成员
     */
    @PutMapping("/members/{name}/role")
    public ApiResponse<Member> role(@PathVariable String name, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(applicationService.changeRole(name, body.getOrDefault("role", "只读成员")));
    }

    /**
     * 邀请新成员。
     *
     * @param body 请求体，字段 {@code name}/{@code email}/{@code role}
     * @return 新建的成员
     */
    @PostMapping("/members")
    public ApiResponse<Member> invite(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(applicationService.invite(str(body.get("name")), str(body.get("email")), str(body.get("role"))));
    }

    /**
     * 查询 API Key 列表。
     *
     * @return 密钥列表
     */
    @GetMapping("/api-keys")
    public ApiResponse<List<ApiKey>> keys() { return ApiResponse.ok(applicationService.keys()); }

    /**
     * 创建 API Key。
     *
     * <p>创建人当前固定为演示账号「顾先路」，后续应改为取当前认证用户。</p>
     *
     * @param body 请求体，字段 {@code name}/{@code scope}
     * @return 新建的密钥记录（含明文密钥，仅此一次）
     */
    @PostMapping("/api-keys")
    public ApiResponse<ApiKey> createKey(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(applicationService.createKey(str(body.get("name")), str(body.get("scope")), "顾先路"));
    }

    /**
     * 查询审计日志。
     *
     * @return 审计日志列表（时间倒序）
     */
    @GetMapping("/audit-logs")
    public ApiResponse<List<AuditLog>> audit() { return ApiResponse.ok(applicationService.audit()); }

    /**
     * 将任意请求体值转为字符串，{@code null} 原样返回。
     *
     * @param value 请求体中的原始值
     * @return 字符串值或 {@code null}
     */
    private static String str(Object value) { return value == null ? null : String.valueOf(value); }
}
