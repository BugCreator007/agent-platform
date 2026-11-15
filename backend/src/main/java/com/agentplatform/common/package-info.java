/**
 * 共享内核包。
 *
 * <p>集中放置跨限界上下文复用的统一响应（{@code api}）、领域事件契约（{@code domain}）、
 * 业务错误码与异常（{@code error}）以及 MyBatis-Plus TypeHandler（{@code persistence}）。
 * 对全部模块开放，但不得反向依赖任何业务模块。</p>
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "共享内核")
package com.agentplatform.common;
