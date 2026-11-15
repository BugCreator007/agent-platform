/**
 * 模型模块领域层。
 *
 * <p>存放模型厂商限界上下文的核心领域模型（{@code Provider}、{@code ModelInfo}、
 * {@code RoutingPolicy}）与仓储端口。领域层不依赖 Spring 持久化实现，
 * 由基础设施层提供适配器，保证业务规则（默认模型唯一、密钥脱敏、路由有效性）留在领域内。</p>
 */
package com.agentplatform.modules.model.domain;
