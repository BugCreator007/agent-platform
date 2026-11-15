/**
 * 发布领域层：发布记录聚合与仓储端口。
 * <p>发布状态机（draft/approve/canary/live/rollback）与流量约束由聚合根保证。</p>
 */
package com.agentplatform.modules.release.domain;
