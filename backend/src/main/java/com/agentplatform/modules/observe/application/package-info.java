/**
 * 可观测模块应用层。
 *
 * <p>提供运营概览聚合、告警规则定时评估与企微通知、告警状态流转、
 * 成本汇总等用例服务，以及概览响应的查询 DTO。
 * 应用层负责跨模块只读数据聚合与口径计算，不直接访问其他模块的 domain/infrastructure。</p>
 */
package com.agentplatform.modules.observe.application;
