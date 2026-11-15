package com.agentplatform.modules.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.chat.infrastructure.persistence.entity.ChatSessionEntity;
import java.util.List;

public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {
    List<ChatSessionEntity> findByCreatedAtBetween(@Param("from") java.time.Instant from,
                                                   @Param("to") java.time.Instant to);
}
