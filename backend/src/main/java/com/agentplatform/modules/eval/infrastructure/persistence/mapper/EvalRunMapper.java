package com.agentplatform.modules.eval.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalRunEntity;

/**
 * {@code eval_runs} 表的 MyBatis-Plus Mapper（infrastructure 层）。
 * <p>仅使用 {@link BaseMapper} 提供的基础 CRUD。</p>
 */
public interface EvalRunMapper extends BaseMapper<EvalRunEntity> {
}
