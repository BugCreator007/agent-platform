package com.agentplatform.modules.system.infrastructure.persistence.repository;

import com.agentplatform.modules.system.infrastructure.persistence.mapper.MemberMapper;

import com.agentplatform.modules.system.infrastructure.persistence.entity.MemberEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MemberRepository {
    private final MemberMapper mapper;

    public MemberRepository(MemberMapper mapper) { this.mapper = mapper; }

    public List<MemberEntity> findAll() { return mapper.selectList(null); }
    public Optional<MemberEntity> findById(String name) { return Optional.ofNullable(mapper.selectById(name)); }
    public void save(MemberEntity member) { mapper.insertOrUpdate(member); }
}
