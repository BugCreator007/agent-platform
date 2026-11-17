/**
 * 知识库领域服务与出站端口。
 *
 * <p>Embedding、向量库、关键词索引属于外部技术能力，但它们的抽象是领域/应用依赖的契约；
 * 端口放在 domain 层，具体 HTTP/Milvus/ES 实现留在 infrastructure，避免应用层反向依赖基础设施。</p>
 */
package com.agentplatform.modules.knowledge.domain.service;
