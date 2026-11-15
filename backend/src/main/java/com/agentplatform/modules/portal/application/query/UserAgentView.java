package com.agentplatform.modules.portal.application.query;

import com.agentplatform.modules.agent.application.query.AgentSummary;

import java.util.List;

/**
 * 用户端展示的智能体信息：只暴露对话需要的字段，不包含运营指标和提示词全文。
 *
 * @param id             智能体唯一标识
 * @param name           展示名称
 * @param code           业务编码
 * @param team           所属团队
 * @param description    由提示词首行裁剪出的简介（去除“你是”前缀与结尾标点，最长 80 字符）
 * @param model          模型展示名称
 * @param status         生命周期状态的小写形式
 * @param tools          工具编码列表
 * @param knowledgeBases 关联知识库数量（只暴露数量，不暴露具体编码）
 */
public record UserAgentView(
        String id, String name, String code, String team, String description,
        String model, String status, List<String> tools, int knowledgeBases
) {
    /**
     * 将管理端视图裁剪为用户端视图：提示词只保留摘要，知识库只保留数量。
     *
     * @param agent 管理端智能体视图
     * @return 用户端展示模型
     */
    public static UserAgentView from(AgentSummary agent) {
        return new UserAgentView(
                agent.id(), agent.name(), agent.code(), agent.team(),
                summarize(agent.prompt()), agent.model(), agent.status(),
                agent.tools() == null ? List.of() : List.copyOf(agent.tools()),
                agent.kb() == null ? 0 : agent.kb().size());
    }

    /**
     * 从系统提示词生成一句简介：取首行，去掉开头“你是/你”和结尾标点，超过 80 字截断。
     * <p>提示词为空时返回通用兜底文案，避免用户端出现空白描述。</p>
     *
     * @param prompt 系统提示词，可为 null
     * @return 适合展示的简介文本
     */
    private static String summarize(String prompt) {
        if (prompt == null || prompt.isBlank()) return "随时为你解答业务问题。";
        String text = prompt.strip().split("\\r?\\n")[0]
                .replaceAll("^你是?", "")
                .replaceAll("[。.!！]$", "");
        return text.length() > 80 ? text.substring(0, 80) + "…" : text;
    }
}
