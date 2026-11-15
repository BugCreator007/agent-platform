package com.agentplatform.modules.knowledge.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DatasetEntity;

/**
 * 数据集 MyBatis-Plus Mapper。
 *
 * <p>继承 {@link BaseMapper} 获得基础 CRUD；当前无额外自定义 SQL。</p>
 */
public interface DatasetMapper extends BaseMapper<DatasetEntity> {
}
