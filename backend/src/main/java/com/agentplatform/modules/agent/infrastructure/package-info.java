/**
 * 智能体基础设施层：MyBatis-Plus 实体/映射/仓储适配、模型绑定与演示种子数据。
 * <p>实现 domain 层仓储端口；种子数据仅在首次启动且表为空时写入，
 * 不作为运行时存储（运行时以 MySQL 为准）。</p>
 */
package com.agentplatform.modules.agent.infrastructure;
