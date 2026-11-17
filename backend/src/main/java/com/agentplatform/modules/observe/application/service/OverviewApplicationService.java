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
 *
 * <p>统计口径固定使用 Asia/Shanghai 时区：按天分组、当日/昨日对比、本月与上月
 * 同期对比都以该时区日界为准，避免服务器/浏览器时区差异导致跨天误差。
 * 成本单位为元：token 数 × 输出单价 ÷ 1,000,000。</p>
 */
@Service
public class OverviewApplicationService {
    /** 统一统计时区：中国标准时间，保证“今天/本月”的口径一致。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 趋势图日期标签格式。 */
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");
    /** 吞吐图分钟标签格式。 */
    private static final DateTimeFormatter MINUTE_LABEL = DateTimeFormatter.ofPattern("HH:mm");
    /** 快照生成时间格式。 */
    private static final DateTimeFormatter SNAPSHOT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 会话成功状态值。 */
    private static final String SUCCESS = "success";
    /** 视为失败的会话状态集合，用于成功率/错误率统计。 */
    private static final Set<String> FAILED = Set.of("failed", "timeout", "error");
    /** 视为“在线”的智能体状态集合。 */
    private static final Set<String> ONLINE = Set.of("running", "grayscale");
    /** 价格口径：每百万 token 的金额。 */
    private static final double TOKENS_PER_YUAN_UNIT = 1_000_000.0;

    private final AgentApplicationService agents;
    private final AlertApplicationService alerts;
    private final ReleaseApplicationService releases;
    private final SessionAnalyticsService analytics;
    private final ModelCatalog models;

    /**
     * 构造概览聚合服务。
     *
     * @param agents    智能体应用服务（目录数据）
     * @param alerts    告警应用服务
     * @param releases  发布应用服务
     * @param analytics 会话分析服务（真实会话事实）
     * @param models    模型目录（价格来源）
     */
    public OverviewApplicationService(AgentApplicationService agents, AlertApplicationService alerts,
                                      ReleaseApplicationService releases, SessionAnalyticsService analytics,
                                      ModelCatalog models) {
        this.agents = agents;
        this.alerts = alerts;
        this.releases = releases;
        this.analytics = analytics;
        this.models = models;
    }

    /**
     * 生成运营概览。
     *
     * @param rangeDays 统计天数；<=0 时默认 7 天，最大 90 天
     * @return 概览响应（KPI、实时脉搏、趋势、快照、智能体指标、告警、发布）
     */
    public OverviewResponse overview(int rangeDays) {
        int days = rangeDays <= 0 ? 7 : Math.min(rangeDays, 90);
        LocalDate today = LocalDate.now(ZONE);
        Instant now = Instant.now();
        LocalDate startDate = today.minusDays(days - 1L);
        // 统计窗口起点取起始日 00:00（东八区），终点取“现在”后一秒以包含当前时刻数据
        Instant rangeStart = startDate.atStartOfDay(ZONE).toInstant();
        List<SessionFact> facts = analytics.sessions(rangeStart, now.plusSeconds(1));
        List<AgentSummary> agentList = agents.list(null, null);
        PriceBook prices = priceBook(agentList);

        // 按东八区自然日分组，保持日期顺序
        Map<LocalDate, List<SessionFact>> byDay = facts.stream()
                .collect(Collectors.groupingBy(f -> f.createdAt().atZone(ZONE).toLocalDate(),
                        LinkedHashMap::new, Collectors.toList()));
        List<SessionFact> todayFacts = byDay.getOrDefault(today, List.of());
        List<SessionFact> yesterdayFacts = byDay.getOrDefault(today.minusDays(1), List.of());

        LocalDate monthStart = today.withDayOfMonth(1);
        Instant monthStartInstant = monthStart.atStartOfDay(ZONE).toInstant();
        List<SessionFact> monthFacts = analytics.sessions(monthStartInstant, now.plusSeconds(1));

        // 上月同期：起点为上月 1 日，终点取“本月已流逝时长”与“上月最后一天”的较小值，
        // 保证是等长区间对比，避免拿半个月和整月比较
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

    /**
     * 计算顶部 KPI 与变化值。
     *
     * @param today     今日会话
     * @param yesterday 昨日会话（对比基准）
     * @param month     本月会话
     * @param prevMonth 上月同期会话（等长区间）
     * @param prices    价格表
     * @return KPI 指标
     */
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

        // 无已完成会话时成功率/P95 无意义，展示 “—” 而不是 0
        String successRate = completed(today).isEmpty() ? "—" : String.format(Locale.CHINA, "%.1f%%", todayRate);
        String p95 = completed(today).isEmpty() ? "—" : duration(todayP95);
        String successDelta = completed(today).isEmpty() || completed(yesterday).isEmpty()
                ? "—" : signed(todayRate - yesterdayRate, "pt", 1);
        // P95 差值换算成秒展示（毫秒差 / 1000）
        String p95Delta = completed(today).isEmpty() || completed(yesterday).isEmpty()
                ? "—" : signed((todayP95 - yesterdayP95) / 1000.0, "s", 2);

        return new OverviewResponse.Kpi(
                number(todaySessions), percentDelta(todaySessions, yesterdaySessions),
                successRate, successDelta, p95, p95Delta,
                money(monthCost), monthCost <= 0 && prevCost <= 0 ? "0.0%"
                        : prevCost <= 0 ? "—" : percentDelta(monthCost, prevCost));
    }

