package com.agentplatform.modules.tool.infrastructure.persistence.repository;

import com.agentplatform.modules.tool.infrastructure.persistence.mapper.McpServerMapper;

import com.agentplatform.framework.security.SecretCipher;
import com.agentplatform.modules.tool.domain.model.McpServer;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpServerEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class McpServerRepository implements com.agentplatform.modules.tool.domain.repository.McpServerRepository {
    private final McpServerMapper mapper;
    private final SecretCipher cipher;
    public McpServerRepository(McpServerMapper mapper, SecretCipher cipher) { this.mapper = mapper; this.cipher = cipher; }
    @Override public List<McpServer> findAll() { return mapper.selectList(null).stream().map(e -> e.toDomain(cipher)).toList(); }
    @Override public Optional<McpServer> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(e -> e.toDomain(cipher)); }
    @Override public void save(McpServer server) { mapper.insertOrUpdate(McpServerEntity.from(server, cipher)); }
    @Override public void deleteById(String id) { mapper.deleteById(id); }
}
