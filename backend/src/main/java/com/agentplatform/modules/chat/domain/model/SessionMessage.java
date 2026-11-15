package com.agentplatform.modules.chat.domain.model;

import java.time.Instant;

/**
 * 会话消息（领域模型）。
 *
 * <p>用户消息与助手消息分别落库，按创建时间升序读取；运行时只取最近 12 条
 * 拼装为模型上下文，避免上下文无限增长。</p>
 *
 * @param id           消息 ID，形如 {@code msg_xxxxxxxxxxxx}
 * @param sessionId    所属会话 ID
 * @param role         消息角色：user / assistant
 * @param content      消息正文
 * @param tokens       本条消息的 token 估算值
 * @param model        生成该消息所用模型名；用户消息为 null
 * @param contextCount 生成该消息时注入的 RAG 上下文条数
 * @param createdAt    创建时间
 */
public record SessionMessage(String id, String sessionId, String role, String content,
                             int tokens, String model, int contextCount, Instant createdAt) {
}
