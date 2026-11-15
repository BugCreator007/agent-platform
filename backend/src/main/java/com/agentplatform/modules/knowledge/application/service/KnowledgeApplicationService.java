package com.agentplatform.modules.knowledge.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.knowledge.application.query.DatasetSummary;
import com.agentplatform.modules.knowledge.application.query.DocumentDownload;
import com.agentplatform.modules.knowledge.application.query.KnowledgeRetrievalService;
import com.agentplatform.modules.knowledge.application.query.RetrievalResult;
import com.agentplatform.modules.knowledge.domain.model.Dataset;
import com.agentplatform.modules.knowledge.domain.model.Document;
import com.agentplatform.modules.knowledge.domain.repository.KnowledgeRepository;
import com.agentplatform.modules.knowledge.interfaces.dto.CreateDatasetRequest;
import com.agentplatform.modules.knowledge.interfaces.dto.UpdateDatasetRequest;
import com.agentplatform.modules.knowledge.interfaces.dto.UploadDocumentRequest;
import com.agentplatform.modules.knowledge.application.ingestion.DocumentIngestionService;
import com.agentplatform.modules.knowledge.infrastructure.storage.FileStorage;
import com.agentplatform.modules.knowledge.infrastructure.web.WebPageFetcher;
import com.agentplatform.modules.knowledge.interfaces.dto.AddWebPageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 知识库命令侧应用服务（命名接口 {@code api}）。
 *
 * <p>编排数据集与文档的完整用例：数据集增改与重建索引、文件上传/替换、网页入库、
 * 文档版本查询/回滚/删除以及检索测试。文件类操作先写入 {@link FileStorage}，
 * 再保存文档元数据并触发异步摄取；写入失败时以 {@link BusinessException} 统一包装错误。</p>
 *
 * <p>关键约束：同一数据集内文件名（忽略大小写）与网页 URL 均不可重复；
 * 单文件大小上限 50 MB；文档删除会级联清理分块（由仓储实现保证）。</p>
 */
@Service
public class KnowledgeApplicationService {
    /** 数据集与文档元数据仓储。 */
    private final KnowledgeRepository repository;
    /** 混合检索服务。 */
    private final KnowledgeRetrievalService retrievalService;
    /** 异步摄取流水线。 */
    private final DocumentIngestionService ingestion;
    /** 原始文件存储（本地或 COS）。 */
    private final FileStorage storage;
    /** 网页抓取器。 */
    private final WebPageFetcher webPageFetcher;

    /**
     * 构造应用服务。
     *
     * @param repository       知识库仓储
     * @param retrievalService 检索服务
     * @param ingestion        摄取服务
     * @param storage          文件存储
     * @param webPageFetcher   网页抓取器
     */
    public KnowledgeApplicationService(KnowledgeRepository repository,
                                       KnowledgeRetrievalService retrievalService,
                                       DocumentIngestionService ingestion,
                                       FileStorage storage,
                                       WebPageFetcher webPageFetcher) {
        this.repository = repository;
        this.retrievalService = retrievalService;
        this.ingestion = ingestion;
        this.storage = storage;
        this.webPageFetcher = webPageFetcher;
    }

    /**
     * 列出全部数据集摘要。
     *
     * @return 数据集摘要列表
     */
    public List<DatasetSummary> listDatasets() {
        return repository.findAllDatasets().stream().map(DatasetSummary::from).toList();
    }

    /**
     * 查询单个数据集摘要。
     *
     * @param id 数据集 ID
     * @return 数据集摘要
     * @throws BusinessException 数据集不存在时抛出 NOT_FOUND
     */
    public DatasetSummary getDataset(String id) {
        return DatasetSummary.from(requireDataset(id));
    }

