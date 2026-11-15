package com.agentplatform.modules.system.infrastructure.security;

import com.agentplatform.modules.system.application.auth.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 写操作审计过滤器。
 *
 * <p>位于基础设施安全层，以 Servlet Filter 形式对所有 {@code /api/v1/*} 的非 GET 请求
 * （排除登录与 OPTIONS 预检）记录一条审计日志。先执行业务链路再记录，
 * 因此能捕获响应状态码；日志异常一律吞掉，绝不能影响业务请求。
 * 约束：只记录方法、路径、状态码与操作人，不记录请求体，避免泄露密码/Token/提示词。</p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditLogFilter extends OncePerRequestFilter {
    private final AuditService auditService;

    /**
     * @param auditService 审计记录服务
     */
    public AuditLogFilter(AuditService auditService) { this.auditService = auditService; }

    /**
     * 过滤逻辑：业务执行完成后追加审计记录。
     *
     * @param request     请求
     * @param response    响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 处理异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 先放行到业务链路，返回后才能拿到最终状态码
        filterChain.doFilter(request, response);
        String method = request.getMethod();
        String path = request.getRequestURI();
        // 只审计 API 下的写操作；登录接口自身已有更细粒度审计，避免重复记录
        if (path.startsWith("/api/v1") && !"GET".equalsIgnoreCase(method)
                && !"OPTIONS".equalsIgnoreCase(method) && !path.startsWith("/api/v1/auth/login")) {
            try {
                auditService.record(method + " " + path + " [" + response.getStatus() + "]", path);
            } catch (RuntimeException ignored) {
                // audit must never break the business request
            }
        }
    }
}
