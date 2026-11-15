package com.agentplatform.modules.release.domain.model;

/**
 * 发布记录聚合根（domain 层）。
 * <p>描述某个智能体版本从草稿到全量的发布过程，状态机为：
 * {@code draft --promote--> approve --approve--> canary(5%) --promote--> live(100%)}，
 * 任意阶段可 {@code rollback} 回滚为 {@code rollback(0%)}。状态用字符串表示，
 * 与前端展示字段保持一致。</p>
 * <p>状态不允许的操作会抛出 {@link IllegalStateException}，由应用层转换为
 * 业务异常（错误码 STATE_CONFLICT），因此本类不依赖 common 层。</p>
 */
public class Release {
    /** 发布单唯一标识。 */
    private final String id;
    /** 关联的智能体 id。 */
    private final String agentId;
    /** 智能体名称快照，用于列表展示与评测门禁按名称匹配。 */
    private final String agentName;
    /** 待发布版本号。 */
    private final String version;
    /** 当前发布阶段：draft/approve/canary/live/rollback。 */
    private String stage;
    /** 当前流量比例（0-100，单位百分比）；仅灰度/全量阶段有意义。 */
    private int traffic;
    /** 发布前评测得分，可为 null（未评测的草稿），用于审批门禁。 */
    private final Double evalScore;
    /** 操作人。 */
    private final String owner;
    /** 创建/更新时间展示文本。 */
    private final String time;
    /** 发布说明（变更内容、回滚原因等）。 */
    private final String note;

    /**
     * 从持久化数据恢复发布记录，不校验状态机合法性。
     *
     * @param id        发布单唯一标识
     * @param agentId   关联智能体 id
     * @param agentName 智能体名称快照
     * @param version   版本号
     * @param stage     当前发布阶段
     * @param traffic   当前流量比例（0-100）
     * @param evalScore 评测得分，可为 null
     * @param owner     操作人
     * @param time      时间展示文本
     * @param note      发布说明
     */
    public Release(String id, String agentId, String agentName, String version, String stage,
                   int traffic, Double evalScore, String owner, String time, String note) {
        this.id = id;
        this.agentId = agentId;
        this.agentName = agentName;
        this.version = version;
        this.stage = stage;
        this.traffic = traffic;
        this.evalScore = evalScore;
        this.owner = owner;
        this.time = time;
        this.note = note;
    }

    /**
     * 审批通过：仅允许从 {@code approve} 阶段进入 {@code canary}，初始灰度流量固定为 5%。
     *
     * @throws IllegalStateException 当前阶段不是 {@code approve} 时抛出
     */
    public void approve() {
        if (!"approve".equals(stage)) throw new IllegalStateException("当前状态不能审批");
        this.stage = "canary";
        this.traffic = 5;
    }

    /**
     * 推进发布阶段：
     * <ul>
     *   <li>{@code draft -> approve}（提交审批，等待评测门禁与人工审批）；</li>
     *   <li>{@code canary -> live}（灰度通过，放量至 100%）。</li>
     * </ul>
     *
     * @throws IllegalStateException 当前阶段不是 {@code draft} 或 {@code canary} 时抛出
     */
    public void promote() {
        if ("draft".equals(stage)) { this.stage = "approve"; return; }
        if ("canary".equals(stage)) { this.stage = "live"; this.traffic = 100; return; }
        throw new IllegalStateException("当前状态不能推进");
    }

    /**
     * 回滚发布：阶段置为 {@code rollback}，流量归零。当前实现不校验前置状态，
     * 允许从任意阶段执行回滚。
     */
    public void rollback() { this.stage = "rollback"; this.traffic = 0; }

    /**
     * 调整流量比例，超出 0-100 的输入会被夹紧到边界值。
     *
     * @param traffic 期望流量百分比
     */
    public void updateTraffic(int traffic) { this.traffic = Math.max(0, Math.min(100, traffic)); }

    /** @return 发布单唯一标识 */
    public String id() { return id; }
    /** @return 关联智能体 id */
    public String agentId() { return agentId; }
    /** @return 智能体名称快照 */
    public String agentName() { return agentName; }
    /** @return 待发布版本号 */
    public String version() { return version; }
    /** @return 当前发布阶段 */
    public String stage() { return stage; }
    /** @return 当前流量比例（0-100） */
    public int traffic() { return traffic; }
    /** @return 评测得分，可为 null */
    public Double evalScore() { return evalScore; }
    /** @return 操作人 */
    public String owner() { return owner; }
    /** @return 时间展示文本 */
    public String time() { return time; }
    /** @return 发布说明 */
    public String note() { return note; }
}
