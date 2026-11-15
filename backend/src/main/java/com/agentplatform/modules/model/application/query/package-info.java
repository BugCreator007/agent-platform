/**
 * 模型模块对外查询契约。
 *
 * <p>该包通过 {@code @NamedInterface("api")} 暴露给其他模块（如 Agent 运行时、
 * 运营概览），提供模型路由候选 {@link com.agentplatform.modules.model.application.query.ModelRoute}
 * 与厂商探测结果等只读数据，避免跨模块直接访问 domain/infrastructure。</p>
 */
@org.springframework.modulith.NamedInterface("api")
package com.agentplatform.modules.model.application.query;
