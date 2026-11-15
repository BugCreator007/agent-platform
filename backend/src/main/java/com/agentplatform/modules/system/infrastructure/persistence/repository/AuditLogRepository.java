package com.agentplatform.modules.system.infrastructure.persistence.repository;

import com.agentplatform.modules.system.infrastructure.persistence.mapper.AuditLogMapper;

import com.agentplatform.modules.system.infrastructure.persistence.entity.AuditLogEntity;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 审计日志数据访问仓储（Mapper 包装）。
 *
 * <p>位于基础设施持久化层，封装 {@link AuditLogMapper}，提供按主键倒序的最近日志查询与追加写入。</p>
 */
@Repository
public class AuditLogRepository {
    private final AuditLogMapper mapper;

    /**
     * @param mapper 审计日志 Mapper
     */
    public AuditLogRepository(AuditLogMapper mapper) { this.mapper = mapper; }

    /**
     * 查询最近的审计日志。
     *
     * @param limit 最大条数（由调用方传入固定值，不来自用户输入）
     * @return 按 id 倒序的实体列表
     */
    public List<AuditLogEntity> recent(int limit) {
        // id 自增，按 id 倒序等价于按写入时间倒序；limit 为内部常量，无注入风险
        return mapper.selectList(Wrappers.<AuditLogEntity>query().orderByDesc("id").last("limit " + limit));
    }

    /**
     * 新增或更新审计日志。
     *
     * @param log 审计日志实体
     */
    public void save(AuditLogEntity log) { mapper.insertOrUpdate(log); }
}
