package com.agentplatform.modules.chat.application.query;

import java.util.List;

/**
 * 管理端反馈分页结果（application/query 层）。
 *
 * @param items 当前页反馈
 * @param page  页码（从 1 开始）
 * @param size  每页条数
 * @param total 过滤条件下的总条数
 */
public record FeedbackPage(List<FeedbackListItem> items, int page, int size, long total) {
}
