/**
 * 平台系统模块的 REST 接口层。
 *
 * <p>暴露认证（{@code /api/v1/auth/*}）与平台管理（成员、API Key、审计日志）接口，
 * 统一使用 {@code ApiResponse}，写操作受 {@code @PreAuthorize} 与审计过滤器约束。</p>
 */
package com.agentplatform.modules.system.interfaces.rest;
