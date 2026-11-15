package com.agentplatform.modules.knowledge.interfaces.rest;

import com.agentplatform.common.api.ApiResponse;
import com.agentplatform.modules.knowledge.application.query.DatasetSummary;
import com.agentplatform.modules.knowledge.application.query.RetrievalResult;
import com.agentplatform.modules.knowledge.application.service.KnowledgeApplicationService;
import com.agentplatform.modules.knowledge.domain.model.Document;
import com.agentplatform.modules.knowledge.interfaces.dto.AddWebPageRequest;
import com.agentplatform.modules.knowledge.interfaces.dto.CreateDatasetRequest;
import com.agentplatform.modules.knowledge.interfaces.dto.RetrievalRequest;
import com.agentplatform.modules.knowledge.interfaces.dto.UpdateDatasetRequest;
import com.agentplatform.modules.knowledge.interfaces.dto.UploadDocumentRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.util.List;

/**
 * 知识库 REST 控制器（接口层）。
 *
 * <p>提供数据集管理、文档上传/替换/回滚/删除、网页入库与检索测试接口，
 * 统一前缀 {@code /api/v1}，响应统一包装为 {@link ApiResponse}。</p>
 *
 * <p>权限约束：查询接口对已登录用户开放；所有写接口通过 {@code @PreAuthorize}
 * 限定为 PLATFORM_ADMIN 或 DEVELOPER 角色，并由全局审计过滤器记录写操作。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class KnowledgeController {
    /** 知识库应用服务。 */
    private final KnowledgeApplicationService applicationService;

    /**
     * @param applicationService 知识库应用服务
     */
    public KnowledgeController(KnowledgeApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询全部数据集。
     *
     * @return 数据集摘要列表
     */
    @GetMapping("/datasets")
    public ApiResponse<List<DatasetSummary>> datasets() {
        return ApiResponse.ok(applicationService.listDatasets());
    }

    /**
     * 创建数据集。
     *
     * @param request 创建请求
     * @return 新建的数据集摘要
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/datasets")
    public ApiResponse<DatasetSummary> createDataset(@Valid @RequestBody CreateDatasetRequest request) {
        return ApiResponse.ok(applicationService.createDataset(request));
    }

    /**
     * 查询单个数据集。
     *
     * @param id 数据集 ID
     * @return 数据集摘要
     */
    @GetMapping("/datasets/{id}")
    public ApiResponse<DatasetSummary> dataset(@PathVariable String id) {
        return ApiResponse.ok(applicationService.getDataset(id));
    }

    /**
     * 更新数据集基础信息与配置。
     *
     * @param id      数据集 ID
     * @param request 更新请求（null 字段不修改）
     * @return 更新后的数据集摘要
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/datasets/{id}")
    public ApiResponse<DatasetSummary> updateDataset(@PathVariable String id,
                                                     @RequestBody UpdateDatasetRequest request) {
        return ApiResponse.ok(applicationService.updateDataset(id, request));
    }

    /**
     * 触发数据集重建索引。
     *
     * @param id 数据集 ID
     * @return 重建标记已清除的数据集摘要
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/datasets/{id}/reindex")
    public ApiResponse<DatasetSummary> reindex(@PathVariable String id) {
        return ApiResponse.ok(applicationService.reindex(id));
    }

    /**
     * 查询数据集下的文档列表。
     *
     * @param id 数据集 ID
     * @return 文档列表
     */
    @GetMapping("/datasets/{id}/documents")
    public ApiResponse<List<Document>> documents(@PathVariable String id) {
        return ApiResponse.ok(applicationService.listDocuments(id));
    }

    /**
     * 仅登记文档元数据（不上传文件）。
     *
     * @param id      数据集 ID
     * @param request 文档元数据请求
     * @return 新建文档
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/datasets/{id}/documents")
    public ApiResponse<Document> upload(@PathVariable String id,
                                        @Valid @RequestBody UploadDocumentRequest request) {
        return ApiResponse.ok(applicationService.uploadDocument(id, request));
    }

    /**
     * 更新文档名称/大小展示信息（版本号加一）。
     *
     * @param id   文档 ID
     * @param body 请求体，可包含 name 与 size
     * @return 更新后的文档
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/documents/{id}")
    public ApiResponse<Document> replace(@PathVariable String id, @RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok(applicationService.replaceDocument(id,
                body.get("name") == null ? null : String.valueOf(body.get("name")),
                body.get("size") == null ? null : String.valueOf(body.get("size"))));
    }

    /**
     * 将文档回滚到指定版本。
     *
     * @param id   文档 ID
     * @param body 请求体，version 字段为目标版本号，缺省为 1
     * @return 回滚后的文档
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/documents/{id}/rollback")
    public ApiResponse<Document> rollback(@PathVariable String id, @RequestBody java.util.Map<String, Object> body) {
        int version = body.get("version") instanceof Number n ? n.intValue() : 1;
        return ApiResponse.ok(applicationService.rollbackDocument(id, version));
    }

    /**
     * 查询文档的版本列表（当前版本 ready，历史版本 archived）。
     *
     * @param id 文档 ID
     * @return 按版本号降序的文档列表
     */
    @GetMapping("/documents/{id}/versions")
    public ApiResponse<List<Document>> versions(@PathVariable String id) {
        return ApiResponse.ok(applicationService.documentVersions(id));
    }

    /**
     * 上传真实文件到数据集（multipart），保存后异步解析并索引。
     *
     * @param id   数据集 ID
     * @param file 上传文件，最大 50 MB
     * @return 处于 indexing 状态的文档
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping(value = "/datasets/{id}/documents:upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Document> uploadFile(@PathVariable String id, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(applicationService.uploadMultipart(id, file));
    }

    /**
     * 抓取网页并作为文档加入数据集。
     *
     * @param id      数据集 ID
     * @param request 网页地址、可选名称与 Cookie
     * @return 处于 indexing 状态的文档
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/datasets/{id}/documents:web")
    public ApiResponse<Document> addWebPage(@PathVariable String id, @Valid @RequestBody AddWebPageRequest request) {
        return ApiResponse.ok(applicationService.addWebPage(id, request));
    }

    /**
     * 用新文件替换已有文档（版本号加一）并重新索引。
     *
     * @param id   文档 ID
     * @param file 新文件，最大 50 MB
     * @return 处于 indexing 状态的新版本文档
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping(value = "/documents/{id}:upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Document> replaceFile(@PathVariable String id, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(applicationService.replaceMultipart(id, file));
    }

    /**
     * 删除文档及其分块，并扣减数据集统计。
     *
     * @param id 文档 ID
     * @return 空响应体
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable String id) {
        applicationService.deleteDocument(id);
        return ApiResponse.ok(null);
    }

    /**
     * 在指定数据集上执行检索测试。
     *
     * @param id      数据集 ID
     * @param request 查询请求
     * @return 检索结果
     */
    @PostMapping("/datasets/{id}/retrieval-test")
    public ApiResponse<RetrievalResult> retrieval(@PathVariable String id,
                                                  @Valid @RequestBody RetrievalRequest request) {
        return ApiResponse.ok(applicationService.retrievalTest(id, request.query()));
    }
}
