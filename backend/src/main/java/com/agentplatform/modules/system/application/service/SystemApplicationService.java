package com.agentplatform.modules.system.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.system.domain.model.ApiKey;
import com.agentplatform.modules.system.domain.model.AuditLog;
import com.agentplatform.modules.system.domain.model.Member;
import com.agentplatform.modules.system.domain.repository.SystemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemApplicationService {
    private final SystemRepository repository;

    public SystemApplicationService(SystemRepository repository) {
        this.repository = repository;
    }

    public List<Member> members() { return repository.members(); }
    public List<ApiKey> keys() { return repository.keys(); }
    public List<AuditLog> audit() { return repository.audit(); }

    public Member invite(String name, String email, String role) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "成员姓名不能为空");
        }
        Member member = new Member(name, email == null ? "" : email,
                role == null || role.isBlank() ? "开发者" : role, 0, "刚刚");
        repository.saveMember(member);
        return member;
    }

    public ApiKey createKey(String name, String scope, String by) {
        String raw = "ak_live_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        ApiKey key = new ApiKey(name == null || name.isBlank() ? "新密钥" : name, raw,
                scope == null || scope.isBlank() ? "runtime:read" : scope,
                java.time.LocalDate.now().toString(), "从未使用", by == null ? "顾先路" : by);
        repository.saveKey(key);
        return key;
    }

    public Member changeRole(String name, String role) {
        Member member = repository.member(name)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "成员不存在: " + name));
        member.changeRole(role);
        repository.saveMember(member);
        return member;
    }
}
