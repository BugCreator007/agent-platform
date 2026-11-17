package com.agentplatform.modules.chat.domain.model;

import java.util.List;

/**
 * 会话聚合根（领域模型）。
 *
 * <p>表示一次智能体会话的汇总状态，运行结束时由运行时服务整体覆盖保存；
 * 每次对话还会单独持久化 {@link SessionMessage}，本记录只保留汇总与 trace。</p>
 *
 * @param id        会话 ID，形如 {@code s_xxxxxxxx}
 * @param agentId   所属智能体 ID
 * @param agentName 所属智能体名称（冗余保存，便于列表展示）
 * @param status    会话状态：running、success、failed、timeout、error
 * @param turns     对话轮次
 * @param tools     工具调用次数
 * @param tokens    累计 token 估算值
 * @param ms        本次运行耗时（毫秒）
 * @param time      展示用时间标签，格式 HH:mm:ss
 * @param topic     会话主题，默认取首条用户消息
 * @param trace     执行链路步骤，持久化为 JSON
 * @param createdBy 会话创建人登录名；历史会话为 null
 */
public record ChatSession(
        String id, String agentId, String agentName, String status, int turns,
        int tools, int tokens, long ms, String time, String topic, List<TraceStep> trace,
        String createdBy
) {
    /**
     * 兼容旧调用方的构造器：不携带会话归属。
     *
     * @param id        会话 ID
     * @param agentId   智能体 ID
     * @param agentName 智能体名称
     * @param status    会话状态
     * @param turns     轮次
     * @param tools     工具调用次数
     * @param tokens    累计 token
     * @param ms        耗时
     * @param time      展示时间
     * @param topic     主题
     * @param trace     执行轨迹
     */
    public ChatSession(String id, String agentId, String agentName, String status, int turns,
                       int tools, int tokens, long ms, String time, String topic, List<TraceStep> trace) {
        this(id, agentId, agentName, status, turns, tools, tokens, ms, time, topic, trace, null);
    }
}
