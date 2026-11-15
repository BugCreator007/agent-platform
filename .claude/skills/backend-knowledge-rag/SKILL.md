---
name: backend-knowledge-rag
description: 修改知识库数据集、文件/网页摄取、PDF/DOCX/HTML 解析、切分、Embedding、Milvus/Elasticsearch、混合检索或检索调试时使用。
---

# 知识库 / RAG 开发

## 先读

- `modules/knowledge/domain/model/{Dataset,Document,DocumentChunk}.java`
- `modules/knowledge/application/ingestion/DocumentIngestionService.java`
- `modules/knowledge/application/service/KnowledgeApplicationService.java`
- `modules/knowledge/application/query/KnowledgeRetrievalService.java`
- `modules/knowledge/infrastructure/ingestion/DocumentTextExtractor.java`
- `modules/knowledge/infrastructure/ingestion/TextChunker.java`
- `modules/knowledge/infrastructure/embedding/`
- `modules/knowledge/infrastructure/retrieval/{MilvusVectorStore,ElasticsearchKeywordIndex,NoopVectorStore,NoopKeywordIndex}.java`
- `modules/knowledge/infrastructure/storage/{LocalFileStorage,CosFileStorage}.java`
- `modules/knowledge/infrastructure/web/WebPageFetcher.java`
- `admin-web/src/pages/Knowledge.tsx`

## 数据模型

```text
datasets          数据集/解析/切分/检索配置
documents         文档元数据、版本、来源、状态、checksum
document_chunks   分块、tokenCount、embedding JSON
```

Document 支持：

- `sourceType`：file/web
- `sourceUrl`
- `storageKey/contentType/checksum`
- `version`
- `status`：ready/indexing/failed 等
- `datasetId/chunks/size/updated`

## 摄取链路

```text
上传文件 / 添加网页
 → 存储（local/COS）
 → DocumentTextExtractor
 → TextChunker
 → EmbeddingClient
 → VectorStore(Milvus/DB fallback)
 → KeywordIndex(Elasticsearch/DB fallback)
 → DocumentChunk 持久化
```

### 文件类型

- PDF：PDFBox `PDFTextStripper`
- DOCX：POI XWPF
- Markdown/TXT/HTML：按文本/HTML 解析
- 图片扫描件 OCR 未实现

### 网页

`WebPageFetcher`：

- Jsoup 抓取单个 URL。
- 支持可选 Cookie（仅本次抓取，不持久化）。
- `ALLOW_PRIVATE_WEB=false` 时阻止内网地址。
- 只抓单页，没有站点爬取/增量同步。

## Embedding

- `OpenAiEmbeddingClient`：OpenAI-compatible `/embeddings`。
- `HashEmbeddingClient`：未配置 Key 时的确定性本地哈希向量。
- 维度 `EMBEDDING_DIMENSION`，默认 384。
- 远端失败时回退哈希向量。
- 生产建议 bge-m3 等真实语义模型。

## 检索

`KnowledgeRetrievalService.retrieve(dataset, query)`：

1. query embedding。
2. 向量检索：Milvus；为空则 DB cosine 回退。
3. 关键词检索：Elasticsearch；为空则 DB 子串/字符匹配回退。
4. RRF 融合排序。
5. 阈值过滤、topK 截断。
6. 返回 `RetrievalResult`（hits/source/chunk/score/text）。

配置项：`retrievalMode`（vector/keyword/hybrid）、`topK`、`threshold`、`rerank`。

注意：`rerank` 当前只是配置字段，没有实际重排服务。

## API

```text
GET    /api/v1/datasets
POST   /api/v1/datasets
GET    /api/v1/datasets/{id}
PUT    /api/v1/datasets/{id}
POST   /api/v1/datasets/{id}/reindex
GET    /api/v1/datasets/{id}/documents
POST   /api/v1/datasets/{id}/documents        （上传文件）
POST   /api/v1/datasets/{id}/documents:web    （添加网页）
POST   /api/v1/datasets/{id}/documents:upload （multipart）
PUT    /api/v1/documents/{id}                 （替换）
PUT    /api/v1/documents/{id}:upload          （替换文件）
POST   /api/v1/documents/{id}/rollback
GET    /api/v1/documents/{id}/versions
DELETE /api/v1/documents/{id}
POST   /api/v1/datasets/{id}/retrieval-test
```

## 版本与替换

- 替换文档时 version+1。
- 旧分块先删除，再重新解析/写入。
- 支持按版本回滚元数据。
- 文档删除要同时清理 chunks、向量和关键词索引；当前 `KnowledgeApplicationService.deleteDocument` 主要清理 DB，Milvus/ES 旧向量清理需确认/补齐。

## 智能体授权

- 数据集/文档与智能体通过 `kb` 列表关联。
- Agent 运行时对每个 `agent.kb()` 调用 `knowledge.retrieve`。
- 单个知识库失败降级为空，不影响对话。

## 安全

- 网页抓取默认禁止内网；生产谨慎使用 Cookie。
- 上传文件名/路径不能直接拼接到本地路径，需 storage key。
- COS 密钥只从环境变量读取。
- 文档内容作为不可信上下文，提示词要防注入。
- 文档级 ACL 未实现。

## 新增解析器/切分器

1. 在 `infrastructure/ingestion` 增加实现。
2. 更新 `DocumentTextExtractor` 的类型判断。
3. 处理编码、空文本、超大文件、异常。
4. 更新 `parser/chunkStrategy` 展示与前端选项。
5. 用真实文件验证 chunks。
6. 添加中文注释。

## 测试

- 上传 PDF/DOCX/MD/TXT/HTML，检查 doc/chunks。
- 网页抓取公开页。
- retrieval-test 三种模式。
- 无 Embedding Key 时回退哈希向量。
- 无 Milvus/ES 时 DB 回退。
- 重启后数据仍在。

## 当前限制

- 真实 rerank 未实现。
- OCR、表格结构化、图片多模态未实现。
- 网页增量爬取/Confluence API 未实现。
- 文档级权限/引用标注/查询改写未实现。
- 向量删除清理和版本清理需加强。
- 没有完整 RAG 自动化测试。

## Checklist

- [ ] 文件/网页来源正确落库。
- [ ] 解析失败有明确状态/错误。
- [ ] 分块参数与前端一致。
- [ ] Embedding/向量/关键词索引三者同步。
- [ ] 替换/删除不残留旧分块/向量。
- [ ] 检索阈值/topK/模式可调。
- [ ] 无外部服务时能降级。
- [ ] 中文注释。
