package com.agentplatform.modules.knowledge.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 仅登记文档元数据的请求 DTO（不包含真实文件内容）。
 *
 * @param name   文档名称，必填
 * @param chunks 分块数量，可选；为空时按数据集分块大小估算
 * @param size   展示用文件大小文本，可选
 */
public record UploadDocumentRequest(@NotBlank String name, Long chunks, String size) {
}
