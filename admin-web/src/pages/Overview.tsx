import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api, type Overview } from '../api'
import { Icon } from '../components/icons'
import {
  Avatar,
  Button,
  LineChart,
  Meter,
  PageHead,
  Panel,
  Sparkline,
  StatusBadge,
  toast,
} from '../components/ui'

const EMPTY_SERIES = {
  labels: [] as string[],
  sessions: [] as number[],
  tools: [] as number[],
  success: [] as number[],
  p95: [] as number[],
  cost: [] as number[],
}

function deltaTone(value: string, higherIsBetter: boolean) {
  if (!value || value === '—' || value.startsWith('0')) return ''
  const negative = value.startsWith('-')
  const good = higherIsBetter ? !negative : negative
  return good ? 'up' : 'down'
}

export function OverviewPage() {
  const navigate = useNavigate()
  const [range, setRange] = useState(7)
  const [showTable, setShowTable] = useState(false)
  const { data, isLoading, isError } = useQuery({
    queryKey: ['overview', range],
    queryFn: () => api.get<Overview>(`/overview?range=${range}`),
    refetchInterval: 60000,
  })

  const chart = data?.series ?? EMPTY_SERIES
  const agents = useMemo(() => data?.agents ?? [], [data])
  const health = agents.filter((a) => ['running', 'grayscale', 'paused'].includes(a.status)).slice(0, 5)
  const activeAlerts = data?.alerts.filter((a) => a.status === 'active') ?? []
  const pendingReleases = data?.releases.filter((r) => ['approve', 'canary', 'draft'].includes(r.stage)) ?? []
  const throughput = data?.pulse.throughput ?? []
  const recentSessions = throughput.reduce((sum, value) => sum + value, 0)
  const tailCount = Math.max(0, agents.length - 5)
  const tailSessions = agents.slice(5).reduce((sum, a) => sum + a.sessions, 0)

  const exportReport = () => {
    const rows = [
      ['指标', '数值'],
      ['今日会话', data?.kpi.todaySessions ?? ''],
      ['任务成功率', data?.kpi.successRate ?? ''],
      ['P95 响应', data?.kpi.p95 ?? ''],
      ['本月模型成本', data?.kpi.cost ?? ''],
      [],
      ['日期', '会话数', '工具调用', '成功率', 'P95(ms)', '成本(元)'],
      ...chart.labels.map((label, i) => [
        label,
        chart.sessions[i] ?? 0,
        chart.tools[i] ?? 0,
        (chart.success[i] ?? 0).toFixed(1),
        chart.p95[i] ?? 0,
        (chart.cost[i] ?? 0).toFixed(2),
      ]),
    ]
    const csv = rows.map((r) => r.join(',')).join('\n')
    const url = URL.createObjectURL(new Blob([`﻿${csv}`], { type: 'text/csv;charset=utf-8' }))
    const a = document.createElement('a')
    a.href = url
    a.download = 'agent-platform-overview.csv'
    a.click()
    URL.revokeObjectURL(url)
    toast('报表已导出')
  }

  if (isLoading) {
    return <Panel><div className="empty">加载运营数据…</div></Panel>
  }
  if (isError || !data) {
    return <Panel><div className="empty">运营数据加载失败，请稍后重试</div></Panel>
  }

  return (
    <>
      <PageHead
        title="运营概览"
        sub={`数据每 60 秒刷新 · 最近更新 ${data.snapshot.generatedAt}`}
      >
        <div className="seg">
          {[7, 14, 30].map((r) => (
            <button key={r} className={range === r ? 'active' : ''} onClick={() => setRange(r)}>近 {r} 天</button>
          ))}
        </div>
        <Button onClick={exportReport}><Icon name="download" />导出报表</Button>
      </PageHead>

      <section className="pulse" aria-label="实时运行脉搏">
        <div>
          <div className="pulse-label">
            <span className="live"><span className="pulse-dot" />实时脉搏</span>
            <span>最近 60 分钟 · 生产环境</span>
          </div>
          <div className="pulse-main">
            <span className="big">{data.pulse.runningSessions}</span>
            <span className="unit">个会话正在运行，近 30 分钟活跃</span>
          </div>
          <div className="pulse-stats">
            <div className="pulse-stat"><div className="lb">在线智能体</div><div className="vl">{data.pulse.onlineAgents}</div></div>
            <div className="pulse-stat"><div className="lb">近 1 小时会话</div><div className="vl">{recentSessions}</div></div>
            <div className="pulse-stat"><div className="lb">错误率</div><div className="vl">{data.pulse.errorRate}</div></div>
            <div className="pulse-stat"><div className="lb">P99 响应</div><div className="vl">{data.pulse.p99}</div></div>
          </div>
        </div>
        <div className="pulse-chart">
          <div className="cap"><span>请求吞吐</span><span>峰值 {data.pulse.peakRpm} rpm</span></div>
          <PulseChart values={throughput} />
          <div className="axis"><span>60 分钟前</span><span>30 分钟前</span><span>现在</span></div>
        </div>
      </section>

      <div className="kpi-row">
        <article className="panel stat hero-stat">
          <div className="lb"><Icon name="sessions" />今日会话</div>
          <div className="row" style={{ alignItems: 'baseline', gap: 10 }}>
            <div className="vl">{data.kpi.todaySessions}</div>
            <span className={`badge ${data.kpi.sessionsDelta.startsWith('-') ? 'bad' : 'good'}`}>
              <span className="bd" />{data.kpi.sessionsDelta}
            </span>
          </div>
          <div className="spark"><Sparkline values={chart.sessions} width={220} height={46} /></div>
          <div className="foot">较昨日 {data.kpi.sessionsDelta}，共 {chart.sessions.reduce((a, b) => a + b, 0)} 次会话</div>
        </article>
        <article className="panel stat">
          <div className="lb"><Icon name="check-circle" />任务成功率</div>
          <div className="vl">{data.kpi.successRate}</div>
          <div className="delta"><span className={deltaTone(data.kpi.successDelta, true)}>{data.kpi.successDelta}</span><span>较昨日</span></div>
          <div className="spark"><Sparkline values={chart.success} color="#12A150" width={92} height={32} /></div>
        </article>
        <article className="panel stat">
          <div className="lb"><Icon name="clock" />P95 响应</div>
          <div className="vl">{data.kpi.p95}</div>
          <div className="delta"><span className={deltaTone(data.kpi.p95Delta, false)}>{data.kpi.p95Delta}</span><span>较昨日</span></div>
          <div className="spark"><Sparkline values={chart.p95} color="#2A78D6" width={92} height={32} /></div>
        </article>
        <article className="panel stat">
          <div className="lb"><Icon name="cost" />本月模型成本</div>
          <div className="vl">{data.kpi.cost}</div>
          <div className="delta"><span className={deltaTone(data.kpi.costDelta, false)}>{data.kpi.costDelta}</span><span>较上月同期</span></div>
          <div className="spark"><Sparkline values={chart.cost} color="#D99A2B" width={92} height={32} /></div>
        </article>
      </div>

      <div className="grid-2 mt-3">
        <Panel
          title="会话与工具调用"
          sub="按天统计 · 全部智能体"
          tools={<button className="chip" onClick={() => setShowTable((v) => !v)}><Icon name="list" />{showTable ? '图表' : '表格'}</button>}
        >
          {showTable ? (
            <div className="table-wrap">
              <table className="data">
                <thead><tr><th>日期</th><th className="num">会话数</th><th className="num">工具调用</th></tr></thead>
                <tbody>
                  {chart.labels.map((label, i) => (
                    <tr key={label}><td>{label}</td><td className="num">{chart.sessions[i].toLocaleString()}</td><td className="num">{chart.tools[i].toLocaleString()}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <LineChart
              labels={chart.labels}
              series={[
                { name: '会话数', color: 'var(--s1)', values: chart.sessions },
                { name: '工具调用', color: 'var(--s2)', values: chart.tools },
              ]}
            />
          )}
        </Panel>

        <Panel title="智能体调用分布" sub={`近 ${range} 天会话量 · Top 5`} footer={<><span>{tailCount > 0 ? `其余 ${tailCount} 个智能体贡献 ${tailSessions.toLocaleString()} 次会话` : '全部智能体已展示'}</span><span className="spacer" /><Button kind="ghost" size="sm" onClick={() => navigate('/agents')}>查看全部<Icon name="arrow-right" /></Button></>}>
          {agents.some((a) => a.sessions > 0) ? (
            <div className="bar-list">
              {agents.slice(0, 5).map((a, i) => {
                const max = agents[0]?.sessions || 1
                const total = agents.reduce((sum, x) => sum + x.sessions, 0) || 1
                return (
                  <div className="bar-row" key={a.id}>
                    <div className="bar-top">
                      <span className="bar-name">{a.name}</span>
                      <span className="bar-val">{a.sessions.toLocaleString()}</span>
                      <span className="bar-pct">{((a.sessions / total) * 100).toFixed(1)}%</span>
                    </div>
                    <div className="bar-track">
                      <div className={`bar-fill ${['', 'blue', 'amber', 'aqua', 'violet'][i]}`} style={{ width: `${(a.sessions / max) * 100}%` }} />
                    </div>
                  </div>
                )
              })}
            </div>
          ) : (
            <div className="empty">近 {range} 天还没有会话记录</div>
          )}
        </Panel>
      </div>

      <div className="grid-2 mt-3">
        <Panel
          title="智能体健康度"
          sub="在线智能体的成功率与延迟"
          tools={<Button kind="ghost" size="sm" onClick={() => navigate('/agents')}>全部智能体<Icon name="arrow-right" /></Button>}
          noBody
        >
          <div className="table-wrap">
            <table className="data">
              <thead><tr><th>智能体</th><th>状态</th><th className="num">近 {range} 天会话</th><th className="num">成功率</th><th className="num">P95</th><th /></tr></thead>
              <tbody>
                {health.map((a) => (
                  <tr key={a.id} className="clickable" onClick={() => navigate('/agents')}>
                    <td>
                      <div className="entity">
                        <Avatar text={a.name.slice(0, 1)} tone={['blue', 'amber', 'aqua', 'violet', 'orange'][a.name.length % 5]} />
                        <div><div className="nm">{a.name}</div><div className="sub">{a.code} · {a.version}</div></div>
                      </div>
                    </td>
                    <td><StatusBadge status={a.status} /></td>
                    <td className="num">{a.sessions.toLocaleString()}</td>
                    <td className="num">{a.sessions === 0 ? '—' : <><Meter value={a.success} tone={a.success < 96 ? 'danger' : ''} />{a.success.toFixed(1)}%</>}</td>
                    <td className="num">{a.p95}</td>
                    <td className="num" style={{ color: '#B2BAB2' }}><Icon name="chevron-right" /></td>
                  </tr>
                ))}
                {!health.length && <tr><td colSpan={6}><div className="empty">暂无在线智能体</div></td></tr>}
              </tbody>
            </table>
          </div>
        </Panel>

        <div className="stack">
          <Panel
            title="最近告警"
            sub={`${activeAlerts.length} 条待处理`}
            tools={<Button kind="ghost" size="sm" onClick={() => navigate('/alerts')}>处理<Icon name="arrow-right" /></Button>}
            bodyStyle={{ paddingTop: 4 }}
          >
            {activeAlerts.slice(0, 3).map((a) => (
              <div key={a.id} className="alert-item" onClick={() => navigate('/alerts')}>
                <div className={`alert-icon ${a.severity}`}><Icon name={a.severity === 'critical' ? 'alert-triangle' : 'clock'} /></div>
                <div style={{ minWidth: 0 }}>
                  <div className="al-title">{a.title}</div>
                  <div className="al-meta">{a.target}<span>·</span>{a.ago}</div>
                </div>
                <div className="al-actions"><StatusBadge status={a.status} /></div>
              </div>
            ))}
            {!activeAlerts.length && <div className="muted" style={{ padding: '10px 0' }}>暂无活跃告警</div>}
          </Panel>
          <Panel title="待办发布" sub={`今天有 ${pendingReleases.length} 个版本等待处理`} bodyStyle={{ paddingTop: 4 }}>
            {pendingReleases.slice(0, 3).map((r) => (
              <div key={r.id} className="alert-item" onClick={() => navigate('/release')}>
                <div className="alert-icon info"><Icon name="releases" /></div>
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div className="al-title">{r.agentName} · {r.version}</div>
                  <div className="al-meta">{r.owner} 提交 · 评测得分 {r.evalScore ?? '—'}</div>
                </div>
                <div className="al-actions"><StatusBadge status={r.stage} /></div>
              </div>
            ))}
            {!pendingReleases.length && <div className="muted" style={{ padding: '10px 0' }}>暂无待办发布</div>}
          </Panel>
        </div>
      </div>
    </>
  )
}

function PulseChart({ values }: { values: number[] }) {
  const width = 320
  const height = 86
  const max = Math.max(1, ...values)
  const points = values.map((v, i) => [
    values.length <= 1 ? 0 : (i / (values.length - 1)) * width,
    height - 6 - (v / max) * (height - 14),
  ])
  const d = points.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join(' ')
  const area = `${d} L ${width} ${height} L 0 ${height} Z`
  const last = points[points.length - 1] ?? [width, height - 6]
  return (
    <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <linearGradient id="pulseFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#E8B54B" stopOpacity=".22" />
          <stop offset="100%" stopColor="#E8B54B" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill="url(#pulseFill)" />
      <path d={d} fill="none" stroke="#E8B54B" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx={last[0]} cy={last[1]} r="3.5" fill="#E8B54B" stroke="#101A15" strokeWidth="2" />
    </svg>
  )
}
