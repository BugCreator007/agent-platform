/**
 * 流程编排领域层。
 *
 * <p>包含流程定义聚合（{@code Workflow}）、流程实例聚合（{@code WorkflowInstance}）、
 * 节点定义值对象（{@code WorkflowNode}）、节点运行记录（{@code WorkflowNodeRun}）以及仓储端口。</p>
 *
 * <p>约束：状态流转规则集中在聚合方法中（如发布、运行、等待审批/定时器/子流程、成功、失败、取消），
 * 本层不依赖 Spring 或持久化实现，供 application 层与 infrastructure 层共同依赖。</p>
 */
package com.agentplatform.modules.workflow.domain;
