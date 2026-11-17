package com.agentplatform.modules.chat.application.query;

/**
 * 反馈原因展示项（application/query 层）。
 *
 * @param code  原因编码，前端提交时使用
 * @param label 中文文案，直接展示
 */
public record FeedbackReasonView(String code, String label) {
}
