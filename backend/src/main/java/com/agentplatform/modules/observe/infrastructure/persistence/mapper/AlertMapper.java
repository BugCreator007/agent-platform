package com.agentplatform.modules.observe.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.observe.infrastructure.persistence.entity.AlertEntity;

/**
 * 告警表 MyBatis-Plus Mapper（infrastructure 层），直接复用 {@link BaseMapper} 的 CRUD 能力。
 */
public interface AlertMapper extends BaseMapper<AlertEntity> {
}
