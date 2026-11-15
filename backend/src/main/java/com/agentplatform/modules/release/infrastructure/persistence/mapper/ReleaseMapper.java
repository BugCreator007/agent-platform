package com.agentplatform.modules.release.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.release.infrastructure.persistence.entity.ReleaseEntity;

/**
 * {@code releases} 表的 MyBatis-Plus Mapper（infrastructure 层）。
 * <p>仅使用 {@link BaseMapper} 提供的基础 CRUD，无自定义 SQL。</p>
 */
public interface ReleaseMapper extends BaseMapper<ReleaseEntity> {
}
