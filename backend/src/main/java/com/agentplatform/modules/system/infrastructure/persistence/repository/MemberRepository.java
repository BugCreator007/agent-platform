package com.agentplatform.modules.system.infrastructure.persistence.repository;

import com.agentplatform.modules.system.infrastructure.persistence.mapper.MemberMapper;

import com.agentplatform.modules.system.infrastructure.persistence.entity.MemberEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 成员数据访问仓储（Mapper 包装）。
 *
 * <p>位于基础设施持久化层，封装 {@link MemberMapper}，只操作实体。</p>
 */
@Repository
public class MemberRepository {
    private final MemberMapper mapper;

    /**
     * @param mapper 成员 Mapper
     */
    public MemberRepository(MemberMapper mapper) { this.mapper = mapper; }

    /**
     * 查询全部成员。
     *
     * @return 实体列表
     */
    public List<MemberEntity> findAll() { return mapper.selectList(null); }

    /**
     * 按主键（成员姓名）查找成员。
     *
     * @param name 成员姓名
     * @return 成员实体（可能为空）
     */
    public Optional<MemberEntity> findById(String name) { return Optional.ofNullable(mapper.selectById(name)); }

    /**
     * 新增或更新成员（按主键判断）。
     *
     * @param member 成员实体
     */
    public void save(MemberEntity member) { mapper.insertOrUpdate(member); }
}
