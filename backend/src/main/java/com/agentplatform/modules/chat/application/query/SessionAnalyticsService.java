package com.agentplatform.modules.chat.application.query;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供运营概览等跨模块查询使用的会话分析入口。
 */
@Service
public class SessionAnalyticsService {
    private static final String SUCCESS = "success";
    private static final Set<String> FAILED = Set.of("failed", "timeout", "error");

    private final SessionAnalyticsPort port;

    public SessionAnalyticsService(SessionAnalyticsPort port) {
        this.port = port;
    }

    public List<SessionFact> sessions(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            return List.of();
        }
        return port.sessionsBetween(from, to);
    }

    /**
     * 按智能体汇总时间窗口内的会话数、成功率与 P95 时延。
     */
    public List<AgentSessionStat> agentStats(Instant from, Instant to) {
        Map<String, List<SessionFact>> byAgent = sessions(from, to).stream()
                .filter(f -> f.agentId() != null && !f.agentId().isBlank())
                .collect(Collectors.groupingBy(SessionFact::agentId, LinkedHashMap::new, Collectors.toList()));
        List<AgentSessionStat> stats = new ArrayList<>(byAgent.size());
        byAgent.forEach((agentId, facts) -> {
            List<SessionFact> done = facts.stream()
                    .filter(f -> f.status() != null)
                    .filter(f -> SUCCESS.equalsIgnoreCase(f.status())
                            || FAILED.contains(f.status().toLowerCase(Locale.ROOT)))
                    .toList();
            long success = done.stream().filter(f -> SUCCESS.equalsIgnoreCase(f.status())).count();
            double rate = done.isEmpty() ? 0 : Math.round(success * 1000.0 / done.size()) / 10.0;
            long p95 = percentile(done.stream().map(SessionFact::ms).filter(ms -> ms > 0).toList(), 0.95);
            String agentName = facts.stream().map(SessionFact::agentName)
                    .filter(name -> name != null && !name.isBlank()).findFirst().orElse(agentId);
            stats.add(new AgentSessionStat(agentId, agentName, facts.size(), rate,
                    done.isEmpty() ? "—" : duration(p95),
                    facts.stream().mapToLong(SessionFact::tools).sum()));
        });
        stats.sort(Comparator.comparingLong(AgentSessionStat::sessions).reversed()
                .thenComparing(AgentSessionStat::agentId));
        return stats;
    }

    private static long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static String duration(long ms) {
        return ms >= 1000 ? String.format(Locale.CHINA, "%.2fs", ms / 1000.0) : ms + " ms";
    }
}
