package com.agentplatform.modules.agent.domain.model;

import java.util.List;

/**
 * 智能体聚合根。
 * <p>所在层：domain。封装智能体的配置（模型、提示词、工具、知识库）与生命周期
 * 状态迁移，确保状态只能通过聚合方法变更，而不是由应用层直接赋值。</p>
 * <p>关键约束：{@code id}/{@code code} 创建后不可变；{@code tools}/{@code kb}
 * 在构造与更新时都做不可变拷贝，外部修改原列表不会影响聚合内部状态。</p>
 */
public class Agent {
    /** 智能体唯一标识（形如 {@code a_xxxxxxxxxxxx}），创建后不可变。 */
    private final String id;
    /** 业务编码，全局唯一，用于对外寻址与创建时查重，创建后不可变。 */
    private final String code;
    /** 展示名称，可通过 {@link #update} 修改。 */
    private String name;
    /** 生命周期状态，只能通过 {@link #publish}/{@link #pause}/{@link #resume} 迁移。 */
    private AgentStatus status;
    /** 语义化版本号（如 {@code v25}），草稿默认 {@code v1}。 */
    private String version;
    /** 展示用模型名称（如 Claude Sonnet 5.5）；未绑定时为“未绑定模型”。 */
    private String model;
    /** 模型厂商标识（如 anthropic/deepseek），未绑定时为 null。 */
    private String providerId;
    /** 厂商侧模型标识（如 claude-sonnet-5-5），未绑定时为 null。 */
    private String modelId;
    /** 负责人。 */
    private String owner;
    /** 所属团队。 */
    private String team;
    /** 发布状态展示文案（草稿/灰度 20%/全量/已暂停等），与 {@link #status} 联动更新。 */
    private String release;
    /** 系统提示词，决定智能体的人设与行为约束。 */
    private String prompt;
    /** 允许调用的工具编码列表，不可变列表。 */
    private List<String> tools;
    /** 关联的知识库编码列表，不可变列表。 */
    private List<String> kb;
    /** 需要返回文档引用的知识库编码列表（必须属于 kb）。 */
    private List<String> citationKb;
    /** 累计会话数（运营指标，由运行时统计写入）。 */
    private long sessions;
    /** 成功率百分比（运营指标，98.7 表示 98.7%）。 */
    private double success;
    /** P95 响应耗时展示文本（如 {@code 1.10s}）。 */
    private String p95;
    /** 累计成本展示值（运营指标）。 */
    private double cost;
    /** 最近更新时间展示文本（如“今天 09:42”）。 */
    private String updated;

    /**
     * 全量构造，仅供本类静态工厂使用；{@code tools}/{@code kb} 在此转为不可变列表。
     */
    private Agent(String id, String code, String name, AgentStatus status, String version,
                  String model, String providerId, String modelId, String owner, String team,
                  String release, String prompt, List<String> tools, List<String> kb,
                  long sessions, double success, String p95, double cost, String updated) {
        this(id, code, name, status, version, model, providerId, modelId, owner, team, release,
                prompt, tools, kb, kb, sessions, success, p95, cost, updated);
    }

