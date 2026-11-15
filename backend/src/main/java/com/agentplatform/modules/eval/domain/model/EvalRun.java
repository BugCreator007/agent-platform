package com.agentplatform.modules.eval.domain.model;

/**
 * 单次评测运行记录（domain 层值对象）。
 * <p>记录某个评测集对指定智能体版本的一次执行结果，用于发布门禁与页面展示。
 * 时间与通过数为展示用文本，不参与精确计算。</p>
 *
 * @param id       运行记录唯一标识（形如 {@code run_xxxxxx}）
 * @param suite    评测集名称快照
 * @param agent    被评测的智能体名称
 * @param version  被评测的版本号
 * @param passed   通过用例数展示文本（如 {@code 302/320}）
 * @param score    总得分（百分比）
 * @param duration 耗时展示文本（如 {@code 6m 12s}）
 * @param time     运行时间展示文本（如 {@code 14:20}、{@code 昨天 18:12}）
 * @param status   运行状态（{@code success}/{@code failed}）
 */
public record EvalRun(
        String id, String suite, String agent, String version, String passed,
        double score, String duration, String time, String status
) {
}
