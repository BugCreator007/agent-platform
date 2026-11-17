package com.agentplatform.modules.chat.application.query;

import java.time.Instant;
import java.util.List;

/**
 * 用户端消息视图（application/query 层）。
 *
 * <p>用于门户读取历史消息，字段与 user-web 的 {@code BackendMessage} 对齐；
 * 不包含 trace、检索诊断等运营字段，避免把内部实现暴露给用户端。</p>
 *
 * @param id           消息 ID
 * @param sessionId    会话 ID
 * @param role         角色：user / assistant
 * @param content      正文
 * @param tokens       token 估算
 * @param model        模型名
 * @param contextCount 上下文条数
 * @param createdAt    创建时间
 * @param citations    引用列表
 * @param answerType   ANSWER / REFUSAL
 * @param lowConfidence 是否低置信
 */
public record UserMessageView(
        String id, String sessionId, String role, String content, int tokens, String model,
        int contextCount, Instant createdAt, List<CitationView> citations,
        String answerType, boolean lowConfidence
) {
}