    /**
     * 计算实时脉搏：近 1 小时错误率/P99、近半小时运行会话、近 60 分钟吞吐。
     *
     * @param facts     统计窗口内全部会话
     * @param now       当前时刻
     * @param agentList 智能体目录
     * @return 脉搏指标
     */
    private OverviewResponse.Pulse pulse(List<SessionFact> facts, Instant now, List<AgentSummary> agentList) {
        Instant hourAgo = now.minus(Duration.ofHours(1));
        Instant halfHourAgo = now.minus(Duration.ofMinutes(30));
        List<SessionFact> recent = facts.stream()
                .filter(f -> f.createdAt() != null && !f.createdAt().isBefore(hourAgo))
                .toList();
        // “运行中”只统计近 30 分钟内创建的会话，避免历史卡住的 running 记录虚高
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

        // 按“分钟”（截断秒和纳秒）统计会话数，用东八区时间对齐分钟标签
        Map<LocalDateTime, Long> perMinute = recent.stream()
                .collect(Collectors.groupingBy(
                        f -> f.createdAt().atZone(ZONE).withSecond(0).withNano(0).toLocalDateTime(),
                        Collectors.counting()));
        List<Long> throughput = new ArrayList<>(60);
        List<String> labels = new ArrayList<>(60);
        ZonedDateTime cursor = now.atZone(ZONE).withSecond(0).withNano(0);
        // 从 59 分钟前补到当前分钟，缺失分钟补 0，保证前端有连续 60 个点
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

    /**
     * 构造按天趋势序列：缺失日期补 0，保证每个序列长度等于统计天数。
     *
     * @param startDate 起始日期（东八区）
     * @param days      天数
     * @param byDay     已按东八区自然日分组的会话
     * @param prices    价格表
     * @return 趋势序列
     */
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

    /**
     * 重新计算每个智能体的运营指标（会话数、成功率、P95、成本），并按会话数降序排序。
     *
     * @param agentList 智能体目录
     * @param facts     统计窗口内会话
     * @param prices    价格表
     * @return 带指标的智能体列表
     */
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
            // 无已完成会话时延迟展示 “—”
            String latency = completed.isEmpty() ? "—" : duration(percentile(durations(agentFacts), 0.95));
            double cost = totalCost(agentFacts, prices);
            result.add(new AgentSummary(agent.id(), agent.name(), agent.code(), agent.status(),
                    agent.version(), agent.model(), agent.providerId(), agent.modelId(),
                    agent.owner(), agent.team(), agent.release(), sessions,
                    round(rate), latency, round(cost), agent.updated(),
                    agent.prompt(), agent.tools(), agent.kb(), agent.citationKb(), agent.answerMode()));
        }
        // 会话多的排前面，同量级按名称排序，保证列表稳定
        result.sort(Comparator.comparingLong(AgentSummary::sessions).reversed()
                .thenComparing(AgentSummary::name));
        return result;
    }

    /**
     * 构建“智能体 → 输出单价”的价格表。
     *
     * <p>匹配顺序：模型 ID → 平台模型名 → 上游模型名，任一命中即可估算成本。</p>
     *
     * @param agentList 智能体目录
     * @return 价格表
     */
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

    /**
     * 仅在键非空且价格为正时写入，避免 0 价格或空键覆盖有效价格。
     */
    private static void putIfPresent(Map<String, Double> map, String key, double value) {
        if (key != null && !key.isBlank() && value > 0) {
            map.putIfAbsent(key, value);
        }
    }

    /**
     * 估算会话成本：token 数 × 智能体绑定模型的输出单价 ÷ 1,000,000。
     * 未匹配到价格的智能体不计成本。
     *
     * @param facts  会话
     * @param prices 价格表
     * @return 成本合计（元）
     */
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

    /**
     * 过滤出已结束的会话（success 或 failed/timeout/error），用于成功率与延迟统计。
     */
    private static List<SessionFact> completed(List<SessionFact> facts) {
        return facts.stream()
                .filter(f -> f.status() != null)
                .filter(f -> SUCCESS.equalsIgnoreCase(f.status())
                        || FAILED.contains(f.status().toLowerCase(Locale.ROOT)))
                .toList();
    }

    /**
     * 计算成功率（成功会话数 / 已完成会话数 × 100）。
     *
     * @param facts 会话
     * @return 百分比；无已完成会话时返回 0
     */
    private static double successRate(List<SessionFact> facts) {
        List<SessionFact> done = completed(facts);
        if (done.isEmpty()) return 0;
        long success = done.stream().filter(f -> SUCCESS.equalsIgnoreCase(f.status())).count();
        return success * 100.0 / done.size();
    }

    /**
     * 提取已完成且耗时大于 0 的会话耗时（毫秒），用于分位数计算。
     */
    private static List<Long> durations(List<SessionFact> facts) {
        return completed(facts).stream().map(SessionFact::ms).filter(ms -> ms > 0).toList();
    }

    /**
     * 计算分位数（最近秩法）：向上取整定位下标，单样本时直接返回该值。
     *
     * @param values     样本值
     * @param percentile 分位点，如 0.95
     * @return 分位值；样本为空时返回 0
     */
    private static long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    /**
     * 取两个时刻中较早的一个。
     */
    private static Instant minInstant(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    /**
     * 会话数千分位格式化。
     */
    private static String number(long value) {
        return String.format(Locale.CHINA, "%,d", value);
    }

    /**
     * 金额格式化：千元以上省略小数，千元以下保留两位。
     */
    private static String money(double value) {
        return value >= 1000
                ? String.format(Locale.CHINA, "¥%,.0f", value)
                : String.format(Locale.CHINA, "¥%,.2f", value);
    }

    /**
     * 耗时格式化：1 秒以上显示秒（两位小数），否则显示毫秒。
     */
    private static String duration(long ms) {
        return ms >= 1000 ? String.format(Locale.CHINA, "%.2fs", ms / 1000.0) : ms + " ms";
    }

    /**
     * 百分比变化：基数为 0 时无法计算，当前值也为 0 返回 0.0%，否则视为 +100.0%。
     */
    private static String percentDelta(double current, double previous) {
        if (previous <= 0) return current <= 0 ? "0.0%" : "+100.0%";
        return signed((current - previous) * 100.0 / previous, "%", 1);
    }

    /**
     * 带符号数值格式化（正数补 “+”，负数自带 “-”）。
     */
    private static String signed(double value, String unit, int decimals) {
        String format = "%." + decimals + "f";
        return (value >= 0 ? "+" : "") + String.format(Locale.CHINA, format, value) + unit;
    }

    /**
     * 四舍五入保留两位小数。
     */
    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 价格表：智能体 ID → 输出单价（每百万 token）。
     */
    private record PriceBook(Map<String, Double> agentPrices) {
    }
}
