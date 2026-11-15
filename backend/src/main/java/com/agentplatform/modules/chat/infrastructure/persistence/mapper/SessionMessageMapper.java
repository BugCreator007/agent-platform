package com.agentplatform.modules.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.chat.infrastructure.persistence.entity.SessionMessageEntity;
import java.util.List;

public interface SessionMessageMapper extends BaseMapper<SessionMessageEntity> {
    List<SessionMessageEntity> findBySessionIdOrderByCreatedAtAsc(@Param("sessionId") String sessionId);

    int deleteBySessionId(@Param("sessionId") String sessionId);
}