    /**
     * 创建数据集；未提供的可选配置使用默认值。
     *
     * @param request 创建请求
     * @return 新建的数据集摘要
     */
    public DatasetSummary createDataset(CreateDatasetRequest request) {
        String id = "kb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Dataset dataset = Dataset.create(id, request.name(),
                request.owner() == null ? "顾先路" : request.owner(),
                request.description() == null ? "" : request.description(),
                request.embeddingModel() == null ? "bge-m3" : request.embeddingModel(),
                request.indexType() == null ? "HNSW" : request.indexType(),
                request.retrievalMode() == null ? "hybrid" : request.retrievalMode());
        repository.saveDataset(dataset);
        return DatasetSummary.from(dataset);
    }

    /**
     * 更新数据集基础信息与配置。
     *
     * @param id      数据集 ID
     * @param request 更新请求，null 字段表示不修改
     * @return 更新后的数据集摘要
     * @throws BusinessException 数据集不存在时抛出 NOT_FOUND
     */
    public DatasetSummary updateDataset(String id, UpdateDatasetRequest request) {
        Dataset dataset = requireDataset(id);
        dataset.updateBasic(request.name(), request.owner(), request.description());
        dataset.updateConfig(request.embeddingModel(), request.indexType(), request.metric(),
                request.parser(), request.chunkStrategy(), request.chunkSize(), request.overlap(),
                request.retrievalMode(), request.topK(), request.threshold(), request.rerank(),
                request.authAgents(), request.needsReindex());
        repository.saveDataset(dataset);
        return DatasetSummary.from(dataset);
    }

    /**
     * 触发数据集重建索引：先清除重建标记并保存，再异步逐文档重新摄取。
     *
     * @param id 数据集 ID
     * @return 重建标记已清除的数据集摘要
     * @throws BusinessException 数据集不存在时抛出 NOT_FOUND
     */
    public DatasetSummary reindex(String id) {
        Dataset dataset = requireDataset(id);
        dataset.markReindexed();
        repository.saveDataset(dataset);
        ingestion.reindexDataset(id);
        return DatasetSummary.from(dataset);
    }

    /**
     * 上传文件到数据集：校验大小与重名，保存原始文件与元数据，并触发异步摄取。
     *
     * @param datasetId 数据集 ID
     * @param file      上传的文件（multipart）
     * @return 处于 indexing 状态的文档元数据
     * @throws BusinessException 数据集不存在、文件为空/超限、同名校验失败或存储失败时抛出
     */
    public Document uploadMultipart(String datasetId, MultipartFile file) {
        Dataset dataset = requireDataset(datasetId);
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择文件");
        // 限制单文件大小，避免同步读取大文件占用过多内存
        if (file.getSize() > 50L * 1024 * 1024) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件不能超过 50 MB");
        String name = file.getOriginalFilename() == null ? "document.txt" : file.getOriginalFilename();
        if (repository.findDocuments(datasetId).stream().anyMatch(d -> d.name().equalsIgnoreCase(name))) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "同名文档已存在，请先删除或替换");
        }
        try {
            byte[] bytes = file.getBytes();
            // 存储键由存储实现生成（local:/cos: 前缀），后续摄取阶段凭此读取原始文件
            String storageKey = storage.store(datasetId, name, bytes, file.getContentType());
            Document document = new Document("doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    datasetId, name, 1, 0, formatSize(file.getSize()), "indexing", "刚刚",
                    storageKey, file.getContentType(), sha256(bytes));
            repository.saveDocument(document);
            // 先登记文档数量（分块数稍后由摄取完成时修正），使前端能立即看到文档
            dataset.addDocument(0);
            repository.saveDataset(dataset);
            ingestion.ingest(document.id());
            return document;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文档保存失败: " + e.getMessage());
        }
    }

    /**
     * 将网页抓取为文本并加入数据集：校验 URL 唯一性，按标题命名，保存文本文件并触发摄取。
     *
     * @param datasetId 数据集 ID
     * @param request   网页请求（URL、可选名称与 Cookie）
     * @return 处于 indexing 状态的文档元数据
     * @throws BusinessException 数据集不存在、URL 为空/重复、名称冲突或抓取存储失败时抛出
     */
    public Document addWebPage(String datasetId, AddWebPageRequest request) {
        Dataset dataset = requireDataset(datasetId);
        String url = request.url() == null ? "" : request.url().trim();
        if (url.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "网页地址不能为空");
        if (repository.findDocuments(datasetId).stream().anyMatch(d -> url.equals(d.sourceUrl()))) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "该网页已经加入当前数据集");
        }
        // 抓取器会做协议与内网地址校验，返回标题与正文纯文本
        WebPageFetcher.WebPage page = webPageFetcher.fetch(url, request.cookie());
        String name = request.name() == null || request.name().isBlank() ? page.title() : request.name().trim();
        if (repository.findDocuments(datasetId).stream().anyMatch(d -> d.name().equalsIgnoreCase(name))) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "同名文档已存在，请指定网页名称");
        }
        byte[] content = page.text().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // 网页抓取结果统一以 .txt 落盘；标题中的路径非法字符替换为下划线
        String storageKey = storage.store(datasetId, name.replaceAll("[\\/:*?\"<>|]", "_") + ".txt",
                content, "text/plain");
        Document document = new Document("doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                datasetId, name, 1, 0, formatSize(content.length), "indexing", "刚刚",
                storageKey, "text/plain; source=web", sha256(content), "WEB", url);
        repository.saveDocument(document);
        dataset.addDocument(0);
        repository.saveDataset(dataset);
        ingestion.ingest(document.id());
        return document;
    }

    /**
     * 用新文件替换已有文档：版本号加一，保留文档 ID 与数据集归属，并重新摄取。
     *
     * @param id   文档 ID
     * @param file 新文件（multipart）
     * @return 处于 indexing 状态的新版本文档
     * @throws BusinessException 文档不存在、文件为空/超限或存储失败时抛出
     */
    public Document replaceMultipart(String id, MultipartFile file) {
        Document current = repository.findDocument(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档不存在: " + id));
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择文件");
        if (file.getSize() > 50L * 1024 * 1024) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件不能超过 50 MB");
        try {
            byte[] bytes = file.getBytes();
            String name = file.getOriginalFilename() == null ? current.name() : file.getOriginalFilename();
            String storageKey = storage.store(current.datasetId(), name, bytes, file.getContentType());
            // 版本号递增，旧的原始文件仍保留在存储中以便追溯
            Document next = new Document(current.id(), current.datasetId(), name, current.version() + 1,
                    current.chunks(), formatSize(file.getSize()), "indexing", "刚刚", storageKey,
                    file.getContentType(), sha256(bytes));
            repository.saveDocument(next);
            ingestion.ingest(next.id());
            return next;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文档替换失败: " + e.getMessage());
        }
    }

    /**
     * 将字节数格式化为展示文本：≥1 MB 显示一位小数的 MB，否则显示 KB（至少 1 KB）。
     *
     * @param bytes 字节数
     * @return 展示用大小文本
     */
    private String formatSize(long bytes) {
        return bytes >= 1024 * 1024 ? String.format("%.1f MB", bytes / 1024.0 / 1024.0)
                : Math.max(1, Math.round(bytes / 1024.0)) + " KB";
    }

    /**
     * 计算内容的 SHA-256 十六进制摘要；算法不可用时返回 null。
     *
     * @param bytes 原始内容
     * @return 64 位十六进制摘要，或 null
     */
    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { return null; }
    }

    /**
     * 列出数据集下的全部文档。
     *
     * @param datasetId 数据集 ID
     * @return 文档列表
     * @throws BusinessException 数据集不存在时抛出 NOT_FOUND
     */
    public List<Document> listDocuments(String datasetId) {
        requireDataset(datasetId);
        return repository.findDocuments(datasetId);
    }

    /**
     * 仅登记文档元数据（不上传真实文件），用于演示或外部已入库场景。
     *
     * @param datasetId 数据集 ID
     * @param request   文档元数据请求；未提供分块数时按数据集分块大小估算
     * @return 状态为 ready 的文档
     * @throws BusinessException 数据集不存在或同名文档已存在时抛出
     */
    public Document uploadDocument(String datasetId, UploadDocumentRequest request) {
        Dataset dataset = requireDataset(datasetId);
        String name = request.name().trim();
        if (repository.findDocuments(datasetId).stream().anyMatch(d -> d.name().equalsIgnoreCase(name))) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "同名文档已存在，请先删除或替换");
        }
        // 以 14000 字符左右的典型文档规模反推分块数，仅用于统计展示
        long chunkCount = request.chunks() == null
                ? Math.max(4, Math.round(14000.0 / Math.max(128, dataset.chunkSize())))
                : request.chunks();
        Document document = new Document(
                "doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                datasetId, name, 1, chunkCount,
                request.size() == null ? "—" : request.size(), "ready", "刚刚");
        repository.saveDocument(document);
        dataset.addDocument(chunkCount);
        repository.saveDataset(dataset);
        return document;
    }

    /**
     * 更新文档的展示元数据（名称/大小），并将版本号加一。
     *
     * @param id   文档 ID
     * @param name 新名称，空白时保留原名
     * @param size 新大小展示文本，空白时保留原值
     * @return 更新后的文档
     * @throws BusinessException 文档不存在时抛出 NOT_FOUND
     */
    public Document replaceDocument(String id, String name, String size) {
        Document current = repository.findDocument(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档不存在: " + id));
        Document next = new Document(current.id(), current.datasetId(),
                name == null || name.isBlank() ? current.name() : name,
                current.version() + 1, current.chunks(),
                size == null || size.isBlank() ? current.size() : size, "ready", "刚刚");
        repository.saveDocument(next);
        return next;
    }

    /**
     * 将文档回滚到指定版本；版本号至少为 1。
     *
     * @param id      文档 ID
     * @param version 目标版本号
     * @return 回滚后的文档
     * @throws BusinessException 文档不存在时抛出 NOT_FOUND
     */
    public Document rollbackDocument(String id, int version) {
        Document current = repository.findDocument(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档不存在: " + id));
        Document next = new Document(current.id(), current.datasetId(), current.name(),
                Math.max(1, version), current.chunks(), current.size(), "ready", "刚刚");
        repository.saveDocument(next);
        return next;
    }

    /**
     * 构造文档的版本列表视图：当前版本为 ready，历史版本为 archived。
     *
     * <p>系统不为历史版本单独存储快照，此处按 1..当前版本号生成展示用记录。</p>
     *
     * @param id 文档 ID
     * @return 按版本号降序的文档列表
     * @throws BusinessException 文档不存在时抛出 NOT_FOUND
     */
    public List<Document> documentVersions(String id) {
        Document current = repository.findDocument(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档不存在: " + id));
        return java.util.stream.IntStream.rangeClosed(1, current.version())
                .mapToObj(v -> new Document(current.id(), current.datasetId(), current.name(), v,
                        current.chunks(), current.size(), v == current.version() ? "ready" : "archived",
                        v == current.version() ? current.updated() : (current.version() - v) + " 天前"))
                .sorted((a, b) -> Integer.compare(b.version(), a.version()))
                .toList();
    }

    /**
     * 删除文档并同步扣减数据集统计。
     *
     * <p>文档与分块的级联删除在仓储实现的事务内完成；此处只负责数据集计数。</p>
     *
     * @param id 文档 ID
     * @throws BusinessException 文档不存在或所属数据集不存在时抛出 NOT_FOUND
     */
    /**
     * 读取文档原始文件，供登录用户下载和对话引用链接使用。
     *
     * @param id 文档 ID
     * @return 文件名、MIME 与字节内容
     * @throws BusinessException 文档不存在或没有可下载文件时抛出
     */
    public DocumentDownload downloadDocument(String id) {
        Document document = repository.findDocument(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档不存在: " + id));
        if (document.storageKey() == null || document.storageKey().isBlank()) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "该文档没有可下载的原始文件");
        }
        return new DocumentDownload(document.name(), document.contentType(), storage.read(document.storageKey()));
    }

    public void deleteDocument(String id) {
        Document document = repository.findDocument(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档不存在: " + id));
        repository.deleteDocument(id);
        Dataset dataset = requireDataset(document.datasetId());
        dataset.removeDocument(document.chunks());
        repository.saveDataset(dataset);
    }

    /**
     * 在指定数据集上执行检索测试（供管理后台调试）。
     *
     * @param datasetId 数据集 ID
     * @param query     查询文本
     * @return 检索结果
     * @throws BusinessException 数据集不存在时抛出 NOT_FOUND
     */
    public RetrievalResult retrievalTest(String datasetId, String query) {
        Dataset dataset = requireDataset(datasetId);
        return retrievalService.retrieve(dataset, query);
    }

    /**
     * 在指定数据集上执行检索（供其他模块调用）。
     *
     * @param datasetId 数据集 ID
     * @param query     查询文本
     * @return 检索结果
     * @throws BusinessException 数据集不存在时抛出 NOT_FOUND
     */
    public RetrievalResult retrieve(String datasetId, String query) {
        return retrievalService.retrieve(requireDataset(datasetId), query);
    }

    /**
     * 获取数据集，不存在时抛出业务异常。
     *
     * @param id 数据集 ID
     * @return 数据集聚合
     * @throws BusinessException 数据集不存在时抛出 NOT_FOUND
     */
    private Dataset requireDataset(String id) {
        return repository.findDataset(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在: " + id));
    }
}
