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

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuditLogFilter extends OncePerRequestFilter {
    private final AuditService auditService;

    public AuditLogFilter(AuditService auditService) { this.auditService = auditService; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        String method = request.getMethod();
        String path = request.getRequestURI();
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
