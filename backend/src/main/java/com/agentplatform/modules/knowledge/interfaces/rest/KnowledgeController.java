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

@RestController
@RequestMapping("/api/v1")
public class KnowledgeController {
    private final KnowledgeApplicationService applicationService;

    public KnowledgeController(KnowledgeApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/datasets")
    public ApiResponse<List<DatasetSummary>> datasets() {
        return ApiResponse.ok(applicationService.listDatasets());
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/datasets")
    public ApiResponse<DatasetSummary> createDataset(@Valid @RequestBody CreateDatasetRequest request) {
        return ApiResponse.ok(applicationService.createDataset(request));
    }

    @GetMapping("/datasets/{id}")
    public ApiResponse<DatasetSummary> dataset(@PathVariable String id) {
        return ApiResponse.ok(applicationService.getDataset(id));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/datasets/{id}")
    public ApiResponse<DatasetSummary> updateDataset(@PathVariable String id,
                                                     @RequestBody UpdateDatasetRequest request) {
        return ApiResponse.ok(applicationService.updateDataset(id, request));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/datasets/{id}/reindex")
    public ApiResponse<DatasetSummary> reindex(@PathVariable String id) {
        return ApiResponse.ok(applicationService.reindex(id));
    }

    @GetMapping("/datasets/{id}/documents")
    public ApiResponse<List<Document>> documents(@PathVariable String id) {
        return ApiResponse.ok(applicationService.listDocuments(id));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/datasets/{id}/documents")
    public ApiResponse<Document> upload(@PathVariable String id,
                                        @Valid @RequestBody UploadDocumentRequest request) {
        return ApiResponse.ok(applicationService.uploadDocument(id, request));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping("/documents/{id}")
    public ApiResponse<Document> replace(@PathVariable String id, @RequestBody java.util.Map<String, Object> body) {
        return ApiResponse.ok(applicationService.replaceDocument(id,
                body.get("name") == null ? null : String.valueOf(body.get("name")),
                body.get("size") == null ? null : String.valueOf(body.get("size"))));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/documents/{id}/rollback")
    public ApiResponse<Document> rollback(@PathVariable String id, @RequestBody java.util.Map<String, Object> body) {
        int version = body.get("version") instanceof Number n ? n.intValue() : 1;
        return ApiResponse.ok(applicationService.rollbackDocument(id, version));
    }

    @GetMapping("/documents/{id}/versions")
    public ApiResponse<List<Document>> versions(@PathVariable String id) {
        return ApiResponse.ok(applicationService.documentVersions(id));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping(value = "/datasets/{id}/documents:upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Document> uploadFile(@PathVariable String id, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(applicationService.uploadMultipart(id, file));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping("/datasets/{id}/documents:web")
    public ApiResponse<Document> addWebPage(@PathVariable String id, @Valid @RequestBody AddWebPageRequest request) {
        return ApiResponse.ok(applicationService.addWebPage(id, request));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PutMapping(value = "/documents/{id}:upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Document> replaceFile(@PathVariable String id, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(applicationService.replaceMultipart(id, file));
    }

    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable String id) {
        applicationService.deleteDocument(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/datasets/{id}/retrieval-test")
    public ApiResponse<RetrievalResult> retrieval(@PathVariable String id,
                                                  @Valid @RequestBody RetrievalRequest request) {
        return ApiResponse.ok(applicationService.retrievalTest(id, request.query()));
    }
}
