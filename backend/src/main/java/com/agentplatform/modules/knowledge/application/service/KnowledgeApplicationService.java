package com.agentplatform.modules.knowledge.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.knowledge.application.query.DatasetSummary;
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

@Service
public class KnowledgeApplicationService {
    private final KnowledgeRepository repository;
    private final KnowledgeRetrievalService retrievalService;
    private final DocumentIngestionService ingestion;
    private final FileStorage storage;
    private final WebPageFetcher webPageFetcher;

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

    public List<DatasetSummary> listDatasets() {
        return repository.findAllDatasets().stream().map(DatasetSummary::from).toList();
    }

    public DatasetSummary getDataset(String id) {
        return DatasetSummary.from(requireDataset(id));
    }

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

    public DatasetSummary reindex(String id) {
        Dataset dataset = requireDataset(id);
        dataset.markReindexed();
        repository.saveDataset(dataset);
        ingestion.reindexDataset(id);
        return DatasetSummary.from(dataset);
    }

    public Document uploadMultipart(String datasetId, MultipartFile file) {
        Dataset dataset = requireDataset(datasetId);
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择文件");
        if (file.getSize() > 50L * 1024 * 1024) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件不能超过 50 MB");
        String name = file.getOriginalFilename() == null ? "document.txt" : file.getOriginalFilename();
        if (repository.findDocuments(datasetId).stream().anyMatch(d -> d.name().equalsIgnoreCase(name))) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "同名文档已存在，请先删除或替换");
        }
        try {
            byte[] bytes = file.getBytes();
            String storageKey = storage.store(datasetId, name, bytes, file.getContentType());
            Document document = new Document("doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                    datasetId, name, 1, 0, formatSize(file.getSize()), "indexing", "刚刚",
                    storageKey, file.getContentType(), sha256(bytes));
            repository.saveDocument(document);
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

    public Document addWebPage(String datasetId, AddWebPageRequest request) {
        Dataset dataset = requireDataset(datasetId);
        String url = request.url() == null ? "" : request.url().trim();
        if (url.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "网页地址不能为空");
        if (repository.findDocuments(datasetId).stream().anyMatch(d -> url.equals(d.sourceUrl()))) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "该网页已经加入当前数据集");
        }
        WebPageFetcher.WebPage page = webPageFetcher.fetch(url, request.cookie());
        String name = request.name() == null || request.name().isBlank() ? page.title() : request.name().trim();
        if (repository.findDocuments(datasetId).stream().anyMatch(d -> d.name().equalsIgnoreCase(name))) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "同名文档已存在，请指定网页名称");
        }
        byte[] content = page.text().getBytes(java.nio.charset.StandardCharsets.UTF_8);
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

    public Document replaceMultipart(String id, MultipartFile file) {
        Document current = repository.findDocument(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档不存在: " + id));
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择文件");
        if (file.getSize() > 50L * 1024 * 1024) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件不能超过 50 MB");
        try {
            byte[] bytes = file.getBytes();
            String name = file.getOriginalFilename() == null ? current.name() : file.getOriginalFilename();
            String storageKey = storage.store(current.datasetId(), name, bytes, file.getContentType());
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

    private String formatSize(long bytes) {
        return bytes >= 1024 * 1024 ? String.format("%.1f MB", bytes / 1024.0 / 1024.0)
                : Math.max(1, Math.round(bytes / 1024.0)) + " KB";
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { return null; }
    }

    public List<Document> listDocuments(String datasetId) {
        requireDataset(datasetId);
        return repository.findDocuments(datasetId);
    }

    public Document uploadDocument(String datasetId, UploadDocumentRequest request) {
        Dataset dataset = requireDataset(datasetId);
        String name = request.name().trim();
        if (repository.findDocuments(datasetId).stream().anyMatch(d -> d.name().equalsIgnoreCase(name))) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "同名文档已存在，请先删除或替换");
        }
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

    public Document rollbackDocument(String id, int version) {
        Document current = repository.findDocument(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档不存在: " + id));
        Document next = new Document(current.id(), current.datasetId(), current.name(),
                Math.max(1, version), current.chunks(), current.size(), "ready", "刚刚");
        repository.saveDocument(next);
        return next;
    }

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

    public void deleteDocument(String id) {
        Document document = repository.findDocument(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "文档不存在: " + id));
        repository.deleteDocument(id);
        Dataset dataset = requireDataset(document.datasetId());
        dataset.removeDocument(document.chunks());
        repository.saveDataset(dataset);
    }

    public RetrievalResult retrievalTest(String datasetId, String query) {
        Dataset dataset = requireDataset(datasetId);
        return retrievalService.retrieve(dataset, query);
    }

    public RetrievalResult retrieve(String datasetId, String query) {
        return retrievalService.retrieve(requireDataset(datasetId), query);
    }

    private Dataset requireDataset(String id) {
        return repository.findDataset(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据集不存在: " + id));
    }
}
