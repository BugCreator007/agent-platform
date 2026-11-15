package com.agentplatform.modules.tool.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.framework.security.SecretCipher;
import com.agentplatform.modules.tool.domain.model.McpServer;

import java.time.Instant;

@TableName(value = "mcp_servers", autoResultMap = true)
public class McpServerEntity {
    @TableId(type = IdType.INPUT) private String id;
    private String name;
    private String transport;
    private String url;
    private String command;
    private String args;
    @TableField(value = "auth_type") private String authType;
    @TableField(value = "secret_encrypted") private String secretEncrypted;
    private String status;
    @TableField(value = "last_error") private String lastError;
    @TableField(value = "last_connected_at") private Instant lastConnectedAt;
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
