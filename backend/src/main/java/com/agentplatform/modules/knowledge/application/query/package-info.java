/**
 * 知识库查询侧应用服务与只读视图模型。
 *
 * <p>对外暴露为 Modulith 命名接口 {@code api}，其他模块（如 Agent 运行时组装 RAG 上下文）
 * 只能通过本包使用检索能力与数据集摘要，不得直接依赖 domain 或 infrastructure 层。</p>
 */
@org.springframework.modulith.NamedInterface("api")
package com.agentplatform.modules.knowledge.application.query;
