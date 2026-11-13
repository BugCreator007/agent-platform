package com.agentplatform.modules.system.infrastructure.persistence;

import com.agentplatform.modules.system.domain.model.ApiKey;
import com.agentplatform.modules.system.domain.model.AuditLog;
import com.agentplatform.modules.system.domain.model.Member;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SystemSeedData {
    private final List<Member> members = new ArrayList<>();
    private final List<ApiKey> keys = new ArrayList<>();
    private final List<AuditLog> audit = new ArrayList<>();

    public SystemSeedData() {
        members.add(new Member("顾先路", "guxianlu@zhihe.cn", "平台管理员", 3, "在线"));
        members.add(new Member("李婧", "lijing@zhihe.cn", "开发者", 2, "12 分钟前"));
        members.add(new Member("周越", "zhouyue@zhihe.cn", "开发者", 2, "1 小时前"));
        members.add(new Member("陈昊", "chenhao@zhihe.cn", "只读成员", 1, "昨天"));
        members.add(new Member("王可", "wangke@zhihe.cn", "评测员", 0, "3 天前"));
        members.add(new Member("赵敏", "zhaomin@zhihe.cn", "只读成员", 0, "1 周前"));
        keys.add(new ApiKey("生产服务密钥", "ak_live_9f2c••••••••7a1e", "全部 API", "2026-09-02", "2 小时前", "顾先路"));
        keys.add(new ApiKey("预发测试密钥", "ak_test_4b8d••••••••c930", "仅预发环境", "2026-10-18", "3 天前", "李婧"));
        audit.add(new AuditLog("顾先路", "发布 v25 至灰度 20%", "售后工单助手", "今天 12:04", "10.12.3.41"));
        audit.add(new AuditLog("李婧", "提交 v12 发布审批", "退款审核 Copilot", "今天 11:32", "10.12.8.17"));
        audit.add(new AuditLog("顾先路", "修改系统提示词", "售后工单助手", "今天 09:42", "10.12.3.41"));
        audit.add(new AuditLog("李婧", "认领告警", "知识库召回率下降", "今天 13:52", "10.12.8.17"));
        audit.add(new AuditLog("周越", "创建智能体", "投放素材生成", "昨天 18:20", "10.12.5.88"));
    }

    public List<Member> members() { return List.copyOf(members); }
    public Optional<Member> member(String name) {
        return members.stream().filter(m -> m.name().equals(name)).findFirst();
    }
    public void saveMember(Member member) {
        if (members.stream().noneMatch(m -> m.name().equals(member.name()))) members.add(member);
    }
    public List<ApiKey> keys() { return List.copyOf(keys); }
    public void saveKey(ApiKey key) { keys.add(0, key); }
    public List<AuditLog> audit() { return List.copyOf(audit); }
    public void saveAudit(AuditLog auditLog) { audit.add(0, auditLog); }
}
