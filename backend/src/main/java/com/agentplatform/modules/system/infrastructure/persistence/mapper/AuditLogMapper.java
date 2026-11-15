package com.agentplatform.modules.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.system.infrastructure.persistence.entity.AuditLogEntity;

/**
 * 审计日志 MyBatis-Plus Mapper。
 *
 * <p>位于基础设施持久化层，继承 {@link BaseMapper} 获得标准 CRUD；
 * 倒序分页等查询通过上层 Repository 的 Wrapper 组合完成。</p>
 */
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
}
