/**
 * 流程编排 REST 接口层。
 *
 * <p>暴露流程定义管理与流程实例生命周期接口（启动、查询、审批、取消），统一返回
 * {@code ApiResponse}；写操作通过 {@code @PreAuthorize} 限定为平台管理员或开发者角色。</p>
 */
package com.agentplatform.modules.workflow.interfaces.rest;
