/**
 * 工具模块对外查询模型（只读视图与调用结果）。
 *
 * <p>这些 record 是跨模块可见的 {@code api} 命名接口：其他模块（如 Agent 运行时）
 * 只依赖此处定义的视图/绑定/结果类型，而不接触工具模块的领域实体或基础设施。
 */
@org.springframework.modulith.NamedInterface("api")
package com.agentplatform.modules.tool.application.query;
