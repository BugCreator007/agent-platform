/**
 * 用户门户限界上下文（portal）。
 * <p>面向业务用户的智能体目录与 SSE 流式对话入口（{@code /api/v1/user/*}）。
 * 该模块不拥有领域数据，只复用 agent 模块的查询/应用服务与 chat 模块的运行时服务，
 * 对外只暴露已上线（running/grayscale）的智能体。</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "用户门户")
package com.agentplatform.modules.portal;
