package com.agentplatform.modules.model.application.query;

/**
 * 厂商连通性探测结果（应用层 DTO）。
 *
 * <p>由 {@code ProviderProbeService} 发起一次最小化调用后返回，用于控制台展示
 * “测试连接”的结果。不包含任何密钥信息。</p>
 *
 * @param ok      探测是否成功（HTTP 2xx 且拿到响应）
 * @param status  探测后厂商状态（healthy/degraded），失败时同步写入厂商记录
 * @param latency 探测耗时展示值，如 “620 ms”；未发出请求时为 “—”
 * @param message 面向用户的结果说明或错误摘要（已截断）
 */
public record ProviderTestResult(boolean ok, String status, String latency, String message) {
}
