/**
 * 智能体只读查询视图（跨模块 API）。
 * <p>以 {@code @NamedInterface("api")} 暴露，其他模块只能读取这里的 DTO，
 * 不得依赖领域实体或基础设施实体。</p>
 */
@org.springframework.modulith.NamedInterface("api")
package com.agentplatform.modules.agent.application.query;
