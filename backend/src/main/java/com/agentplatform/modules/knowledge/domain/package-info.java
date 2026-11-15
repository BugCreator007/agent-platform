/**
 * 知识库领域层：聚合根（数据集、文档）、文档分块值模型与领域仓储端口。
 *
 * <p>该层不依赖 Spring 与具体存储实现；仓储端口由 infrastructure 层的 MyBatis 适配器实现，
 * 以保证业务规则与持久化技术解耦。</p>
 */
package com.agentplatform.modules.knowledge.domain;
