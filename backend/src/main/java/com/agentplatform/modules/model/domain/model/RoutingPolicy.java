package com.agentplatform.modules.model.domain.model;

import java.util.List;

/**
 * 全局模型路由策略（domain 层值对象）。
 *
 * <p>决定运行时优先调用哪个主模型、失败后按什么顺序回退，以及候选排序策略。
 * 路由规则：主模型优先；调用失败按 {@code fallback} 顺序降级；
 * {@code strategy} 为 failover 时保持配置顺序，为 cost/latency/quality 时对默认候选排序。</p>
 *
 * @param strategy 路由策略：failover（顺序故障转移）/cost（成本优先）/latency（延迟优先）/quality（质量优先）
 * @param primary  主模型 ID
 * @param fallback 回退模型 ID 列表，按优先级排列
 * @param region   部署区域展示值，如 “华东 1”
 */
public record RoutingPolicy(String strategy, String primary, List<String> fallback, String region) {
}
