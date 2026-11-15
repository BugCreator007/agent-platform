package com.agentplatform.modules.tool.domain.model;

/**
 * 平台工具目录条目（领域模型）。
 *
 * <p>所在层：domain。描述一个可在运行时被调用的工具，既包括内置/HTTP/MCP 等类型，
 * 也保存调用量、错误率与健康状态等运营指标。关键约束：
 * <ul>
 *   <li>{@code name} 是工具的唯一标识，同时作为持久化主键；</li>
 *   <li>{@code enabled=false} 时执行入口必须快速失败；</li>
 *   <li>{@link #check()} 仅做轻量健康推断，不代表真实探活。</li>
 * </ul>
 */
public class Tool {
    /** 工具唯一名称，同时作为目录主键（如 {@code weather.query}）。 */
    private final String name;
    /** 工具类型：{@code API}、{@code 内置}、{@code MCP} 等。 */
    private final String type;
    /** 工具归属团队。 */
    private final String owner;
    /** 累计调用次数。 */
    private final long calls;
    /** 错误率（百分比数值，如 {@code 1.2} 表示 1.2%）。 */
    private final double errorRate;
    /** 健康状态：{@code healthy} 或 {@code degraded}。 */
    private String health;
    /** 最近一次健康检查时间的展示文本（如“刚刚”“2 分钟前”）。 */
    private String checked;
    /** 是否启用。 */
    private boolean enabled;
    /** 工具描述。 */
    private final String description;
    /** HTTP 工具的实际调用地址；为空时走模拟实现或 MCP 分支。 */
    private final String endpoint;

    /**
     * 创建不带 endpoint 的工具（兼容旧构造方式）。
     *
     * @param name        工具名称
     * @param type        工具类型
     * @param owner       归属团队
     * @param calls       累计调用次数
     * @param errorRate   错误率
     * @param health      健康状态
     * @param checked     最近检查时间文本
     * @param enabled     是否启用
     * @param description 工具描述
     */
    public Tool(String name, String type, String owner, long calls, double errorRate,
                String health, String checked, boolean enabled, String description) {
        this(name, type, owner, calls, errorRate, health, checked, enabled, description, null);
    }

    /**
     * 创建完整工具对象。
     *
     * @param name        工具名称
     * @param type        工具类型
     * @param owner       归属团队
     * @param calls       累计调用次数
     * @param errorRate   错误率
     * @param health      健康状态
     * @param checked     最近检查时间文本
     * @param enabled     是否启用
     * @param description 工具描述
     * @param endpoint    HTTP 调用地址，可为 {@code null}
     */
    public Tool(String name, String type, String owner, long calls, double errorRate,
                String health, String checked, boolean enabled, String description, String endpoint) {
        this.name = name; this.type = type; this.owner = owner; this.calls = calls;
        this.errorRate = errorRate; this.health = health; this.checked = checked;
        this.enabled = enabled; this.description = description; this.endpoint = endpoint;
    }

    /**
     * 切换启用状态（启用 ↔ 停用）。
     */
    public void toggle() { this.enabled = !this.enabled; }

    /**
     * 执行一次轻量健康检查：刷新检查时间，并按错误率阈值推断健康状态。
     *
     * <p>错误率高于 1% 视为 {@code degraded}，否则为 {@code healthy}。
     */
    public void check() { this.checked = "刚刚"; this.health = errorRate > 1 ? "degraded" : "healthy"; }

    /** 返回工具名称。 */
    public String name() { return name; }
    /** 返回工具类型。 */
    public String type() { return type; }
    /** 返回归属团队。 */
    public String owner() { return owner; }
    /** 返回累计调用次数。 */
    public long calls() { return calls; }
    /** 返回错误率。 */
    public double errorRate() { return errorRate; }
    /** 返回健康状态。 */
    public String health() { return health; }
    /** 返回最近检查时间文本。 */
    public String checked() { return checked; }
    /** 返回是否启用。 */
    public boolean enabled() { return enabled; }
    /** 返回工具描述。 */
    public String description() { return description; }
    /** 返回 HTTP 调用地址。 */
    public String endpoint() { return endpoint; }
}
