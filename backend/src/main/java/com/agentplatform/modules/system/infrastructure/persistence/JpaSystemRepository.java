package com.agentplatform.modules.system.infrastructure.persistence;

import com.agentplatform.modules.system.domain.model.ApiKey;
import com.agentplatform.modules.system.domain.model.AuditLog;
import com.agentplatform.modules.system.domain.model.Member;
import com.agentplatform.modules.system.domain.repository.SystemRepository;
import com.agentplatform.modules.system.infrastructure.persistence.entity.ApiKeyEntity;
import com.agentplatform.modules.system.infrastructure.persistence.entity.AuditLogEntity;
import com.agentplatform.modules.system.infrastructure.persistence.entity.MemberEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
public class JpaSystemRepository implements SystemRepository {
    private final MemberJpaRepository members;
    private final ApiKeyJpaRepository keys;
    private final AuditLogJpaRepository audit;

    public JpaSystemRepository(MemberJpaRepository members, ApiKeyJpaRepository keys, AuditLogJpaRepository audit) {
        this.members = members; this.keys = keys; this.audit = audit;
    }

    @Override public List<Member> members() { return members.findAll().stream().map(MemberEntity::toDomain).toList(); }
    @Override public Optional<Member> member(String name) { return members.findById(name).map(MemberEntity::toDomain); }
    @Override public void saveMember(Member member) { members.save(MemberEntity.from(member)); }
    @Override public List<ApiKey> keys() { return keys.findAll().stream().map(ApiKeyEntity::toDomain).toList(); }
    @Override public void saveKey(ApiKey key) { keys.save(ApiKeyEntity.from(key)); }
    @Override public List<AuditLog> audit() {
        return audit.findAll(PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "id")))
                .stream().map(AuditLogEntity::toDomain).toList();
    }
    @Override public void saveAudit(AuditLog auditLog) { audit.save(AuditLogEntity.from(auditLog)); }
}
