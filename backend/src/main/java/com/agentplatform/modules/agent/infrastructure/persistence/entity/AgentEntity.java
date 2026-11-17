package com.agentplatform.modules.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentplatform.common.persistence.StringListTypeHandler;

import com.agentplatform.modules.agent.domain.model.Agent;
import com.agentplatform.modules.agent.domain.model.AgentStatus;

import java.util.List;

/**
 * 智能体数据库实体（infrastructure/persistence 层），映射 {@code agents} 表。
 * <p>{@code autoResultMap = true} 使自定义 {@link StringListTypeHandler} 在查询结果
 * 映射时生效；列表列以单元分隔符拼接的 TEXT 形式存储。实体只负责与数据库互转，
 * 业务规则在领域聚合 {@link Agent} 中。</p>
 */
@TableName(value = "agents", autoResultMap = true)
public class AgentEntity {
    /** 主键，由应用层生成（前缀 {@code a_}），不使用数据库自增。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 业务编码，唯一索引列。 */
    private String code;
    /** 展示名称。 */
    private String name;
    /** 生命周期状态，存枚举名的小写形式。 */
    private String status;
    /** 版本号。 */
    private String version;
    /** 模型展示名称。 */
    private String model;
    /** 模型厂商标识，列名 {@code provider_id}。 */
    @TableField(value = "provider_id") private String providerId;
    /** 厂商侧模型标识，列名 {@code model_id}。 */
    @TableField(value = "model_id") private String modelId;
    /** 负责人。 */
    private String owner;
    /** 所属团队。 */
    private String team;
    /** 发布状态展示文案；领域名称为 release，列名 {@code release_status}。 */
    @TableField(value = "release_status") private String releaseStatus;
    /** 系统提示词。 */
    private String prompt;
    /** 工具编码列表，经 {@link StringListTypeHandler} 与 TEXT 列互转。 */
    @TableField(typeHandler = StringListTypeHandler.class) private List<String> tools;
    /** 知识库编码列表，经 {@link StringListTypeHandler} 与 TEXT 列互转。 */
    @TableField(typeHandler = StringListTypeHandler.class) private List<String> kb;
    /** 需要返回文档引用的知识库编码列表。 */
    @TableField(value = "citation_kb", typeHandler = StringListTypeHandler.class) private List<String> citationKb;
    /** 回答模式：HYBRID 混合 / STRICT 严格引用。 */
    @TableField(value = "answer_mode") private String answerMode;
    /** 累计会话数。 */
    private long sessions;
    /** 成功率百分比。 */
    private double success;
    /** P95 耗时展示文本。 */
    private String p95;
    /** 累计成本。 */
    private double cost;
    /** 最近更新时间展示文本。 */
    private String updated;

    /**
     * 领域聚合转数据库实体。
     *
     * @param a 领域聚合
     * @return 对应的持久化实体
     */
    public static AgentEntity from(Agent a) {
        AgentEntity e = new AgentEntity();
        e.id = a.id(); e.code = a.code(); e.name = a.name(); e.status = a.status().name().toLowerCase();
        e.version = a.version(); e.model = a.model(); e.providerId = a.providerId(); e.modelId = a.modelId();
        e.owner = a.owner(); e.team = a.team();
        e.releaseStatus = a.release(); e.prompt = a.prompt(); e.tools = a.tools(); e.kb = a.kb(); e.citationKb = a.citationKb();
        e.answerMode = a.answerMode();
        e.sessions = a.sessions(); e.success = a.success(); e.p95 = a.p95(); e.cost = a.cost(); e.updated = a.updated();
        return e;
    }

    /**
     * 数据库实体转领域聚合。
     * <p>状态字符串转大写后解析枚举；列表列查询为 null 时降级为空列表，
     * 兼容历史数据或手动插入的记录。</p>
     *
     * @return 领域聚合
     */
    public Agent toDomain() {
        return Agent.restore(id, code, name, AgentStatus.valueOf(status.toUpperCase()), version, model,
                providerId, modelId, owner, team, releaseStatus, prompt, tools == null ? List.of() : tools,
                kb == null ? List.of() : kb, citationKb == null ? List.of() : citationKb,
                answerMode, sessions, success, p95, cost, updated);
    }

    /** @return 主键 */
    public String getId() { return id; }
    /** @return 业务编码 */
    public String getCode() { return code; }
    /** @return 展示名称 */
    public String getName() { return name; }
}
