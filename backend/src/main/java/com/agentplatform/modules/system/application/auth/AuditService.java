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

/**
 * 审计记录服务。
 *
 * <p>位于应用层，为登录、登出以及写操作过滤器提供审计写入能力。
 * 操作人默认取当前安全上下文；客户端 IP 兼容反向代理的 {@code X-Forwarded-For}。
 * 约束：不得记录密码、Token、Cookie、完整提示词等敏感信息。</p>
 */
@Service
public class AuditService {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final SystemRepository repository;

    /**
     * @param repository 平台系统仓储（审计写入端口）
     */
    public AuditService(SystemRepository repository) { this.repository = repository; }

    /**
     * 以当前认证用户身份记录审计。
     *
     * @param action 操作描述
     * @param object 操作对象
     */
    public void record(String action, String object) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        record(auth == null ? "anonymous" : auth.getName(), action, object);
    }

    /**
     * 以指定操作人身份记录审计。
     *
     * @param actor  操作人
     * @param action 操作描述
     * @param object 操作对象
     */
    public void record(String actor, String action, String object) {
        repository.saveAudit(new AuditLog(actor, action, object,
                LocalDateTime.now().format(FORMAT), clientIp()));
    }

    /**
     * 解析客户端 IP。
     *
     * <p>部署在 Caddy/Nginx 之后时 {@code remoteAddr} 是代理地址，
     * 因此优先取 {@code X-Forwarded-For} 的第一个地址；非 Web 线程（如异步任务）返回 unknown。</p>
     *
     * @return 客户端 IP 或 unknown
     */
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
