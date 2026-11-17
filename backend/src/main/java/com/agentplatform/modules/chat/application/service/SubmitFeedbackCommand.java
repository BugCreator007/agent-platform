package com.agentplatform.modules.chat.application.service;

import java.util.List;

/**
 * 提交反馈的入参（application 层命令）。
 *
 * @param rating            评分：UP / DOWN
 * @param reasons           原因编码列表；DOWN 必填 1–2 个
 * @param comment           可选备注，最长 200 字
 * @param followUpRequested 是否请求知识运营跟进（仅点踩有效）
 */
public record SubmitFeedbackCommand(String rating, List<String> reasons,
                                    String comment, boolean followUpRequested) {
}
