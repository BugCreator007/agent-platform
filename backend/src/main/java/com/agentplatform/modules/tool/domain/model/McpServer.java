package com.agentplatform.modules.tool.domain.model;

import java.time.Instant;

/**
 * MCP 服务领域模型（聚合根）。
 *
 * <p>所在层：domain。表示一个被平台纳管的 MCP Server，保存传输方式、连接参数、
 * 鉴权信息与连接状态。关键约束：
 * <ul>
 *   <li>{@code authToken} 属于敏感信息，仅允许在领域层与持久化层之间流转，
 *       对外视图（{@code McpServerView}）不得包含；</li>
 *   <li>连接状态只能通过 {@link #connected()}、{@link #failed(String)}、
 *       {@link #disconnected()} 迁移，避免调用方随意拼写状态；</li>
 *   <li>{@link #update} 对空值采取“保持不变”语义，用于部分更新。</li>
 * </ul>
 */
public class McpServer {
    /** 服务主键，形如 {@code mcp_xxxxxxxxxx}。 */
    private final String id;
    /** 服务显示名称。 */
    private String name;
    /** 传输方式：{@code streamable-http}、{@code sse} 或 {@code stdio}。 */
    private String transport;
    /** HTTP/SSE 传输的访问地址；stdio 传输通常为空。 */
    private String url;
    /** stdio 传输的执行命令（可执行文件名）。 */
    private String command;
    /** stdio 传输的启动参数原始字符串。 */
    private String args;
    /** 鉴权类型：{@code bearer} 或 {@code api-key}。 */
    private String authType;
    /** 鉴权令牌/API Key，属于敏感数据，不对外暴露。 */
    private String authToken;
    /** 服务是否启用；停用后不可连接、不可调用其工具。 */
    private boolean enabled;
    /** 连接状态：{@code CONNECTED}、{@code DISCONNECTED}、{@code ERROR}。 */
    private String status;
    /** 最近一次连接失败的错误信息。 */
    private String lastError;
    /** 最近一次成功连接的时间。 */
    private Instant lastConnectedAt;

    /**
     * 构造一个 MCP 服务领域对象。
     *
     * @param id              服务主键
     * @param name            显示名称
     * @param transport       传输方式
     * @param url             HTTP/SSE 地址
     * @param command         stdio 启动命令
     * @param args            stdio 启动参数
     * @param authType        鉴权类型
     * @param authToken       鉴权令牌（敏感）
     * @param enabled         是否启用
     * @param status          初始连接状态
     * @param lastError       最近错误信息
     * @param lastConnectedAt 最近成功连接时间
     */
    public McpServer(String id, String name, String transport, String url, String command, String args,
                     String authType, String authToken, boolean enabled, String status,
                     String lastError, Instant lastConnectedAt) {
        this.id=id; this.name=name; this.transport=transport; this.url=url; this.command=command;
        this.args=args; this.authType=authType; this.authToken=authToken; this.enabled=enabled;
        this.status=status; this.lastError=lastError; this.lastConnectedAt=lastConnectedAt;
    }

    /**
     * 部分更新服务配置：参数为 {@code null} 的字段保持原值，名称为空白时忽略。
     *
     * <p>注意：更新传输方式/地址后调用方应调用 {@link #disconnected()}，
     * 因为既有连接已不再匹配新配置。
     *
     * @param name      新名称，空值或空白则保持原值
     * @param transport 新传输方式，{@code null} 则保持原值
     * @param url       新 HTTP/SSE 地址，{@code null} 则保持原值
     * @param command   新 stdio 命令，{@code null} 则保持原值
     * @param args      新 stdio 参数，{@code null} 则保持原值
     * @param authType  新鉴权类型，{@code null} 则保持原值
     * @param authToken 新鉴权令牌，{@code null} 则保持原值
     * @param enabled   新启用状态，{@code null} 则保持原值
     */
    public void update(String name, String transport, String url, String command, String args,
                       String authType, String authToken, Boolean enabled) {
        if (name != null && !name.isBlank()) this.name = name;
        if (transport != null) this.transport = transport;
        if (url != null) this.url = url;
        if (command != null) this.command = command;
        if (args != null) this.args = args;
        if (authType != null) this.authType = authType;
        if (authToken != null) this.authToken = authToken;
        if (enabled != null) this.enabled = enabled;
    }

    /**
     * 标记连接成功：状态置为 {@code CONNECTED}，清空错误并记录连接时间。
     */
    public void connected() { this.status="CONNECTED"; this.lastError=null; this.lastConnectedAt=Instant.now(); }

    /**
     * 标记连接失败：状态置为 {@code ERROR} 并记录错误信息。
     *
     * @param error 连接失败原因
     */
    public void failed(String error) { this.status="ERROR"; this.lastError=error; }

    /**
     * 标记连接已断开（例如配置变更后），不记录错误。
     */
    public void disconnected() { this.status="DISCONNECTED"; }

    /** 返回服务主键。 */
    public String id() { return id; }
    /** 返回服务显示名称。 */
    public String name() { return name; }
    /** 返回传输方式。 */
    public String transport() { return transport; }
    /** 返回 HTTP/SSE 访问地址。 */
    public String url() { return url; }
    /** 返回 stdio 启动命令。 */
    public String command() { return command; }
    /** 返回 stdio 启动参数原始字符串。 */
    public String args() { return args; }
    /** 返回鉴权类型。 */
    public String authType() { return authType; }
    /** 返回鉴权令牌（敏感，禁止写入日志或接口响应）。 */
    public String authToken() { return authToken; }
    /** 返回是否启用。 */
    public boolean enabled() { return enabled; }
    /** 返回当前连接状态。 */
    public String status() { return status; }
    /** 返回最近一次连接错误信息。 */
    public String lastError() { return lastError; }
    /** 返回最近一次成功连接时间。 */
    public Instant lastConnectedAt() { return lastConnectedAt; }
}
