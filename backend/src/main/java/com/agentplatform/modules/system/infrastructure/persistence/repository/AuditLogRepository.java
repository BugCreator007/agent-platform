package com.agentplatform.modules.system.infrastructure.persistence.repository;

import com.agentplatform.modules.system.infrastructure.persistence.mapper.AuditLogMapper;

import com.agentplatform.modules.system.infrastructure.persistence.entity.AuditLogEntity;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AuditLogRepository {
    private final AuditLogMapper mapper;

    public AuditLogRepository(AuditLogMapper mapper) { this.mapper = mapper; }

    public List<AuditLogEntity> recent(int limit) {
        return mapper.selectList(Wrappers.<AuditLogEntity>query().orderByDesc("id").last("limit " + limit));
    }
    public void save(AuditLogEntity log) { mapper.insertOrUpdate(log); }
}
