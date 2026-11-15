package com.agentplatform.modules.tool.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.framework.security.SecretCipher;
import com.agentplatform.modules.tool.domain.model.McpServer;

import java.time.Instant;

/**
 * {@code mcp_servers} 表实体（infrastructure 层）。
 *
 * <p>负责 MCP 服务领域对象与数据库行的双向映射。关键约束：鉴权令牌不落明文，
 * 写入前经 {@link SecretCipher} 加密为 {@code secret_encrypted}，读取时解密；
 * 表结构由 Flyway 管理，{@code ddl-auto=validate}。
 */
@TableName(value = "mcp_servers", autoResultMap = true)
public class McpServerEntity {
    /** 主键，由应用生成（{@code INPUT} 策略）。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 服务显示名称。 */
    private String name;
    /** 传输方式：{@code streamable-http}/{@code sse}/{@code stdio}。 */
    private String transport;
    /** HTTP/SSE 访问地址。 */
    private String url;
    /** stdio 启动命令。 */
    private String command;
    /** stdio 启动参数原始字符串。 */
    private String args;
    /** 鉴权类型：{@code bearer}/{@code api-key}。 */
    @TableField(value = "auth_type") private String authType;
    /** 加密后的鉴权令牌，禁止以明文落库或输出。 */
    @TableField(value = "secret_encrypted") private String secretEncrypted;
    /** 连接状态。 */
    private String status;
    /** 最近一次连接错误信息。 */
    @TableField(value = "last_error") private String lastError;
    /** 最近一次成功连接时间。 */
    @TableField(value = "last_connected_at") private Instant lastConnectedAt;
    /** 是否启用。 */
    private boolean enabled;

    /**
     * 由领域对象构建数据库实体（令牌加密）。
     *
     * @param s      服务领域对象
     * @param cipher 密钥加解密器
     * @return 可持久化的实体
     */
    public static McpServerEntity from(McpServer s, SecretCipher cipher) {
        McpServerEntity e = new McpServerEntity();
        e.id=s.id(); e.name=s.name(); e.transport=s.transport(); e.url=s.url(); e.command=s.command();
        e.args=s.args(); e.authType=s.authType(); e.secretEncrypted=cipher.encrypt(s.authToken());
        e.status=s.status(); e.lastError=s.lastError(); e.lastConnectedAt=s.lastConnectedAt(); e.enabled=s.enabled();
        return e;
    }

    /**
     * 转换为领域对象（令牌解密）。
     *
     * @param cipher 密钥加解密器
     * @return 服务领域对象
     */
    public McpServer toDomain(SecretCipher cipher) {
        return new McpServer(id, name, transport, url, command, args, authType, cipher.decrypt(secretEncrypted),
                enabled, status, lastError, lastConnectedAt);
    }
}
