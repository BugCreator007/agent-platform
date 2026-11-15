package com.agentplatform.modules.eval.domain.model;

/**
 * 评测集定义（domain 层值对象）。
 * <p>描述一组评测用例及其最近一次运行结果，用于页面展示和触发评测。</p>
 *
 * @param id          评测集唯一标识（如 {@code ev-cs}）
 * @param name        评测集名称
 * @param description 覆盖范围/说明
 * @param rate        最近一次通过率（百分比）
 * @param cases       用例总数，用于计算通过用例数
 * @param last        最近运行时间展示文本
 */
public record EvalSuite(String id, String name, String description, double rate, int cases, String last) {
}
