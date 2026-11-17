/**
 * 产品洞察上下文：埋点事件、后续反馈洞察的归属模块。
 *
 * <p>当前只包含 B0 埋点；B2 会话洞察顺延到下一迭代。
 * 该模块不依赖 chat，避免与 chat → insight 的埋点调用形成循环。</p>
 */
package com.agentplatform.modules.insight;
