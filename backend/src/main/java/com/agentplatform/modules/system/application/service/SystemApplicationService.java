package com.agentplatform.modules.system.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.system.domain.model.ApiKey;
import com.agentplatform.modules.system.domain.model.AuditLog;
import com.agentplatform.modules.system.domain.model.Member;
import com.agentplatform.modules.system.domain.repository.SystemRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 平台系统管理用例服务。
 *
 * <p>位于应用层，编排成员、API Key 与审计日志的查询/变更。
 * 写操作由接口层的 {@code @PreAuthorize} 与审计过滤器双重约束。</p>
 */
@Service
public class SystemApplicationService {
    private final SystemRepository repository;

    /**
     * @param repository 平台系统仓储端口
     */
    public SystemApplicationService(SystemRepository repository) {
        this.repository = repository;
    }

    /**
     * 查询全部成员。
     *
     * @return 成员列表
     */
    public List<Member> members() { return repository.members(); }

    /**
     * 查询全部 API Key。
     *
     * @return 密钥列表
     */
    public List<ApiKey> keys() { return repository.keys(); }

    /**
     * 查询最近审计日志。
     *
     * @return 审计日志列表（按时间倒序）
     */
    public List<AuditLog> audit() { return repository.audit(); }

    /**
     * 邀请新成员。
     *
     * @param name  成员姓名（必填）
     * @param email 邮箱，空值转为空串
     * @param role  角色，空值默认「开发者」
     * @return 新建的成员
     * @throws BusinessException 姓名为空时抛出（400）
     */
    public Member invite(String name, String email, String role) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "成员姓名不能为空");
        }
        Member member = new Member(name, email == null ? "" : email,
                role == null || role.isBlank() ? "开发者" : role, 0, "刚刚");
        repository.saveMember(member);
        return member;
    }

    /**
     * 创建 API Key。
     *
     * <p>生成 {@code ak_live_} 前缀的随机密钥，名称/范围缺省时回退默认值。
     * 注意：返回对象包含明文密钥，前端应在创建成功后立即展示并提示妥善保存。</p>
     *
     * @param name  密钥名称，空值默认「新密钥」
     * @param scope 授权范围，空值默认 {@code runtime:read}
     * @param by    创建人，空值默认「顾先路」（演示数据）
     * @return 新建的密钥记录
     */
    public ApiKey createKey(String name, String scope, String by) {
        // 取 UUID 去连字符后的前 16 位作为随机密钥，降低可猜测性
        String raw = "ak_live_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        ApiKey key = new ApiKey(name == null || name.isBlank() ? "新密钥" : name, raw,
                scope == null || scope.isBlank() ? "runtime:read" : scope,
                java.time.LocalDate.now().toString(), "从未使用", by == null ? "顾先路" : by);
        repository.saveKey(key);
        return key;
    }

    /**
     * 变更成员角色。
     *
     * @param name 成员姓名
     * @param role 新角色
     * @return 更新后的成员
     * @throws BusinessException 成员不存在时抛出（404）
     */
    public Member changeRole(String name, String role) {
        Member member = repository.member(name)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "成员不存在: " + name));
        member.changeRole(role);
        repository.saveMember(member);
        return member;
    }
}
