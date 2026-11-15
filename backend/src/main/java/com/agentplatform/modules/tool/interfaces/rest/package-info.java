/**
 * 工具模块 REST 接口层。
 *
 * <p>暴露 {@code /api/v1/tools} 与 {@code /api/v1/mcp-servers} 相关端点，
 * 统一返回 {@code ApiResponse}，写操作使用 {@code @PreAuthorize} 限定为平台管理员/开发者。
 */
package com.agentplatform.modules.tool.interfaces.rest;
