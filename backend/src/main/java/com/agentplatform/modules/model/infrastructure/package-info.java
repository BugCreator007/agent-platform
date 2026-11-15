/**
 * 模型模块基础设施层。
 *
 * <p>提供 MyBatis-Plus 持久化实现与首次启动种子数据：entity 负责表映射，
 * mapper 继承 {@code BaseMapper}，repository 包装单表读写，
 * {@code MybatisModelRepository} 作为领域端口适配器聚合多个 Repository 并负责密钥加解密。</p>
 */
package com.agentplatform.modules.model.infrastructure;
