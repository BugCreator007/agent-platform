package com.agentplatform.modules.workflow.domain.model;

import java.util.List;

/**
 * 流程定义聚合根。
 *
 * <p>所属层：domain 领域层。描述一条可编排的流程：基础信息、发布状态、触发方式、
 * 运行统计以及有序节点列表。节点顺序既用于画布展示，也作为执行引擎推断默认边
 * （未显式配置 {@code next} 时连接下一个节点）的依据。</p>
 *
 * <p>约束：{@link #publish()} 与 {@link #recordRun(boolean)} 是仅有的可变状态入口，
 * 状态变更通过应用层调用后由仓储持久化；{@code nodes} 在构造时做不可变拷贝。</p>
 */
public class Workflow {
    /** 流程 ID，创建时生成，形如 {@code wf-xxxxxxxx}。 */
    private final String id;
    /** 流程名称。 */
    private final String name;
    /** 发布状态：{@code draft} 草稿、{@code published} 已发布、{@code paused} 已暂停。 */
    private String status;
    /** 触发方式描述，如“手动触发”“定时任务 · 每天 09:00”，调度器按关键字识别定时流程。 */
    private final String trigger;
    /** 累计运行次数。 */
    private long runs;
    /** 历史成功率（百分比，保留一位小数）。 */
    private double success;
    /** 负责人。 */
    private final String owner;
    /** 版本号，形如 {@code v1}，每次发布自增。 */
    private String version;
    /** 最近更新时间的人类可读文本。 */
    private String updated;
    /** 有序节点列表，执行引擎按该顺序构建图及默认边。 */
    private final List<WorkflowNode> nodes;

    /**
     * 创建流程定义。
     *
     * @param id      流程 ID
     * @param name    流程名称
     * @param status  发布状态
     * @param trigger 触发方式描述
     * @param runs    累计运行次数
     * @param success 历史成功率（百分比）
     * @param owner   负责人
     * @param version 版本号
     * @param updated 最近更新时间文本
     * @param nodes   有序节点列表，不能为 {@code null}
     */
    public Workflow(String id, String name, String status, String trigger, long runs, double success,
                    String owner, String version, String updated, List<WorkflowNode> nodes) {
        this.id = id; this.name = name; this.status = status; this.trigger = trigger;
        this.runs = runs; this.success = success; this.owner = owner; this.version = version;
        this.updated = updated; this.nodes = List.copyOf(nodes);
    }

    /**
     * 发布流程：状态置为 {@code published}，版本号自增并刷新更新时间。
     *
     * <p>版本号中的非数字字符会被忽略，仅对数字部分加一；因此无法解析的版本号会抛异常。</p>
     *
     * @throws NumberFormatException 当前版本号不含可解析的数字时抛出
     */
    public void publish() {
        this.status = "published";
        // 版本号形如 v12，只保留数字部分做自增，再拼回 v 前缀。
        int current = Integer.parseInt(version.replaceAll("\\D", ""));
        this.version = "v" + (current + 1);
        this.updated = "刚刚";
    }

    /**
     * 记录一次运行结果并更新成功率。
     *
     * <p>成功率按累计运行次数做滚动平均：本次成功记 100 分、失败记 0 分，
     * 四舍五入保留一位小数。</p>
     *
     * @param ok 本次运行是否成功
     */
    public void recordRun(boolean ok) {
        this.runs++;
        this.success = Math.round(((this.success * (this.runs - 1)) + (ok ? 100 : 0)) / this.runs * 10.0) / 10.0;
        this.updated = "刚刚";
    }

    /** @return 流程 ID */
    public String id() { return id; }
    /** @return 流程名称 */
    public String name() { return name; }
    /** @return 发布状态（draft/published/paused） */
    public String status() { return status; }
    /** @return 触发方式描述 */
    public String trigger() { return trigger; }
    /** @return 累计运行次数 */
    public long runs() { return runs; }
    /** @return 历史成功率（百分比） */
    public double success() { return success; }
    /** @return 负责人 */
    public String owner() { return owner; }
    /** @return 版本号 */
    public String version() { return version; }
    /** @return 最近更新时间文本 */
    public String updated() { return updated; }
    /** @return 有序节点列表（不可变） */
    public List<WorkflowNode> nodes() { return nodes; }
}
