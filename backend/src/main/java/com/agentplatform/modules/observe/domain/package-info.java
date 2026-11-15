/**
 * 可观测模块领域层。
 *
 * <p>包含告警、告警规则与成本汇总等领域模型及仓储端口。
 * 告警状态（active/ack/resolved）的流转规则定义在 {@code Alert} 聚合内，
 * 应用层只调用领域方法完成状态变更。</p>
 */
package com.agentplatform.modules.observe.domain;
