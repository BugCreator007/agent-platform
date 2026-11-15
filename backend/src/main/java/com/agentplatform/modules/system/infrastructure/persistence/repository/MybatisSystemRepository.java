package com.agentplatform.modules.system.infrastructure.persistence.repository;

import com.agentplatform.modules.system.domain.model.ApiKey;
import com.agentplatform.modules.system.domain.model.AuditLog;
import com.agentplatform.modules.system.domain.model.Member;
import com.agentplatform.modules.system.domain.repository.SystemRepository;
import com.agentplatform.modules.system.infrastructure.persistence.entity.ApiKeyEntity;
import com.agentplatform.modules.system.infrastructure.persistence.entity.AuditLogEntity;
import com.agentplatform.modules.system.infrastructure.persistence.entity.MemberEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link SystemRepository} 的 MyBatis-Plus 适配器。
 *
 * <p>位于基础设施持久化层，聚合成员/API Key/审计日志三个 Mapper 包装仓储，
 * 在领域模型与持久化实体之间转换，使应用层只依赖领域端口。
 * 标记 {@code @Primary}，确保存在其他实现时优先使用持久化版本。</p>
 */
@Primary
@Repository
public class MybatisSystemRepository implements SystemRepository {
    private final MemberRepository members;
    private final ApiKeyRepository keys;
    private final AuditLogRepository audit;

    /**
     * @param members 成员仓储
     * @param keys    API Key 仓储
     * @param audit   审计日志仓储
     */
    public MybatisSystemRepository(MemberRepository members, ApiKeyRepository keys, AuditLogRepository audit) {
        this.members = members; this.keys = keys; this.audit = audit;
    }

    /** {@inheritDoc} */
    @Override public List<Member> members() { return members.findAll().stream().map(MemberEntity::toDomain).toList(); }
    /** {@inheritDoc} */
    @Override public Optional<Member> member(String name) { return members.findById(name).map(MemberEntity::toDomain); }
    /** {@inheritDoc} */
    @Override public void saveMember(Member member) { members.save(MemberEntity.from(member)); }
    /** {@inheritDoc} */
    @Override public List<ApiKey> keys() { return keys.findAll().stream().map(ApiKeyEntity::toDomain).toList(); }
    /** {@inheritDoc} */
    @Override public void saveKey(ApiKey key) { keys.save(ApiKeyEntity.from(key)); }
    /** {@inheritDoc} */
    @Override public List<AuditLog> audit() { return audit.recent(200).stream().map(AuditLogEntity::toDomain).toList(); }
    /** {@inheritDoc} */
    @Override public void saveAudit(AuditLog auditLog) { audit.save(AuditLogEntity.from(auditLog)); }
}
