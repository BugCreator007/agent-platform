package com.agentplatform.framework.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置。
 *
 * <p>位于技术框架层，声明接口文档元信息与全局 Bearer JWT 安全方案，
 * 使 Swagger UI（{@code /swagger-ui.html}）可携带 Token 调试受保护接口。
 * 仅影响文档展示，不参与实际认证。</p>
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "Agent Platform Backend", version = "v1",
                description = "DDD modular monolith · JWT · Runtime · RAG · Workflow"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
