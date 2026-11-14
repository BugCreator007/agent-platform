package com.agentplatform.modules.observe.application.service;

import com.agentplatform.modules.agent.application.query.AgentSummary;
import com.agentplatform.modules.agent.application.service.AgentApplicationService;
import com.agentplatform.modules.chat.application.query.SessionAnalyticsService;
import com.agentplatform.modules.chat.application.query.SessionFact;
import com.agentplatform.modules.model.application.query.ModelCatalog;
import com.agentplatform.modules.model.application.query.ModelRoute;
import com.agentplatform.modules.observe.application.query.OverviewResponse;
import com.agentplatform.modules.observe.domain.model.Alert;
import com.agentplatform.modules.release.application.service.ReleaseApplicationService;
import com.agentplatform.modules.release.domain.model.Release;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运营概览聚合：全部指标基于 chat_sessions 的真实记录计算，
 * 成本按会话 token 数与智能体绑定模型的价格估算。
 */
@Service
public class OverviewApplicationService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter MINUTE_LABEL = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SNAPSHOT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SUCCESS = "success";
    private static final Set<String> FAILED = Set.of("failed", "timeout", "error");
    private static final Set<String> ONLINE = Set.of("running", "grayscale");
    private static final double TOKENS_PER_YUAN_UNIT = 1_000_000.0;

    private final AgentApplicationService agents;
    private final AlertApplicationService alerts;
    private final ReleaseApplicationService releases;
    private final SessionAnalyticsService analytics;
    private final ModelCatalog models;

    public OverviewApplicationService(AgentApplicationService agents, AlertApplicationService alerts,
                                      ReleaseApplicationService releases, SessionAnalyticsService analytics,
                                      ModelCatalog models) {
        this.agents = agents;
        this.alerts = alerts;
        this.releases = releases;
        this.analytics = analytics;
        this.models = models;
    }

    public OverviewResponse overview(int rangeDays) {
        int days = rangeDays <= 0 ? 7 : Math.min(rangeDays, 90);
        LocalDate today = LocalDate.now(ZONE);
        Instant now = Instant.now();
        LocalDate startDate = today.minusDays(days - 1L);
        Instant rangeStart = startDate.atStartOfDay(ZONE).toInstant();
        List<SessionFact> facts = analytics.sessions(rangeStart, now.plusSeconds(1));
        List<AgentSummary> agentList = agents.list(null, null);
        PriceBook prices = priceBook(agentList);

        Map<LocalDate, List<SessionFact>> byDay = facts.stream()
                .collect(Collectors.groupingBy(f -> f.createdAt().atZone(ZONE).toLocalDate(),
                        LinkedHashMap::new, Collectors.toList()));
        List<SessionFact> todayFacts = byDay.getOrDefault(today, List.of());
        List<SessionFact> yesterdayFacts = byDay.getOrDefault(today.minusDays(1), List.of());

        LocalDate monthStart = today.withDayOfMonth(1);
        Instant monthStartInstant = monthStart.atStartOfDay(ZONE).toInstant();
        List<SessionFact> monthFacts = analytics.sessions(monthStartInstant, now.plusSeconds(1));

        LocalDate prevMonthStart = monthStart.minusMonths(1);
        LocalDate prevMonthEnd = monthStart.minusDays(1);
        Instant elapsedEnd = minInstant(prevMonthStart.atStartOfDay(ZONE)
                .plus(Duration.between(monthStartInstant, now)).toInstant(),
                prevMonthEnd.plusDays(1).atStartOfDay(ZONE).toInstant());
        List<SessionFact> prevMonthFacts = analytics.sessions(
                prevMonthStart.atStartOfDay(ZONE).toInstant(), elapsedEnd);

        OverviewResponse.Kpi kpi = kpi(todayFacts, yesterdayFacts, monthFacts, prevMonthFacts, prices);
        OverviewResponse.Pulse pulse = pulse(facts, now, agentList);
        OverviewResponse.Series series = series(startDate, days, byDay, prices);
        List<AgentSummary> agentMetrics = agentMetrics(agentList, facts, prices);
        List<Alert> alertList = alerts.list(null);
        List<Release> releaseList = releases.list();

        return new OverviewResponse(kpi, pulse, series,
                new OverviewResponse.Snapshot(LocalDateTime.now(ZONE).format(SNAPSHOT), days),
                agentMetrics, alertList, releaseList);
    }

    private OverviewResponse.Kpi kpi(List<SessionFact> today, List<SessionFact> yesterday,
                                     List<SessionFact> month, List<SessionFact> prevMonth,
                                     PriceBook prices) {
        long todaySessions = today.size();
        long yesterdaySessions = yesterday.size();
        double todayRate = successRate(today);
        double yesterdayRate = successRate(yesterday);
        long todayP95 = percentile(durations(today), 0.95);
        long yesterdayP95 = percentile(durations(yesterday), 0.95);
        double monthCost = totalCost(month, prices);
        double prevCost = totalCost(prevMonth, prices);

        String successRate = completed(today).isEmpty() ? "—" : String.format(Locale.CHINA, "%.1f%%", todayRate);
        String p95 = completed(today).isEmpty() ? "—" : duration(todayP95);
        String successDelta = completed(today).isEmpty() || completed(yesterday).isEmpty()
                ? "—" : signed(todayRate - yesterdayRate, "pt", 1);
        String p95Delta = completed(today).isEmpty() || completed(yesterday).isEmpty()
                ? "—" : signed((todayP95 - yesterdayP95) / 1000.0, "s", 2);

        return new OverviewResponse.Kpi(
                number(todaySessions), percentDelta(todaySessions, yesterdaySessions),
                successRate, successDelta, p95, p95Delta,
                money(monthCost), monthCost <= 0 && prevCost <= 0 ? "0.0%"
                        : prevCost <= 0 ? "—" : percentDelta(monthCost, prevCost));
    }

    private OverviewResponse.Pulse pulse(List<SessionFact> facts, Instant now, List<AgentSummary> agentList) {
        Instant hourAgo = now.minus(Duration.ofHours(1));
        Instant halfHourAgo = now.minus(Duration.ofMinutes(30));
        List<SessionFact> recent = facts.stream()
                .filter(f -> f.createdAt() != null && !f.createdAt().isBefore(hourAgo))
                .toList();
        long running = facts.stream()
                .filter(f -> "running".equalsIgnoreCase(f.status()))
                .filter(f -> f.createdAt() != null && !f.createdAt().isBefore(halfHourAgo))
                .count();
        long onlineAgents = agentList.stream()
                .filter(a -> ONLINE.contains(a.status()))
                .count();
        List<SessionFact> completedRecent = completed(recent);
        double errorRate = completedRecent.isEmpty() ? 0
                : completedRecent.stream().filter(f -> FAILED.contains(f.status().toLowerCase(Locale.ROOT))).count()
                * 100.0 / completedRecent.size();
        long p99 = percentile(durations(recent), 0.99);

        Map<LocalDateTime, Long> perMinute = recent.stream()
                .collect(Collectors.groupingBy(
                        f -> f.createdAt().atZone(ZONE).withSecond(0).withNano(0).toLocalDateTime(),
                        Collectors.counting()));
        List<Long> throughput = new ArrayList<>(60);
        List<String> labels = new ArrayList<>(60);
        ZonedDateTime cursor = now.atZone(ZONE).withSecond(0).withNano(0);
        for (int i = 59; i >= 0; i--) {
            LocalDateTime minute = cursor.minusMinutes(i).toLocalDateTime();
            labels.add(minute.format(MINUTE_LABEL));
            throughput.add(perMinute.getOrDefault(minute, 0L));
        }
        long peak = throughput.stream().mapToLong(Long::longValue).max().orElse(0);

        return new OverviewResponse.Pulse(running, onlineAgents,
                completedRecent.isEmpty() ? "—" : String.format(Locale.CHINA, "%.1f%%", errorRate),
                completedRecent.isEmpty() ? "—" : duration(p99), peak, throughput, labels);
    }

    private OverviewResponse.Series series(LocalDate startDate, int days,
                                           Map<LocalDate, List<SessionFact>> byDay, PriceBook prices) {
        List<String> labels = new ArrayList<>(days);
        List<Long> sessions = new ArrayList<>(days);
        List<Long> tools = new ArrayList<>(days);
        List<Double> success = new ArrayList<>(days);
        List<Long> p95 = new ArrayList<>(days);
        List<Double> cost = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            List<SessionFact> dayFacts = byDay.getOrDefault(date, List.of());
            labels.add(date.format(DAY_LABEL));
            sessions.add((long) dayFacts.size());
            tools.add(dayFacts.stream().mapToLong(SessionFact::tools).sum());
            success.add(round(successRate(dayFacts)));
            p95.add(percentile(durations(dayFacts), 0.95));
            cost.add(round(totalCost(dayFacts, prices)));
        }
        return new OverviewResponse.Series(labels, sessions, tools, success, p95, cost);
    }

    private List<AgentSummary> agentMetrics(List<AgentSummary> agentList, List<SessionFact> facts,
                                            PriceBook prices) {
        Map<String, List<SessionFact>> byAgent = facts.stream()
                .filter(f -> f.agentId() != null)
                .collect(Collectors.groupingBy(SessionFact::agentId));
        List<AgentSummary> result = new ArrayList<>();
        for (AgentSummary agent : agentList) {
            List<SessionFact> agentFacts = byAgent.getOrDefault(agent.id(), List.of());
            List<SessionFact> completed = completed(agentFacts);
            long sessions = agentFacts.size();
            double rate = successRate(agentFacts);
            String latency = completed.isEmpty() ? "—" : duration(percentile(durations(agentFacts), 0.95));
            double cost = totalCost(agentFacts, prices);
            result.add(new AgentSummary(agent.id(), agent.name(), agent.code(), agent.status(),
                    agent.version(), agent.model(), agent.providerId(), agent.modelId(),
                    agent.owner(), agent.team(), agent.release(), sessions,
                    round(rate), latency, round(cost), agent.updated(),
                    agent.prompt(), agent.tools(), agent.kb()));
        }
        result.sort(Comparator.comparingLong(AgentSummary::sessions).reversed()
                .thenComparing(AgentSummary::name));
        return result;
    }

    private PriceBook priceBook(List<AgentSummary> agentList) {
        Map<String, Double> byModelId = new HashMap<>();
        Map<String, Double> byName = new HashMap<>();
        Map<String, Double> byUpstream = new HashMap<>();
        for (ModelRoute route : models.allRoutes()) {
            putIfPresent(byModelId, route.modelId(), route.outPrice());
            putIfPresent(byName, route.modelName(), route.outPrice());
            putIfPresent(byUpstream, route.upstreamModel(), route.outPrice());
        }
        Map<String, Double> agentPrices = new HashMap<>();
        for (AgentSummary agent : agentList) {
            Double price = null;
            if (agent.modelId() != null) price = byModelId.get(agent.modelId());
            if (price == null && agent.model() != null) price = byName.get(agent.model());
            if (price == null && agent.model() != null) price = byUpstream.get(agent.model());
            if (price != null) agentPrices.put(agent.id(), price);
        }
        return new PriceBook(agentPrices);
    }

    private static void putIfPresent(Map<String, Double> map, String key, double value) {
        if (key != null && !key.isBlank() && value > 0) {
            map.putIfAbsent(key, value);
        }
    }

    private double totalCost(List<SessionFact> facts, PriceBook prices) {
        double total = 0;
        for (SessionFact fact : facts) {
            Double price = prices.agentPrices().get(fact.agentId());
            if (price != null && fact.tokens() > 0) {
                total += fact.tokens() * price / TOKENS_PER_YUAN_UNIT;
            }
        }
        return total;
    }

    private static List<SessionFact> completed(List<SessionFact> facts) {
        return facts.stream()
                .filter(f -> f.status() != null)
                .filter(f -> SUCCESS.equalsIgnoreCase(f.status())
                        || FAILED.contains(f.status().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static double successRate(List<SessionFact> facts) {
        List<SessionFact> done = completed(facts);
        if (done.isEmpty()) return 0;
        long success = done.stream().filter(f -> SUCCESS.equalsIgnoreCase(f.status())).count();
        return success * 100.0 / done.size();
    }

    private static List<Long> durations(List<SessionFact> facts) {
        return completed(facts).stream().map(SessionFact::ms).filter(ms -> ms > 0).toList();
    }

    private static long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static Instant minInstant(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static String number(long value) {
        return String.format(Locale.CHINA, "%,d", value);
    }

    private static String money(double value) {
        return value >= 1000
                ? String.format(Locale.CHINA, "¥%,.0f", value)
                : String.format(Locale.CHINA, "¥%,.2f", value);
    }

    private static String duration(long ms) {
        return ms >= 1000 ? String.format(Locale.CHINA, "%.2fs", ms / 1000.0) : ms + " ms";
    }

    private static String percentDelta(double current, double previous) {
        if (previous <= 0) return current <= 0 ? "0.0%" : "+100.0%";
        return signed((current - previous) * 100.0 / previous, "%", 1);
    }

    private static String signed(double value, String unit, int decimals) {
        String format = "%." + decimals + "f";
        return (value >= 0 ? "+" : "") + String.format(Locale.CHINA, format, value) + unit;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record PriceBook(Map<String, Double> agentPrices) {
    }
}
