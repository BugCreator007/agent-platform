package com.agentplatform.admin;

import com.agentplatform.common.api.ApiResponse;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统信息与控制台入口控制器。
 *
 * <p>位于 admin（启动与系统）模块的接口层，提供无需认证的服务元信息
 * （服务名、激活的 profile、模块清单），供前端启动页与健康探测使用。
 * 普通信息接口保持无敏感内容，便于在 {@code SecurityConfig} 中放行。</p>
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    private final Environment environment;

    /**
     * @param environment Spring 环境抽象，用于读取应用名与激活 profile
     */
    public SystemController(Environment environment) {
        this.environment = environment;
    }

    /**
     * 查询服务基本信息。
     *
     * @return 包含服务名、激活 profile 列表与状态的数据
     */
    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        return ApiResponse.ok(Map.of(
                "service", environment.getProperty("spring.application.name", "agent-backend"),
                "profiles", List.of(environment.getActiveProfiles()),
                "status", "UP"
        ));
    }

    /**
     * 查询已启用的业务模块清单。
     *
     * <p>当前为静态列表，供管理后台菜单/概览展示。</p>
     *
     * @return 模块名称列表
     */
    @GetMapping("/modules")
    public ApiResponse<List<String>> modules() {
        return ApiResponse.ok(List.of(
                "agent", "chat", "knowledge", "model", "release", "eval",
                "observe", "open", "system", "tool", "workflow"
        ));
    }
}
