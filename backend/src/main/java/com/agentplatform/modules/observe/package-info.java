/**
 * 可观测限界上下文（Spring Modulith 模块）。
 *
 * <p>负责运营概览、告警（规则、评估、通知、确认/恢复）与成本统计。
 * 数据来源包括 chat_sessions 会话事实、Micrometer 指标、模型目录价格，
 * 统计口径统一按 Asia/Shanghai 时区。跨模块读取只依赖对方
 * application/query 或 application/service 的 NamedInterface 契约。</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "可观测")
package com.agentplatform.modules.observe;
