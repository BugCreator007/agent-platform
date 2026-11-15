package com.agentplatform.modules.portal.application.query;

import com.agentplatform.modules.agent.application.query.AgentSummary;

import java.util.List;

/**
 * 用户端展示的智能体信息：只暴露对话需要的字段，不包含运营指标和提示词全文。
 */
public record UserAgentView(
        String id, String name, String code, String team, String description,
        String model, String status, List<String> tools, int knowledgeBases
) {
    public static UserAgentView from(AgentSummary agent) {
        return new UserAgentView(
                agent.id(), agent.name(), agent.code(), agent.team(),
                summarize(agent.prompt()), agent.model(), agent.status(),
                agent.tools() == null ? List.of() : List.copyOf(agent.tools()),
                agent.kb() == null ? 0 : agent.kb().size());
    }

    private static String summarize(String prompt) {
        if (prompt == null || prompt.isBlank()) return "随时为你解答业务问题。";
        String text = prompt.strip().split("\\r?\\n")[0]
                .replaceAll("^你是?", "")
                .replaceAll("[。.!！]$", "");
        return text.length() > 80 ? text.substring(0, 80) + "…" : text;
    }
}
