package com.agentplatform.modules.agent.interfaces.dto;

/**
 * 更新智能体的请求体（interfaces 层 DTO）。
 * <p>所有字段均为可选，采用“局部更新”语义：null 或空白字段保持不变，
 * 非 null 的列表字段整体替换原列表。控制器不做 Bean Validation，
 * 由领域聚合的 update 方法逐字段判空处理。</p>
 *
 * @param name       新名称，null/空白表示不修改
 * @param model      新模型展示名称，null/空白表示不修改
 * @param providerId 新模型厂商标识，null 表示不修改
 * @param modelId    新厂商侧模型标识，null 表示不修改
 * @param owner      新负责人，null/空白表示不修改
 * @param team       新所属团队，null/空白表示不修改
 * @param prompt     新系统提示词，null 表示不修改（可传空串清空）
 * @param tools      新工具编码列表，null 表示不修改；非 null 时整体替换
 * @param kb         新知识库编码列表，null 表示不修改；非 null 时整体替换
 * @param citationKb 新引用知识库列表，null 表示按 kb 过滤现有配置
 * @param answerMode 回答模式：HYBRID / STRICT；null 表示不修改
 */
public record UpdateAgentRequest(
        String name,
        String model,
        String providerId,
        String modelId,
        String owner,
        String team,
        String prompt,
        java.util.List<String> tools,
        java.util.List<String> kb,
        java.util.List<String> citationKb,
        String answerMode
) {
}
