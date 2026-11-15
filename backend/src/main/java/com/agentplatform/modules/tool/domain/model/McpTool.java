package com.agentplatform.modules.tool.domain.model;

/**
 * MCP 工具领域模型。
 *
 * <p>所在层：domain。表示从某个 MCP Server 发现并同步到平台目录中的单个工具。
 * 关键约束：
 * <ul>
 *   <li>{@code toolKey} 由 {@code serverId + "." + 服务端工具名} 组成，是平台内
 *       稳定标识；服务端工具名本身可能不含命名空间，因此必须带服务前缀；</li>
 *   <li>除 {@code enabled} 外的字段均不可变，重新同步时整体替换；</li>
 *   <li>风险级别只用于展示与后续审批策略参考，不在此处做调用拦截。</li>
 * </ul>
 */
public class McpTool {
    /** 工具主键，形如 {@code mt_xxxxxxxxxxxx}。 */
    private final String id;
    /** 所属 MCP 服务 ID。 */
    private final String serverId;
    /** 平台工具键，格式为 {@code <serverId>.<toolName>}。 */
    private final String toolKey;
    /** MCP 服务端声明的工具名称。 */
    private final String name;
    /** 工具描述。 */
    private final String description;
    /** 入参 JSON Schema 字符串。 */
    private final String inputSchema;
    /** 是否启用；停用后不会下发给模型，也不能被调用。 */
    private boolean enabled;
    /** 风险级别：{@code read} 或 {@code write}。 */
    private final String riskLevel;

    /**
     * 构造一个 MCP 工具领域对象。
     *
     * @param id         工具主键
     * @param serverId   所属服务 ID
     * @param toolKey    平台工具键
     * @param name       服务端工具名
     * @param description 工具描述
     * @param inputSchema 入参 JSON Schema
     * @param enabled    是否启用
     * @param riskLevel  风险级别
     */
    public McpTool(String id, String serverId, String toolKey, String name, String description,
                   String inputSchema, boolean enabled, String riskLevel) {
        this.id=id; this.serverId=serverId; this.toolKey=toolKey; this.name=name;
        this.description=description; this.inputSchema=inputSchema; this.enabled=enabled;
        this.riskLevel=riskLevel;
    }

    /**
     * 切换启用状态（启用 ↔ 停用）。
     */
    public void toggle() { this.enabled = !enabled; }

    /** 返回工具主键。 */
    public String id() { return id; }
    /** 返回所属 MCP 服务 ID。 */
    public String serverId() { return serverId; }
    /** 返回平台工具键。 */
    public String toolKey() { return toolKey; }
    /** 返回服务端工具名。 */
    public String name() { return name; }
    /** 返回工具描述。 */
    public String description() { return description; }
    /** 返回入参 JSON Schema 字符串。 */
    public String inputSchema() { return inputSchema; }
    /** 返回是否启用。 */
    public boolean enabled() { return enabled; }
    /** 返回风险级别。 */
    public String riskLevel() { return riskLevel; }
}
