package com.agentplatform.modules.system.application.auth;

import com.agentplatform.modules.system.domain.model.AuditLog;
import com.agentplatform.modules.system.domain.repository.SystemRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class AuditService {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final SystemRepository repository;

    public AuditService(SystemRepository repository) { this.repository = repository; }

    public void record(String action, String object) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        record(auth == null ? "anonymous" : auth.getName(), action, object);
    }

    public void record(String actor, String action, String object) {
        repository.saveAudit(new AuditLog(actor, action, object,
                LocalDateTime.now().format(FORMAT), clientIp()));
    }

    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
            return request.getRemoteAddr();
        }
        return "unknown";
    }
}
