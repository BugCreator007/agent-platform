package com.agentplatform.modules.system.domain.model;

public class Member {
    private final String name;
    private final String email;
    private String role;
    private final int agents;
    private final String last;

    public Member(String name, String email, String role, int agents, String last) {
        this.name = name; this.email = email; this.role = role; this.agents = agents; this.last = last;
    }

    public void changeRole(String role) { this.role = role; }
    public String name() { return name; }
    public String email() { return email; }
    public String role() { return role; }
    public int agents() { return agents; }
    public String last() { return last; }
}
