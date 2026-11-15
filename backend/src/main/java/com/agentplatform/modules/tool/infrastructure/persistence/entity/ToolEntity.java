package com.agentplatform.modules.tool.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.tool.domain.model.Tool;

/**
 * {@code tools} 表实体（infrastructure 层）。
 *
 * <p>保存平台工具目录条目。与领域模型不同，工具名称作为表主键（{@code INPUT} 策略，
 * 由应用赋值）；表结构由 Flyway 管理，{@code ddl-auto=validate}。
 * 额外提供名称与 endpoint 的 getter/setter，供持久化适配层按需读取或补充 endpoint。
 */
@TableName(value = "tools", autoResultMap = true)
public class ToolEntity {
    /** 工具名称，同时作为主键。 */
    @TableId(type = IdType.INPUT) private String name;
    /** 工具类型：{@code API}/{@code 内置}/{@code MCP}。 */
    private String type;
    /** 归属团队。 */
    private String owner;
    /** 累计调用次数。 */
    private long calls;
    /** 错误率（百分比）。 */
    @TableField(value = "error_rate") private double errorRate;
    /** 健康状态。 */
    private String health;
    /** 最近检查时间文本。 */
    private String checked;
    /** 是否启用。 */
    private boolean enabled;
    /** 工具描述。 */
    private String description;
    /** HTTP 调用地址。 */
    private String endpoint;

    /**
     * 由领域对象构建数据库实体。
     *
     * @param t 工具领域对象
     * @return 可持久化的实体
     */
    public static ToolEntity from(Tool t) {
        ToolEntity e = new ToolEntity(); e.name=t.name(); e.type=t.type(); e.owner=t.owner(); e.calls=t.calls();
        e.errorRate=t.errorRate(); e.health=t.health(); e.checked=t.checked(); e.enabled=t.enabled(); e.description=t.description(); e.endpoint=t.endpoint(); return e;
    }

    /**
     * 转换为领域对象。
     *
     * @return 工具领域对象
     */
    public Tool toDomain() { return new Tool(name, type, owner, calls, errorRate, health, checked, enabled, description, endpoint); }

    /** 返回工具名称。 */
    public String getName() { return name; }

    /** 返回 HTTP 调用地址。 */
    public String getEndpoint() { return endpoint; }

    /**
     * 设置 HTTP 调用地址。
     *
     * @param endpoint 调用地址
     */
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
}
