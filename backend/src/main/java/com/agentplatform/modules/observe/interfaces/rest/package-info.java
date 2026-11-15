/**
 * 可观测模块 REST 接口层。
 *
 * <p>暴露运营概览、告警列表/确认/恢复、告警规则维护、成本汇总与预算调整接口。
 * 写接口通过 {@code @PreAuthorize} 限制为管理员/开发者，预算调整仅限管理员。</p>
 */
package com.agentplatform.modules.observe.interfaces.rest;
