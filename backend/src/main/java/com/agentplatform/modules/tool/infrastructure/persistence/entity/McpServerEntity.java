package com.agentplatform.modules.tool.infrastructure.persistence.entity;

import com.agentplatform.framework.security.SecretCipher;
import com.agentplatform.modules.tool.domain.model.McpServer;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "mcp_servers")
public class McpServerEntity {
    @Id private String id;
    private String name;
    private String transport;
    @Column(columnDefinition = "TEXT") private String url;
    private String command;
    @Column(columnDefinition = "TEXT") private String args;
    @Column(name = "auth_type") private String authType;
    @Column(name = "secret_encrypted", columnDefinition = "TEXT") private String secretEncrypted;
    private String status;
    @Column(name = "last_error", columnDefinition = "TEXT") private String lastError;
    @Column(name = "last_connected_at") private Instant lastConnectedAt;
    private boolean enabled;

    public static McpServerEntity from(McpServer s, SecretCipher cipher) {
        McpServerEntity e = new McpServerEntity();
        e.id=s.id(); e.name=s.name(); e.transport=s.transport(); e.url=s.url(); e.command=s.command();
        e.args=s.args(); e.authType=s.authType(); e.secretEncrypted=cipher.encrypt(s.authToken());
        e.status=s.status(); e.lastError=s.lastError(); e.lastConnectedAt=s.lastConnectedAt(); e.enabled=s.enabled();
        return e;
    }
    public McpServer toDomain(SecretCipher cipher) {
        return new McpServer(id, name, transport, url, command, args, authType, cipher.decrypt(secretEncrypted),
                enabled, status, lastError, lastConnectedAt);
    }
}
