package com.agentplatform.modules.insight.domain.repository;

import com.agentplatform.modules.insight.domain.model.ProductEvent;

import java.time.Instant;
import java.util.Map;

/**
 * 产品事件仓储端口（domain 层）。
 */
public interface ProductEventRepository {
    /**
     * 写入一条事件；eventId 重复时由数据库唯一约束拒绝，调用方按幂等处理。
     *
     * @param event 事件
     */
    void save(ProductEvent event);

    /**
     * 按事件名统计时间窗口内的事件数。
     *
     * @param from 起始时间（含）
     * @param to   截止时间（不含）
     * @return 事件名到数量的映射，按数量降序；无数据时为空 Map
     */
    Map<String, Long> countByName(Instant from, Instant to);
}
