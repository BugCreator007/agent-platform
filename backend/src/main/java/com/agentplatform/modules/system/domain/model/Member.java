package com.agentplatform.modules.system.domain.model;

/**
 * 平台成员领域模型。
 *
 * <p>位于领域层，表示团队成员及其角色；{@code role} 可在成员生命周期内变更，
 * 其余字段创建后不可变。</p>
 */
public class Member {
    /** 成员姓名，同时作为唯一标识 */
    private final String name;
    /** 邮箱 */
    private final String email;
    /** 成员角色（如 平台管理员/开发者/只读成员），可变 */
    private String role;
    /** 名下智能体数量（展示用统计） */
    private final int agents;
    /** 最近活跃时间（展示文案） */
    private final String last;

    /**
     * @param name   成员姓名
     * @param email  邮箱
     * @param role   初始角色
     * @param agents 名下智能体数量
     * @param last   最近活跃时间
     */
    public Member(String name, String email, String role, int agents, String last) {
        this.name = name; this.email = email; this.role = role; this.agents = agents; this.last = last;
    }

    /**
     * 变更成员角色。
     *
     * @param role 新角色
     */
    public void changeRole(String role) { this.role = role; }
    /** @return 成员姓名 */
    public String name() { return name; }
    /** @return 邮箱 */
    public String email() { return email; }
    /** @return 当前角色 */
    public String role() { return role; }
    /** @return 名下智能体数量 */
    public int agents() { return agents; }
    /** @return 最近活跃时间展示文案 */
    public String last() { return last; }
}
