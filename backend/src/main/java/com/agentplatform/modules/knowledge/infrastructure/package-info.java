/**
 * 知识库基础设施层：各技术能力的适配器实现。
 *
 * <ul>
 *   <li>{@code persistence}：MyBatis-Plus 实体/Mapper/仓储适配器与首次启动种子数据；</li>
 *   <li>{@code embedding}：OpenAI 兼容 Embedding 客户端与本地哈希向量回退；</li>
 *   <li>{@code ingestion}：PDF/DOCX/HTML 文本提取与分块；</li>
 *   <li>{@code retrieval}：Milvus 向量库、Elasticsearch 关键词索引及其空实现；</li>
 *   <li>{@code storage}：本地文件系统与腾讯云 COS 文件存储；</li>
 *   <li>{@code web}：网页抓取（含内网地址安全校验）。</li>
 * </ul>
 *
 * <p>该层不向其他模块暴露，仅由本模块 application 层依赖。</p>
 */
package com.agentplatform.modules.knowledge.infrastructure;
