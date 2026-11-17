package com.agentplatform.modules.chat.domain.model;

import java.util.Locale;

/**
 * 反馈处理状态（领域枚举）。
 *
 * <p>本轮只保留扩展位并在管理端展示，处理动作属于 B2 洞察迭代；
 * 状态编码入库后不要随意改名。</p>
 */
public enum FeedbackHandleStatus {
    /** 待处理 */
    OPEN,
    /** 已处理 */
    HANDLED,
    /** 已忽略 */
    IGNORED;

    /**
     * 解析状态编码，非法或空值回退 OPEN，避免脏数据阻断读取。
     *
     * @param value 状态编码
     * @return 对应状态
     */
    public static FeedbackHandleStatus parse(String value) {
        if (value == null || value.isBlank()) return OPEN;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OPEN;
        }
    }
}
