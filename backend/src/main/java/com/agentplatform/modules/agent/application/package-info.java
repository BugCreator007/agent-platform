/**
 * 智能体应用层：编排领域聚合与仓储完成用例。
 * <p>{@code query} 子包输出跨模块只读视图，{@code service} 子包提供查询与写用例；
 * 两者均标注 {@code @NamedInterface("api")}，是其他模块唯一允许依赖的入口。</p>
 */
package com.agentplatform.modules.agent.application;
