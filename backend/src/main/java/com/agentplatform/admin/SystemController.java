package com.agentplatform.admin;

import com.agentplatform.common.api.ApiResponse;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    private final Environment environment;

    public SystemController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        return ApiResponse.ok(Map.of(
                "service", environment.getProperty("spring.application.name", "agent-backend"),
                "profiles", List.of(environment.getActiveProfiles()),
                "status", "UP"
        ));
    }

    @GetMapping("/modules")
    public ApiResponse<List<String>> modules() {
        return ApiResponse.ok(List.of(
                "agent", "chat", "knowledge", "model", "release", "eval",
                "observe", "open", "system", "tool", "workflow"
        ));
    }
}
