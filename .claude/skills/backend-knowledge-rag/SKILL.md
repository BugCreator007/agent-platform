---
name: backend-knowledge-rag
description: 修改知识库上传、网页/Wiki 摄取、文档解析、切分、Embedding、Milvus、Elasticsearch 或混合检索时使用。
---

# 知识库与 RAG 开发

## 先读

- `modules/knowledge/application/ingestion/DocumentIngestionService.java`
- `modules/knowledge/application/query/KnowledgeRetrievalService.java`
- `modules/knowledge/application/service/KnowledgeApplicationService.java`
- `infrastructure/{ingestion,embedding,retrieval,storage,web}/`
- `backend/src/main/resources/db/migration/V4__document_source.sql`
- 前端 `pages/Knowledge.tsx`

## 摄取链路

```text
文件 / 网页
 → FileStorage（local 或 COS）
 → Document（status=indexing, source_type, source_url）
 → DocumentTextExtractor（PDF/DOCX/MD/TXT/HTML）
 → TextChunker
 → EmbeddingClient.embed
 → document_chunks（embedding_json）
 → Milvus upsert
 → Elasticsearch index
 → Document.status=ready
```

## 新增解析器

1. 在 `DocumentTextExtractor` 增加扩展名分支；未知格式回退 UTF-8 文本。
2. PDF 用 PDFBox，DOCX 用 POI；失败抛出明确错误。
3. 解析失败时文档状态置 `failed`，不要删除原文件。
4. 大文件必须限制大小（当前 50MB）和正文长度。

## 网页 / Wiki

- `WebPageFetcher` 使用 Jsoup，支持可选 Cookie。
- 默认拒绝 localhost、内网、链路本地和组播地址，防止 SSRF。
- 内部 Wiki 需要 `ALLOW_PRIVATE_WEB=true`，生产建议通过服务账号和网关访问。
- 仅执行 HTTP 抓取，不执行 JavaScript；SPA Wiki 需要服务端渲染或浏览器渲染方案。
- 记录 `source_type=WEB` 和 `source_url`，前端展示来源。

## 检索

`KnowledgeRetrievalService`：

- 查询向量由 `EmbeddingClient` 生成。
- 优先 Milvus `search`，不可用时回退数据库余弦相似度。
- 关键词优先 ES，不可用时回退数据库二元组匹配。
- 混合模式用 RRF 融合。
- 数据集 `topK`、`threshold`、`retrievalMode`、`rerank` 必须生效。

## 向量维度

- `EMBEDDING_DIMENSION` 必须与 Milvus collection 的 `dim` 一致。
- 修改维度后需要删除并重建 `agent_chunks` collection，再重建索引。
- `EMBEDDING_API_KEY` 为空时使用 `HashEmbeddingClient`，语义能力有限，不能当作生产向量。

## 验证

1. 上传文件 / 添加网页，确认接口返回 `indexing`。
2. 轮询文档直到 `ready`，检查 chunks 数量。
3. 调用 `/datasets/{id}/retrieval-test`，确认 hits 来源与文本。
4. 检查 Milvus collection/ES 文档数。
5. `mvn test` 和前端 `npm run build`。
