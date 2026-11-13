package com.agentplatform.modules.system.domain.repository;

import com.agentplatform.modules.system.domain.model.ApiKey;
import com.agentplatform.modules.system.domain.model.AuditLog;
import com.agentplatform.modules.system.domain.model.Member;

import java.util.List;
import java.util.Optional;

public interface SystemRepository {
    List<Member> members();
    Optional<Member> member(String name);
    void saveMember(Member member);
    List<ApiKey> keys();
    void saveKey(ApiKey key);
    List<AuditLog> audit();
    void saveAudit(AuditLog auditLog);
}
