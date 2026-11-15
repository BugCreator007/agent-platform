/**
 * 会话运行时限界上下文（Spring Modulith 应用模块）。
 *
 * <p>职责：会话与消息持久化、SSE 流式对话、多模型路由调用、RAG 上下文拼装、
 * MCP 工具调用循环以及智能体会话指标统计。</p>
 *
 * <p>对外仅暴露两个命名接口：{@code api}（会话分析读模型）与 {@code runtime}
 * （流式对话入口）；{@code domain} 与 {@code infrastructure} 不跨模块暴露。</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "会话运行时")
package com.agentplatform.modules.chat;
