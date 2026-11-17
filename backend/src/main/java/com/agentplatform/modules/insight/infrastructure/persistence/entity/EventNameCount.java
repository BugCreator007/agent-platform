package com.agentplatform.modules.insight.infrastructure.persistence.entity;

/**
 * 事件名计数行（infrastructure 层查询结果）。
 *
 * <p>只用于 Mapper 的 group by 查询映射，不进入领域层。</p>
 */
public class EventNameCount {
    /** 事件编码 */
    private String eventName;
    /** 数量 */
    private long cnt;

    /** @return 事件编码 */
    public String getEventName() { return eventName; }
    /** @param eventName 事件编码 */
    public void setEventName(String eventName) { this.eventName = eventName; }
    /** @return 数量 */
    public long getCnt() { return cnt; }
    /** @param cnt 数量 */
    public void setCnt(long cnt) { this.cnt = cnt; }
}
