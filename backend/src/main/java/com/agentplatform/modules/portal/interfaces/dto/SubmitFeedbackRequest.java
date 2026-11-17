package com.agentplatform.modules.portal.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 用户端提交消息反馈的请求体（portal interfaces 层 DTO）。
 *
 * <p>放在门户模块内，避免跨模块依赖 chat 的 interfaces 包；由控制器转换为
 * chat 模块公开的 {@code SubmitFeedbackCommand}。</p>
 *
 * @param rating            评分：UP / DOWN，必填
 * @param reasons           原因编码列表；点踩 1–2 个，点赞可选
 * @param comment           备注，最长 200 字
 * @param followUpRequested 是否请求知识运营跟进（仅点踩有效）
 */
public record SubmitFeedbackRequest(
        @NotBlank String rating,
        List<String> reasons,
        @Size(max = 200) String comment,
        boolean followUpRequested
) {
}
