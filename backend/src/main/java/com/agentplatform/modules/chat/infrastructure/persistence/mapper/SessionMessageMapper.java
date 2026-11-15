package com.agentplatform.modules.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.chat.infrastructure.persistence.entity.SessionMessageEntity;
import java.util.List;

/**
 * 会话消息表 MyBatis-Plus Mapper（infrastructure 层），SQL 定义在对应 XML 中。
 */
public interface SessionMessageMapper extends BaseMapper<SessionMessageEntity> {
    /**
     * 查询某会话的全部消息，按创建时间升序。
     *
     * @param sessionId 会话 ID
     * @return 消息实体列表
     */
    List<SessionMessageEntity> findBySessionIdOrderByCreatedAtAsc(@Param("sessionId") String sessionId);

    /**
     * 删除某会话的全部消息。
     *
     * @param sessionId 会话 ID
     * @return 删除行数
     */
    int deleteBySessionId(@Param("sessionId") String sessionId);
}
