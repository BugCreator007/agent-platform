package com.agentplatform.modules.chat.domain.model;

/**
 * 一次回答的检索诊断快照（领域模型）。
 *
 * <p>用于回答质量复盘与后续 KQ-2 缺口分析：记录本轮命中数量、最高分、
 * 实际检索模式以及是否 0 命中。分数与模式来自知识库检索结果，
 * 不参与回答生成，只作为可解释性元数据。</p>
 *
 * @param hitCount 本轮注入的上下文条数（多知识库合计）
 * @param topScore 命中的最高分；无命中时为 0
 * @param mode     实际检索模式（vector / keyword / hybrid）；无知识库时为空
 * @param noHit    是否绑定知识库但本轮 0 命中
 */
public record RetrievalDiagnostics(int hitCount, double topScore, String mode, boolean noHit) {
}
