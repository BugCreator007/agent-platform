package com.agentplatform.modules.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.agentplatform.modules.chat.infrastructure.persistence.entity.MessageFeedbackEntity;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * 消息反馈表 MyBatis-Plus Mapper（infrastructure 层），过滤查询定义在 XML 中。
 */
public interface MessageFeedbackMapper extends BaseMapper<MessageFeedbackEntity> {
    /**
     * 按时间窗口查询反馈，可选智能体与评分过滤。
     *
     * @param agentId 智能体 ID，可为空
     * @param rating  评分，可为空
     * @param from    起始时间（含）
     * @return 按创建时间倒序的反馈
     */
    List<MessageFeedbackEntity> findFeedback(@Param("agentId") String agentId,
                                             @Param("rating") String rating,
                                             @Param("from") Instant from);

    /**
     * 分页查询反馈。
     *
     * @param agentId    智能体 ID，可为空
     * @param rating     评分，可为空
     * @param reasonCode 原因编码，可为空
     * @param from       起始时间（含）
     * @param to         截止时间（不含）
     * @param offset     偏移量
     * @param size       每页条数
     * @return 当前页反馈
     */
    List<MessageFeedbackEntity> findFeedbackPage(@Param("agentId") String agentId,
                                                 @Param("rating") String rating,
                                                 @Param("reasonCode") String reasonCode,
                                                 @Param("from") Instant from,
                                                 @Param("to") Instant to,
                                                 @Param("offset") int offset,
                                                 @Param("size") int size);

    /**
     * 统计过滤条件下的反馈总数。
     *
     * @param agentId    智能体 ID，可为空
     * @param rating     评分，可为空
     * @param reasonCode 原因编码，可为空
     * @param from       起始时间（含）
     * @param to         截止时间（不含）
     * @return 总条数
     */
    long countFeedback(@Param("agentId") String agentId,
                       @Param("rating") String rating,
                       @Param("reasonCode") String reasonCode,
                       @Param("from") Instant from,
                       @Param("to") Instant to);
}
