package com.agentplatform.modules.insight.infrastructure.persistence.mapper;

import com.agentplatform.modules.insight.infrastructure.persistence.entity.EventNameCount;
import com.agentplatform.modules.insight.infrastructure.persistence.entity.ProductEventEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 产品事件 Mapper（infrastructure 层）。
 */
public interface ProductEventMapper extends BaseMapper<ProductEventEntity> {
    /**
     * 按事件名统计时间窗口内的事件数。
     *
     * @param from 起始时间（含）
     * @param to   截止时间（不含）
     * @return 事件名计数行，按数量降序
     */
    List<EventNameCount> countByName(@Param("from") Instant from, @Param("to") Instant to);
}
