/**
 * 知识库限界上下文（Spring Modulith 应用模块）。
 *
 * <p>负责数据集与文档的元数据管理、文件/网页摄取、文档解析与切分、Embedding 计算、
 * 向量库（Milvus 或数据库回退）与关键词索引（Elasticsearch 或数据库回退）的写入与混合检索。</p>
 *
 * <p>包内按 DDD 四层组织：{@code domain}（聚合、领域仓储端口）、{@code application}（用例编排）、
 * {@code infrastructure}（持久化、检索、存储、解析等技术实现）、{@code interfaces}（REST 接口与 DTO）。
 * 跨模块调用只依赖 {@code application.query} / {@code application.service} 这两个命名接口。</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "知识库")
package com.agentplatform.modules.knowledge;
