/**
 * 模型模块 REST 接口层。
 *
 * <p>对外提供厂商、模型与路由策略管理接口，统一返回 {@code ApiResponse}；
 * 写操作通过 {@code @PreAuthorize} 控制权限，并交由 {@code AuditLogFilter} 审计。</p>
 */
package com.agentplatform.modules.model.interfaces.rest;
