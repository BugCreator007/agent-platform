/**
 * 会话查询读模型与对外查询接口，作为 {@code api} 命名接口供运营概览等跨模块调用。
 *
 * <p>该包只暴露轻量读模型（{@link com.agentplatform.modules.chat.application.query.SessionFact}），
 * 避免把包含 trace 的完整会话聚合读入调用方。</p>
 */
@org.springframework.modulith.NamedInterface("api")
package com.agentplatform.modules.chat.application.query;
