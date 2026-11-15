package com.agentplatform.modules.chat.domain.model;

/**
 * 会话执行链路中的单个步骤（领域值对象），用于运营侧查看模型/工具/知识检索过程。
 *
 * @param kind   步骤类型：model（模型）、tool（工具）、kb（知识检索）等
 * @param name   步骤名称或工具名
 * @param ms     该步骤耗时（毫秒）
 * @param tokens 该步骤消耗的 token 估算值
 * @param status 步骤状态：ok、fail、skip
 * @param detail 步骤说明，例如路由信息、召回条数、错误原因
 */
public record TraceStep(String kind, String name, long ms, int tokens, String status, String detail) {
}
