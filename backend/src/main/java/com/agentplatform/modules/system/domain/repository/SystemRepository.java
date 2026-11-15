package com.agentplatform.modules.system.domain.repository;

import com.agentplatform.modules.system.domain.model.ApiKey;
import com.agentplatform.modules.system.domain.model.AuditLog;
import com.agentplatform.modules.system.domain.model.Member;

import java.util.List;
import java.util.Optional;

/**
 * 平台系统仓储端口。
 *
 * <p>位于领域层，抽象成员、API Key 与审计日志的持久化能力；
 * 由 infrastructure 层的 {@code MybatisSystemRepository} 适配实现，领域与用例不感知数据库细节。</p>
 */
public interface SystemRepository {
    /**
     * 查询全部成员。
     *
     * @return 成员列表
     */
    List<Member> members();

    /**
     * 按姓名查找成员。
     *
     * @param name 成员姓名
     * @return 成员（可能为空）
     */
    Optional<Member> member(String name);

    /**
     * 新增或更新成员（按姓名去重）。
     *
     * @param member 成员
     */
    void saveMember(Member member);

    /**
     * 查询全部 API Key。
     *
     * @return 密钥列表
     */
    List<ApiKey> keys();

    /**
     * 新增或更新 API Key。
     *
     * @param key 密钥记录
     */
    void saveKey(ApiKey key);

    /**
     * 查询最近审计日志（按时间倒序，最多 200 条）。
     *
     * @return 审计日志列表
     */
    List<AuditLog> audit();

    /**
     * 写入一条审计日志。
     *
     * @param auditLog 审计记录
     */
    void saveAudit(AuditLog auditLog);
}
