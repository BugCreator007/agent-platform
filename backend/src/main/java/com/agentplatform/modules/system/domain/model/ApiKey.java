package com.agentplatform.modules.system.domain.model;

/**
 * API Key 领域模型。
 *
 * <p>位于领域层，表示发放给外部调用方的访问密钥记录（展示用信息）。
 * 注意：{@code key} 字段在种子数据中是脱敏展示值；
 * 真实密钥的完整明文仅在创建时返回一次，不应长期落库明文。</p>
 *
 * @param name    密钥名称
 * @param key     密钥值（展示形态，可能已脱敏）
 * @param scope   授权范围，如 {@code runtime:read}
 * @param created 创建日期
 * @param last    最近使用时间（展示文案）
 * @param by      创建人
 */
public record ApiKey(String name, String key, String scope, String created, String last, String by) {
}
