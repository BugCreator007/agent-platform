/**
 * 智能体领域层：聚合根 {@code Agent}、状态枚举与仓储端口。
 * <p>只包含业务规则与抽象，不依赖 Spring Web、MyBatis 等基础设施实现；
 * 状态流转规则直接定义在聚合根上，避免泄漏到应用层或控制器。</p>
 */
package com.agentplatform.modules.agent.domain;
