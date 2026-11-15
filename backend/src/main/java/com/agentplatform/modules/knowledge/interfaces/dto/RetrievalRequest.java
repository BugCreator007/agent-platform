package com.agentplatform.modules.knowledge.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 检索测试请求 DTO。
 *
 * @param query 查询文本，必填
 */
public record RetrievalRequest(@NotBlank String query) {
}