    private Agent(String id, String code, String name, AgentStatus status, String version,
                  String model, String providerId, String modelId, String owner, String team,
                  String release, String prompt, List<String> tools, List<String> kb,
                  List<String> citationKb, long sessions, double success, String p95, double cost,
                  String updated) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.status = status;
        this.version = version;
        this.model = model;
        this.providerId = providerId;
        this.modelId = modelId;
        this.owner = owner;
        this.team = team;
        this.release = release;
        this.prompt = prompt;
        this.tools = List.copyOf(tools == null ? List.of() : tools);
        this.kb = List.copyOf(kb == null ? List.of() : kb);
        List<String> configuredCitations = citationKb == null ? List.of() : citationKb;
        this.citationKb = List.copyOf(configuredCitations.stream()
                .filter(citation -> this.kb.contains(citation)).distinct().toList());
        this.sessions = sessions;
        this.success = success;
        this.p95 = p95;
        this.cost = cost;
        this.updated = updated;
    }

    /**
     * 从持久化数据恢复聚合（兼容旧调用：不携带模型绑定信息）。
     * <p>该重载等价于把 {@code providerId}/{@code modelId} 置为 null，
     * 保留给旧表结构或历史调用方使用。</p>
     *
     * @param id       智能体唯一标识
     * @param code     业务编码
     * @param name     展示名称
     * @param status   生命周期状态
     * @param version  版本号
     * @param model    模型展示名称
     * @param owner    负责人
     * @param team     所属团队
     * @param release  发布状态展示文案
     * @param prompt   系统提示词
     * @param tools    工具编码列表
     * @param kb       知识库编码列表
     * @param sessions 累计会话数
     * @param success  成功率百分比
     * @param p95      P95 耗时展示文本
     * @param cost     累计成本
     * @param updated  最近更新时间展示文本
     * @return 恢复出的聚合实例
     */
    public static Agent restore(String id, String code, String name, AgentStatus status,
                                String version, String model, String owner, String team,
                                String release, String prompt, List<String> tools, List<String> kb,
                                long sessions, double success, String p95, double cost, String updated) {
        return restore(id, code, name, status, version, model, null, null, owner, team, release,
                prompt, tools, kb, sessions, success, p95, cost, updated);
    }

    /**
     * 从持久化数据完整恢复聚合（含模型绑定信息）。
     * <p>仅供 infrastructure 层映射与种子数据使用，不校验业务规则。</p>
     *
     * @param id         智能体唯一标识
     * @param code       业务编码
     * @param name       展示名称
     * @param status     生命周期状态
     * @param version    版本号
     * @param model      模型展示名称
     * @param providerId 模型厂商标识，可为 null
     * @param modelId    厂商侧模型标识，可为 null
     * @param owner      负责人
     * @param team       所属团队
     * @param release    发布状态展示文案
     * @param prompt     系统提示词
     * @param tools      工具编码列表
     * @param kb         知识库编码列表
     * @param sessions   累计会话数
     * @param success    成功率百分比
     * @param p95        P95 耗时展示文本
     * @param cost       累计成本
     * @param updated    最近更新时间展示文本
     * @return 恢复出的聚合实例
     */
    public static Agent restore(String id, String code, String name, AgentStatus status,
                                String version, String model, String providerId, String modelId,
                                String owner, String team, String release, String prompt,
                                List<String> tools, List<String> kb, long sessions, double success,
                                String p95, double cost, String updated) {
        return new Agent(id, code, name, status, version, model, providerId, modelId, owner, team,
                release, prompt, tools, kb, kb, sessions, success, p95, cost, updated);
    }

    /**
     * 从持久化数据完整恢复聚合（含知识库引用配置）。
     *
     * @param id         智能体唯一标识
     * @param code       业务编码
     * @param name       展示名称
     * @param status     生命周期状态
     * @param version    版本号
     * @param model      模型展示名称
     * @param providerId 模型厂商标识
     * @param modelId    厂商侧模型标识
     * @param owner      负责人
     * @param team       所属团队
     * @param release    发布状态展示文案
     * @param prompt     系统提示词
     * @param tools      工具编码列表
     * @param kb         知识库编码列表
     * @param citationKb 需要返回文档引用的知识库编码列表
     * @param sessions   累计会话数
     * @param success    成功率百分比
     * @param p95        P95 耗时展示文本
     * @param cost       累计成本
     * @param updated    最近更新时间展示文本
     * @return 恢复出的聚合实例
     */
    public static Agent restore(String id, String code, String name, AgentStatus status,
                                String version, String model, String providerId, String modelId,
                                String owner, String team, String release, String prompt,
                                List<String> tools, List<String> kb, List<String> citationKb,
                                long sessions, double success, String p95, double cost, String updated) {
        return new Agent(id, code, name, status, version, model, providerId, modelId, owner, team,
                release, prompt, tools, kb, citationKb, sessions, success, p95, cost, updated);
    }

    /**
     * 创建草稿状态的智能体，并填充演示用默认值（负责人、团队、提示词等）。
     *
     * @param id   应用层生成的唯一标识
     * @param code 业务编码（唯一性由应用层校验）
     * @param name 展示名称
     * @return 新的草稿聚合
     */
    public static Agent draft(String id, String code, String name) {
        return new Agent(id, code, name, AgentStatus.DRAFT, "v1", "未绑定模型",
                null, null, "顾先路", "客服平台", "草稿", "你是企业的智能助手。",
                List.of(), List.of(), List.of(), 0, 0, "—", 0, "刚刚");
    }

    /**
     * 配置基础信息（模型展示名、负责人、团队、提示词），不涉及状态与模型绑定。
     *
     * @param model  模型展示名称
     * @param owner  负责人
     * @param team   所属团队
     * @param prompt 系统提示词
     */
    public void configure(String model, String owner, String team, String prompt) {
        this.model = model;
        this.owner = owner;
        this.team = team;
        this.prompt = prompt;
        this.updated = "刚刚";
    }

    /**
     * 绑定模型厂商与模型标识。
     * <p>{@code modelName} 非空白时才覆盖展示名，避免只传 id 时丢失可读的模型名称。</p>
     *
     * @param providerId 模型厂商标识
     * @param modelId    厂商侧模型标识
     * @param modelName  模型展示名称，可为 null/空白（此时保留原值）
     */
    public void bindModel(String providerId, String modelId, String modelName) {
        this.providerId = providerId;
        this.modelId = modelId;
        if (modelName != null && !modelName.isBlank()) this.model = modelName;
        this.updated = "刚刚";
    }

    /**
     * 局部更新（兼容旧调用：不携带模型绑定信息）。
     * <p>该重载把 {@code providerId}/{@code modelId} 传 null，即保持原模型绑定不变。</p>
     *
     * @param name   新名称，null/空白表示不修改
     * @param model  新模型展示名，null/空白表示不修改
     * @param owner  新负责人，null/空白表示不修改
     * @param team   新团队，null/空白表示不修改
     * @param prompt 新提示词，null 表示不修改
     * @param tools  新工具列表，null 表示不修改；非 null 时整体替换
     * @param kb     新知识库列表，null 表示不修改；非 null 时整体替换
     */
    public void update(String name, String model, String owner, String team,
                       String prompt, List<String> tools, List<String> kb) {
        update(name, model, null, null, owner, team, prompt, tools, kb, null);
    }

    /**
     * 局部更新（兼容旧调用：不携带模型绑定信息，但可更新引用配置）。
     *
     * @param name       新名称
     * @param model      新模型展示名
     * @param owner      新负责人
     * @param team       新团队
     * @param prompt     新提示词
     * @param tools      新工具列表
     * @param kb         新知识库列表
     * @param citationKb 需要返回文档引用的知识库列表
     */
    public void update(String name, String model, String owner, String team,
                       String prompt, List<String> tools, List<String> kb,
                       List<String> citationKb) {
        update(name, model, null, null, owner, team, prompt, tools, kb, citationKb);
    }

    /**
     * 局部更新智能体配置。
     * <p>逐字段判空：名称/模型/负责人/团队为 null 或空白时保持不变；提示词、工具、
     * 知识库为 null 时保持不变（提示词允许传空字符串清空）。工具与知识库非 null 时
     * 整体替换并转不可变列表。</p>
     *
     * @param name       新名称，null/空白表示不修改
     * @param model      新模型展示名，null/空白表示不修改
     * @param providerId 新模型厂商标识，null 表示不修改
     * @param modelId    新厂商模型标识，null 表示不修改
     * @param owner      新负责人，null/空白表示不修改
     * @param team       新团队，null/空白表示不修改
     * @param prompt     新提示词，null 表示不修改
     * @param tools      新工具列表，null 表示不修改；非 null 时整体替换
     * @param kb         新知识库列表，null 表示不修改；非 null 时整体替换
     */
    public void update(String name, String model, String providerId, String modelId,
                       String owner, String team, String prompt, List<String> tools, List<String> kb) {
        update(name, model, providerId, modelId, owner, team, prompt, tools, kb, null);
    }

    /**
     * 局部更新智能体配置（含知识库引用开关）。
     *
     * @param name       新名称，null/空白表示不修改
     * @param model      新模型展示名，null/空白表示不修改
     * @param providerId 新模型厂商标识，null 表示不修改
     * @param modelId    新厂商模型标识，null 表示不修改
     * @param owner      新负责人，null/空白表示不修改
     * @param team       新团队，null/空白表示不修改
     * @param prompt     新提示词，null 表示不修改
     * @param tools      新工具列表，null 表示不修改
     * @param kb         新知识库列表，null 表示不修改
     * @param citationKb 需要返回文档引用的知识库列表，null 表示保留并按新 kb 过滤
     */
    public void update(String name, String model, String providerId, String modelId,
                       String owner, String team, String prompt, List<String> tools,
                       List<String> kb, List<String> citationKb) {
        if (name != null && !name.isBlank()) this.name = name;
        if (model != null && !model.isBlank()) this.model = model;
        if (providerId != null) this.providerId = providerId;
        if (modelId != null) this.modelId = modelId;
        if (owner != null && !owner.isBlank()) this.owner = owner;
        if (team != null && !team.isBlank()) this.team = team;
        if (prompt != null) this.prompt = prompt;
        if (tools != null) this.tools = List.copyOf(tools);
        if (kb != null) this.kb = List.copyOf(kb);
        // 引用开关必须始终是 kb 的子集，避免知识库解绑后残留引用配置。
        List<String> nextCitations = citationKb == null ? this.citationKb : citationKb;
        this.citationKb = List.copyOf(nextCitations.stream()
                .filter(this.kb::contains).distinct().toList());
        this.updated = "刚刚";
    }

    /**
     * 发布智能体：状态置为运行中，发布文案置为“全量”。
     * <p>注意：当前实现不做前置状态校验，由应用层按业务场景调用。</p>
     */
    public void publish() {
        this.status = AgentStatus.RUNNING;
        this.release = "全量";
        this.updated = "刚刚";
    }

    /**
     * 暂停智能体：状态置为暂停，发布文案置为“已暂停”。暂停后用户门户不再展示或允许对话。
     */
    public void pause() {
        this.status = AgentStatus.PAUSED;
        this.release = "已暂停";
        this.updated = "刚刚";
    }

    /**
     * 恢复智能体：状态回到运行中，发布文案置为“全量”。
     */
    public void resume() {
        this.status = AgentStatus.RUNNING;
        this.release = "全量";
        this.updated = "刚刚";
    }

    /** @return 智能体唯一标识 */
    public String id() { return id; }
    /** @return 业务编码 */
    public String code() { return code; }
    /** @return 展示名称 */
    public String name() { return name; }
    /** @return 当前生命周期状态 */
    public AgentStatus status() { return status; }
    /** @return 语义化版本号 */
    public String version() { return version; }
    /** @return 展示用模型名称 */
    public String model() { return model; }
    /** @return 模型厂商标识，未绑定时为 null */
    public String providerId() { return providerId; }
    /** @return 厂商侧模型标识，未绑定时为 null */
    public String modelId() { return modelId; }
    /** @return 负责人 */
    public String owner() { return owner; }
    /** @return 所属团队 */
    public String team() { return team; }
    /** @return 发布状态展示文案 */
    public String release() { return release; }
    /** @return 系统提示词 */
    public String prompt() { return prompt; }
    /** @return 工具编码列表（不可变） */
    public List<String> tools() { return tools; }
    /** @return 知识库编码列表（不可变） */
    public List<String> kb() { return kb; }
    /** @return 需要返回文档引用的知识库编码列表（不可变） */
    public List<String> citationKb() { return citationKb; }
    /** @return 累计会话数 */
    public long sessions() { return sessions; }
    /** @return 成功率百分比 */
    public double success() { return success; }
    /** @return P95 耗时展示文本 */
    public String p95() { return p95; }
    /** @return 累计成本 */
    public double cost() { return cost; }
    /** @return 最近更新时间展示文本 */
    public String updated() { return updated; }
}
