package com.agentplatform.modules.chat.application.query;

import java.util.List;

/**
 * 反馈质量汇总（application/query 层）。
 *
 * <p>反馈率为“有反馈的消息数 / 助手消息数”，好评率为“点赞数 / 有反馈数”；
 * 无答案率由拒答消息数 / 助手消息数得到。所有指标均由真实表数据计算，不造演示值。</p>
 *
 * @param agentId          智能体过滤条件，空表示全平台
 * @param days             统计天数
 * @param total            反馈条数（含点赞与点踩）
 * @param up               点赞数
 * @param down             点踩数
 * @param feedbackMessages 有反馈的助手消息数
 * @param assistantMessages 助手消息总数
 * @param refusals         拒答消息数
 * @param followUp         请求知识运营跟进的条数
 * @param feedbackRate     反馈率，0–1
 * @param positiveRate     好评率，0–1；无反馈时为 0
 * @param refusalRate      无答案率，0–1
 * @param reasons          负反馈原因分布（只统计点踩）
 */
public record FeedbackStats(
        String agentId, int days, long total, long up, long down, long feedbackMessages,
        long assistantMessages, long refusals, long followUp,
        double feedbackRate, double positiveRate, double refusalRate,
        List<ReasonCount> reasons
) {
    /**
     * 单个原因的数量。
     *
     * @param code  原因编码
     * @param label 中文文案
     * @param count 数量
     */
    public record ReasonCount(String code, String label, long count) {
    }
}
