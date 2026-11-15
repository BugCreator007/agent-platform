/**
 * 知识库 REST 接口层：暴露数据集、文档与检索测试的 HTTP 端点。
 *
 * <p>统一返回 {@code ApiResponse}，写接口通过 {@code @PreAuthorize} 限定为平台管理员或开发者。</p>
 */
package com.agentplatform.modules.knowledge.interfaces.rest;
