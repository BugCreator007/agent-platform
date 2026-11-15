package com.agentplatform.modules.system.domain.model;

/**
 * 审计日志领域模型（只读视图）。
 *
 * <p>位于领域层，记录对平台的关键写操作。由审计过滤器/服务写入，
 * 管理页面按时间倒序展示；不记录密码、Token、Cookie 等敏感内容。</p>
 *
 * @param who    操作人（用户名或 anonymous）
 * @param action 操作描述，如「登录成功」「POST /api/v1/xxx [200]」
 * @param object 操作对象（资源路径或业务对象名）
 * @param time   发生时间（已格式化的展示文本）
 * @param ip     客户端 IP
 */
public record AuditLog(String who, String action, String object, String time, String ip) {
}
