/**
 * 流程编排基础设施层。
 *
 * <p>提供 MyBatis-Plus 持久化实现：{@code persistence/entity} 映射数据库表，
 * {@code persistence/mapper} 定义 MyBatis-Plus Mapper，{@code persistence/repository}
 * 包装 Mapper 并适配 domain 层仓储端口；另含节点 JSON 类型处理器与首次启动种子数据。</p>
 *
 * <p>约束：表结构仅由 Flyway 迁移维护；实体与领域模型之间通过静态 {@code from}/{@code toDomain}
 * 方法转换，不把持久化对象泄漏到 application/domain 层。</p>
 */
package com.agentplatform.modules.workflow.infrastructure;
