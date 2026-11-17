package com.agentplatform.modules.insight.application.query;

import com.agentplatform.modules.insight.domain.repository.ProductEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 事件统计查询（application/query 层），用于验证埋点落库与运营查看漏斗基数。
 */
@Service
public class EventStatsService {
    private final ProductEventRepository repository;

    /**
     * @param repository 事件仓储
     */
    public EventStatsService(ProductEventRepository repository) {
        this.repository = repository;
    }

    /**
     * 统计最近 N 天的事件数量。
     *
     * @param days 天数，1–90
     * @return 统计结果
     */
    public EventStatsResponse stats(int days) {
        int range = Math.max(1, Math.min(days, 90));
        Instant to = Instant.now();
        Instant from = to.minus(range, ChronoUnit.DAYS);
        Map<String, Long> counts = repository.countByName(from, to);
        return new EventStatsResponse(range, from, to, counts);
    }
}
