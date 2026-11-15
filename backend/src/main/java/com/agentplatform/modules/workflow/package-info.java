/**
 * 流程编排限界上下文（Workflow Bounded Context）。
 *
 * <p>职责：提供可视化流程定义的增删改查与发布，以及流程实例的执行、审批恢复、定时器恢复、
 * 条件分支、并行节点、子流程与失败补偿。模块内部按 DDD 四层组织：</p>
 * <ul>
 *   <li>{@code domain}：流程定义、流程实例、节点运行记录与仓储端口；</li>
 *   <li>{@code application}：流程应用服务、执行引擎、表达式求值、通知与调度器；</li>
 *   <li>{@code infrastructure}：MyBatis-Plus 持久化适配器与演示种子数据；</li>
 *   <li>{@code interfaces.rest}：对管理后台暴露的流程与实例 REST 接口。</li>
 * </ul>
 *
 * <p>关键约束：执行状态必须持久化，审批/定时器/子流程等待通过轮询调度恢复；
 * 跨模块调用只允许依赖其他模块的 {@code application} 层服务。</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "流程编排")
package com.agentplatform.modules.workflow;
