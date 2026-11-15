package com.agentplatform.modules.agent.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建智能体的请求体（interfaces 层 DTO）。
 * <p>由控制器使用 {@code @Valid} 触发校验；可选字段留空时由应用服务填充默认值。</p>
 *
 * @param name       智能体名称，必填，最长 120 字符
 * @param code       业务编码，必填且全局唯一，最长 64 字符
 * @param model      模型展示名称，可选；为空时使用默认演示模型
 * @param providerId 模型厂商标识，可选；非空时才会绑定模型
 * @param modelId    厂商侧模型标识，可选，通常与 providerId 一起传入
 * @param owner      负责人，可选；为空时使用默认负责人
 * @param team       所属团队，可选；为空时使用默认团队
 * @param prompt     系统提示词，可选；为空时使用默认提示词
 * @param tools      工具编码列表，可选
 * @param kb         知识库编码列表，可选
 */
public record CreateAgentRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 64) String code,
        String model,
        String providerId,
        String modelId,
        String owner,
        String team,
        String prompt,
        java.util.List<String> tools,
        java.util.List<String> kb
) {
}
