package com.agentplatform.modules.tool.infrastructure.persistence.repository;

import com.agentplatform.modules.tool.infrastructure.persistence.mapper.McpServerMapper;

import com.agentplatform.framework.security.SecretCipher;
import com.agentplatform.modules.tool.domain.model.McpServer;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpServerEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MCP 服务仓储的 MyBatis-Plus 适配器（infrastructure 层）。
 *
 * <p>实现 domain 层 {@code McpServerRepository} 端口：在实体与领域对象之间转换，
 * 并借助 {@link SecretCipher} 完成鉴权令牌的加密落库与解密读取。
 */
@Repository
public class McpServerRepository implements com.agentplatform.modules.tool.domain.repository.McpServerRepository {
    /** MyBatis-Plus Mapper。 */
    private final McpServerMapper mapper;
    /** 密钥加解密器。 */
    private final SecretCipher cipher;

    /**
     * 构造仓储适配器。
     *
     * @param mapper MyBatis-Plus Mapper
     * @param cipher 密钥加解密器
     */
    public McpServerRepository(McpServerMapper mapper, SecretCipher cipher) { this.mapper = mapper; this.cipher = cipher; }

    /**
     * 查询全部服务并转换为领域对象（令牌已解密）。
     *
     * @return 服务列表
     */
    @Override public List<McpServer> findAll() { return mapper.selectList(null).stream().map(e -> e.toDomain(cipher)).toList(); }

    /**
     * 按主键查询服务。
     *
     * @param id 服务主键
     * @return 服务；不存在时返回 {@link Optional#empty()}
     */
    @Override public Optional<McpServer> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(e -> e.toDomain(cipher)); }

    /**
     * 插入或更新服务（令牌加密后落库）。
     *
     * @param server 服务领域对象
     */
    @Override public void save(McpServer server) { mapper.insertOrUpdate(McpServerEntity.from(server, cipher)); }

    /**
     * 按主键删除服务。
     *
     * @param id 服务主键
     */
    @Override public void deleteById(String id) { mapper.deleteById(id); }
}
