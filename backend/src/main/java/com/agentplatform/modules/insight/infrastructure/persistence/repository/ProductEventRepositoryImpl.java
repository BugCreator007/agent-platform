package com.agentplatform.modules.insight.infrastructure.persistence.repository;

import com.agentplatform.modules.insight.domain.model.ProductEvent;
import com.agentplatform.modules.insight.domain.repository.ProductEventRepository;
import com.agentplatform.modules.insight.infrastructure.persistence.entity.EventNameCount;
import com.agentplatform.modules.insight.infrastructure.persistence.entity.ProductEventEntity;
import com.agentplatform.modules.insight.infrastructure.persistence.mapper.ProductEventMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产品事件仓储的 MyBatis-Plus 实现（infrastructure 层）。
 */
@Repository
public class ProductEventRepositoryImpl implements ProductEventRepository {
    private final ProductEventMapper mapper;

    /**
     * @param mapper 事件 Mapper
     */
    public ProductEventRepositoryImpl(ProductEventMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 写入事件；重复 eventId 由唯一约束拒绝并抛出 DuplicateKeyException。
     *
     * @param event 事件
     */
    @Override
    public void save(ProductEvent event) {
        mapper.insert(ProductEventEntity.from(event));
    }

    /**
     * 按事件名统计。
     *
     * @param from 起始时间（含）
     * @param to   截止时间（不含）
     * @return 事件名到数量的有序映射
     */
    @Override
    public Map<String, Long> countByName(Instant from, Instant to) {
        List<EventNameCount> rows = mapper.countByName(from, to);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (EventNameCount row : rows) {
            counts.put(row.getEventName(), row.getCnt());
        }
        return counts;
    }
}
