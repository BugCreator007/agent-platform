package com.agentplatform.modules.portal.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 用户端客户端事件上报请求（portal interfaces 层 DTO）。
 *
 * <p>服务端会覆盖用户身份，并按事件类型过滤属性；未知属性直接丢弃。
 * {@code eventId} 由前端生成，用于重试幂等；为空时服务端补发。</p>
 *
 * @param name       事件编码，仅允许 agent_opened / answer_copied / citation_downloaded
 * @param eventId    客户端事件 ID，可为空
 * @param sessionId  会话 ID，可为空
 * @param messageId  消息 ID，可为空
 * @param agentId    智能体 ID，可为空
 * @param properties 事件属性，仅允许标量值
 */
public record SubmitEventRequest(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 64) String eventId,
        String sessionId,
        String messageId,
        String agentId,
        Map<String, Object> properties
) {
}
