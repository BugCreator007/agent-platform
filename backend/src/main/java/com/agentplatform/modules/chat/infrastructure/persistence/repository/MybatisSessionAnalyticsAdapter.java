package com.agentplatform.modules.chat.infrastructure.persistence.repository;

import com.agentplatform.modules.chat.application.query.SessionAnalyticsPort;
import com.agentplatform.modules.chat.application.query.SessionFact;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 会话分析读端口（{@link SessionAnalyticsPort}）的 MyBatis 适配器（infrastructure 层）。
 *
 * <p>按时间窗口查询会话实体，并裁剪为运营指标所需的轻量 {@link SessionFact}，
 * 避免把 trace 等大字段传递给调用方。</p>
 */
@Repository
public class MybatisSessionAnalyticsAdapter implements SessionAnalyticsPort {
    private final ChatSessionRepository sessions;

    public MybatisSessionAnalyticsAdapter(ChatSessionRepository sessions) {
        this.sessions = sessions;
    }

    /**
     * 查询时间窗口内的会话并转换为读模型。
     *
     * @param from 起始时间（含）
     * @param to   结束时间（不含）
     * @return 会话事实列表
     */
    @Override
    public List<SessionFact> sessionsBetween(Instant from, Instant to) {
        return sessions.findByCreatedAtBetween(from, to).stream()
                .map(e -> new SessionFact(e.getAgentId(), e.getAgentName(), e.getStatus(),
                        e.getTools(), e.getTokens(), e.getMs(), e.getCreatedAt()))
                .toList();
    }
}
