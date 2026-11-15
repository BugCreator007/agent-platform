package com.agentplatform.modules.workflow.domain.model;

import java.time.Instant;

/**
 * 节点运行记录实体。
 *
 * <p>所属层：domain 领域层。每次节点执行（包括补偿动作）生成一条记录，用于实例详情展示、
 * 崩溃后执行状态回填（{@code hydrate}）以及等待态定位（按 instanceId + nodeKey + status 查找）。</p>
 *
 * <p>约束：正常执行仅成功/失败/跳过会写入结束时间；等待态（审批、定时器、子流程）保留
 * {@code finishedAt} 为空，恢复时由调度器将其置为成功。</p>
 */
public class WorkflowNodeRun {
    /** 运行记录 ID，形如 {@code noderun_xxxxxxxxxx}。 */
    private final String id;
    /** 所属流程实例 ID。 */
    private final String instanceId;
    /** 对应流程节点 key。 */
    private final String nodeKey;
    /** 节点类型，补偿记录使用 {@code COMPENSATION}。 */
    private final String nodeType;
    /** 节点展示名称（补偿记录为“补偿 · 原节点 key”）。 */
    private final String title;
    /** 运行状态：RUNNING/SUCCESS/FAILED/SKIPPED/WAITING_APPROVAL/WAITING_TIMER/WAITING_SUBFLOW。 */
    private String status;
    /** 执行输入 JSON。 */
    private final String inputJson;
    /** 执行输出 JSON。 */
    private String outputJson;
    /** 尝试次数（当前实现创建时固定为 1）。 */
    private final int attempt;
    /** 开始时间。 */
    private final Instant startedAt;
    /** 结束时间，等待态或运行中为空。 */
    private Instant finishedAt;
    /** 失败原因。 */
    private String error;

    /**
     * 创建节点运行记录。
     *
     * @param id         运行记录 ID
     * @param instanceId 流程实例 ID
     * @param nodeKey    节点 key
     * @param nodeType   节点类型
     * @param title      节点展示名称
     * @param status     初始状态
     * @param inputJson  执行输入 JSON
     * @param outputJson 执行输出 JSON
     * @param attempt    尝试次数
     * @param startedAt  开始时间
     * @param finishedAt 结束时间
     * @param error      失败原因
     */
    public WorkflowNodeRun(String id, String instanceId, String nodeKey, String nodeType, String title,
                           String status, String inputJson, String outputJson, int attempt,
                           Instant startedAt, Instant finishedAt, String error) {
        this.id=id; this.instanceId=instanceId; this.nodeKey=nodeKey; this.nodeType=nodeType; this.title=title;
        this.status=status; this.inputJson=inputJson; this.outputJson=outputJson; this.attempt=attempt;
        this.startedAt=startedAt; this.finishedAt=finishedAt; this.error=error;
    }

    /**
     * 标记节点成功并写入输出。
     *
     * @param output 输出 JSON
     */
    public void succeed(String output) { this.status="SUCCESS"; this.outputJson=output; this.finishedAt=Instant.now(); }

    /** 标记节点进入待审批等待态（不写结束时间，恢复后由审批流程置为成功）。 */
    public void waitApproval() { this.status="WAITING_APPROVAL"; }

    /** 标记节点进入定时器等待态（不写结束时间，由调度器到期恢复）。 */
    public void waitTimer() { this.status="WAITING_TIMER"; }

    /** 标记节点进入子流程等待态（不写结束时间，由调度器在子流程结束后恢复）。 */
    public void waitSubflow() { this.status="WAITING_SUBFLOW"; }

    /**
     * 标记节点被跳过。
     *
     * @param reason 跳过原因 JSON，写入输出字段便于排查
     */
    public void skip(String reason) { this.status="SKIPPED"; this.outputJson=reason; this.finishedAt=Instant.now(); }

    /**
     * 标记节点失败。
     *
     * @param error 失败原因
     */
    public void fail(String error) { this.status="FAILED"; this.error=error; this.finishedAt=Instant.now(); }

    /** @return 运行记录 ID */
    public String id() { return id; }
    /** @return 流程实例 ID */
    public String instanceId() { return instanceId; }
    /** @return 节点 key */
    public String nodeKey() { return nodeKey; }
    /** @return 节点类型 */
    public String nodeType() { return nodeType; }
    /** @return 节点展示名称 */
    public String title() { return title; }
    /** @return 运行状态 */
    public String status() { return status; }
    /** @return 执行输入 JSON */
    public String inputJson() { return inputJson; }
    /** @return 执行输出 JSON */
    public String outputJson() { return outputJson; }
    /** @return 尝试次数 */
    public int attempt() { return attempt; }
    /** @return 开始时间 */
    public Instant startedAt() { return startedAt; }
    /** @return 结束时间 */
    public Instant finishedAt() { return finishedAt; }
    /** @return 失败原因 */
    public String error() { return error; }
}
