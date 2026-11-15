package com.agentplatform.modules.workflow.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程实例的可持久化执行游标。
 *
 * <p>所属层：application 应用层（作为实例状态 JSON 的数据结构）。该对象会被序列化到
 * 实例的 {@code state_json} 字段，在每次波次执行后持久化，并在恢复时反序列化。
 * 因此字段使用 public 可变集合并由 Jackson 直接读写，保持结构简单、向后兼容。</p>
 *
 * <p>Persisted execution state. It is intentionally small: completed/skipped nodes,
 * node outputs, selected condition edges and the compensation stack.</p>
 *
 * <p>关键约束：执行引擎以“已完成/已跳过节点 + 失效边”判定节点就绪；
 * 补偿栈按完成顺序入栈、失败时逆序执行；等待节点与子流程实例 ID 用于恢复定位。</p>
 */
public class WorkflowRunState {
    /** 已成功完成的节点 key 集合，决定后继节点是否就绪。 */
    public Set<String> completed = new LinkedHashSet<>();
    /** 被判定为不可达而跳过的节点 key 集合。 */
    public Set<String> skipped = new LinkedHashSet<>();
    /** 已完成节点输出（nodeKey → 输出 JSON），供表达式与下游节点使用。 */
    public Map<String, String> outputs = new LinkedHashMap<>();
    /** 条件分支中未选中的边（{@code from->to}），用于剪枝，避免未选路径被误判为就绪。 */
    public Set<String> inactiveEdges = new LinkedHashSet<>();
    /** 补偿栈：按节点完成顺序入栈，流程失败时逆序执行已注册的补偿工具。 */
    public List<CompensationRecord> compensations = new ArrayList<>();
    /** 当前等待中的节点 key（WAITING_* 状态时有效）。 */
    public String waitingNode;
    /** 等待中的子流程实例 ID（SUBFLOW 节点时有效）。 */
    public String subflowInstanceId;

    /**
     * 单条补偿记录：当流程失败时，用该工具与原始输入回滚已完成的副作用。
     */
    public static class CompensationRecord {
        /** 注册补偿的节点 key。 */
        public String nodeKey;
        /** 补偿工具名（TOOL 节点配置的 compensation）。 */
        public String tool;
        /** 补偿工具入参，通常复用该节点的执行输入。 */
        public String inputJson;

        /** Jackson 反序列化需要的无参构造器。 */
        public CompensationRecord() {
        }

        /**
         * 创建补偿记录。
         *
         * @param nodeKey   注册补偿的节点 key
         * @param tool      补偿工具名
         * @param inputJson 补偿工具入参
         */
        public CompensationRecord(String nodeKey, String tool, String inputJson) {
            this.nodeKey = nodeKey;
            this.tool = tool;
            this.inputJson = inputJson;
        }
    }
}
