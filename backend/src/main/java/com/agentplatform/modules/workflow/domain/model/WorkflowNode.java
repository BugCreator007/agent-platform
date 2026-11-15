package com.agentplatform.modules.workflow.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程节点定义值对象（record）。
 *
 * <p>所属层：domain 领域层。描述流程中的一个节点，执行引擎据此选择节点类型并读取配置。
 * 节点类型的语义（大小写不敏感，空值按 TOOL 处理）：</p>
 * <ul>
 *   <li>{@code START/TRIGGER/END/JOIN}：控制节点，直接产出成功输出；</li>
 *   <li>{@code KNOWLEDGE}：知识库检索；{@code MODEL}：大模型调用；{@code TOOL}：工具调用；</li>
 *   <li>{@code CONDITION}：SpEL 条件分支，按 {@code trueNext/falseNext} 选边；</li>
 *   <li>{@code APPROVAL}：等待人工审批；{@code TIMER}：等待定时器；{@code SUBFLOW}：启动子流程；</li>
 *   <li>{@code NOTIFY}：企业微信或 Webhook 通知。</li>
 * </ul>
 *
 * <p>约束：{@code config} 在构造时做防御性不可变拷贝，避免聚合外部修改；{@code key} 在单个流程内唯一。</p>
 *
 * @param key    节点唯一标识，边与执行游标都引用该值
 * @param type   节点类型（见类注释）
 * @param title  节点展示名称
 * @param meta   节点摘要/元信息，可作为模型提示词或工具名的回退来源
 * @param config 节点配置，如 {@code next/trueNext/falseNext/expression/tool/compensation/delaySeconds}
 */
public record WorkflowNode(String key, String type, String title, String meta, Map<String, Object> config) {
    /**
     * 紧凑构造器：空配置归一为空 Map，非空配置复制为不可变 LinkedHashMap（保留配置顺序）。
     */
    public WorkflowNode {
        config = config == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(config));
    }

    /**
     * 无配置的便捷构造器。
     *
     * @param key   节点唯一标识
     * @param type  节点类型
     * @param title 节点展示名称
     * @param meta  节点摘要/元信息
     */
    public WorkflowNode(String key, String type, String title, String meta) {
        this(key, type, title, meta, Map.of());
    }
}
