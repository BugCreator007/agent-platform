package com.agentplatform.modules.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.chat.infrastructure.persistence.entity.ChatSessionEntity;
import java.util.List;

/**
 * 会话表 MyBatis-Plus Mapper（infrastructure 层），SQL 定义在对应 XML 中。
 */
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {
    /**
     * 查询创建时间落在给定窗口内的会话（供运营指标统计）。
     *
     * @param from 起始时间（含）
     * @param to   结束时间（不含）
     * @return 会话实体列表
     */
    List<ChatSessionEntity> findByCreatedAtBetween(@Param("from") java.time.Instant from,
                                                   @Param("to") java.time.Instant to);
}
