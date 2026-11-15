package com.agentplatform.modules.chat.application.query;

import java.time.Instant;
import java.util.List;

/**
 * 会话分析读端口（application 层出站端口）。
 *
 * <p>用于隔离具体持久化实现，由 infrastructure 层的 MyBatis 适配器实现；
 * 跨模块调用方只依赖本端口即可获取会话事实，不感知数据库细节。</p>
 */
public interface SessionAnalyticsPort {
    /**
     * 查询创建时间落在给定窗口内的会话事实。
     *
     * @param from 窗口起始时间（含）
     * @param to   窗口结束时间（不含）
     * @return 会话读模型列表；无数据时返回空列表
     */
    List<SessionFact> sessionsBetween(Instant from, Instant to);
}
