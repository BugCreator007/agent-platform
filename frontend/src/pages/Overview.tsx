import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api, type Overview } from '../api'
import { Icon } from '../components/icons'
import {
  Avatar,
  Badge,
  Button,
  LineChart,
  Meter,
  PageHead,
  Panel,
  Sparkline,
  StatusBadge,
  toast,
} from '../components/ui'

const RANGE_SERIES: Record<number, { labels: string[]; sessions: number[]; tools: number[] }> = {
  7: {
    labels: ['11-07', '11-08', '11-09', '11-10', '11-11', '11-12', '11-13'],
    sessions: [612, 684, 741, 706, 842, 905, 1018],
    tools: [410, 462, 501, 488, 592, 641, 728],
  },
  14: {
    labels: dateLabels(14),
    sessions: [560, 610, 592, 640, 688, 705, 662, 710, 760, 742, 810, 880, 920, 1018],
    tools: [380, 420, 402, 445, 470, 492, 455, 501, 530, 512, 570, 618, 650, 728],
  },
  30: {
    labels: dateLabels(30),
    sessions: [420, 455, 438, 470, 512, 498, 530, 560, 545, 580, 610, 592, 640, 688, 705, 662, 710, 760, 742, 810, 790, 840, 880, 860, 920, 950, 930, 980, 1010, 1040],
    tools: [280, 302, 290, 315, 340, 332, 355, 380, 366, 392, 410, 402, 445, 470, 492, 455, 501, 530, 512, 570, 552, 598, 618, 602, 650, 678, 662, 700, 718, 740],
  },
}

function dateLabels(n: number) {
  const end = new Date(2026, 10, 13)
  return Array.from({ length: n }, (_, i) => {
    const d = new Date(end)
    d.setDate(end.getDate() - (n - 1 - i))
    return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  })
}

const PULSE = [42, 48, 45, 52, 49, 58, 55, 62, 60, 67, 64, 72, 69, 76, 74, 81, 78, 85, 82, 88, 86, 91, 89, 93, 90, 95, 92, 96, 94, 97, 95, 98, 96, 99, 97, 100]

export function OverviewPage() {
  const navigate = useNavigate()
  const [range, setRange] = useState(7)
  const [showTable, setShowTable] = useState(false)
  const { data, isLoading } = useQuery({ queryKey: ['overview'], queryFn: () => api.get<Overview>('/overview') })

  const chart = RANGE_SERIES[range]
  const agents = useMemo(() => [...(data?.agents ?? [])].sort((a, b) => b.sessions - a.sessions), [data])
  const health = agents.filter((a) => ['running', 'grayscale', 'paused'].includes(a.status)).slice(0, 5)
  const activeAlerts = data?.alerts.filter((a) => a.status === 'active') ?? []
  const pendingReleases = data?.releases.filter((r) => ['approve', 'canary', 'draft'].includes(r.stage)) ?? []

  const exportReport = () => {
    const rows = [
      ['指标', '数值'],
      ['今日会话', data?.kpi.todaySessions ?? ''],
      ['任务成功率', data?.kpi.successRate ?? ''],
      ['P95 首响', data?.kpi.p95 ?? ''],
      ['本月模型成本', data?.kpi.cost ?? ''],
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

  return (
    <>
      <PageHead
        title="运营概览"
        sub="掌握智能体运行、工具调用与模型成本的实时情况。数据每 60 秒刷新，当前为 14:32 的快照。"
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
            <span>最近 60 秒 · 生产环境</span>
          </div>
          <div className="pulse-main">
            <span className="big">38</span>
            <span className="unit">个会话正在运行，全部链路正常</span>
          </div>
          <div className="pulse-stats">
            <div className="pulse-stat"><div className="lb">在线智能体</div><div className="vl">6 <span className="mini-dot" /></div></div>
            <div className="pulse-stat"><div className="lb">排队任务</div><div className="vl">0</div></div>
            <div className="pulse-stat"><div className="lb">错误率</div><div className="vl">0.4% <span className="mini-dot warn" /></div></div>
            <div className="pulse-stat"><div className="lb">网关 P99</div><div className="vl">86 ms</div></div>
          </div>
        </div>
        <div className="pulse-chart">
          <div className="cap"><span>请求吞吐</span><span>峰值 1,284 rpm</span></div>
          <PulseChart />
          <div className="axis"><span>60 秒前</span><span>30 秒前</span><span>现在</span></div>
        </div>
      </section>

      <div className="kpi-row">
        <article className="panel stat hero-stat">
          <div className="lb"><Icon name="sessions" />今日会话</div>
          <div className="row" style={{ alignItems: 'baseline', gap: 10 }}>
            <div className="vl">{data?.kpi.todaySessions ?? '—'}</div>
            <span className="badge good"><span className="bd" />{data?.kpi.sessionsDelta ?? '+0%'}</span>
          </div>
          <div className="spark"><Sparkline values={chart.sessions} width={220} height={46} /></div>
          <div className="foot">较上一周期增长 2.8%，其中 28% 来自售后工单助手</div>
        </article>
        <article className="panel stat">
          <div className="lb"><Icon name="check-circle" />任务成功率</div>
          <div className="vl">{data?.kpi.successRate ?? '—'}</div>
          <div className="delta"><span className="up">↑ +0.4pt</span><span>较上周期</span></div>
          <div className="spark"><Sparkline values={[96.9, 97.2, 97.8, 97.5, 98.1, 98.2, 98.6]} color="#12A150" width={92} height={32} /></div>
        </article>
        <article className="panel stat">
          <div className="lb"><Icon name="clock" />P95 首响</div>
          <div className="vl">{data?.kpi.p95 ?? '—'}</div>
          <div className="delta"><span className="down">↓ -0.18s</span><span>较上周期</span></div>
          <div className="spark"><Sparkline values={[1.62, 1.58, 1.51, 1.54, 1.42, 1.38, 1.24]} color="#2A78D6" width={92} height={32} /></div>
        </article>
        <article className="panel stat">
          <div className="lb"><Icon name="cost" />本月模型成本</div>
          <div className="vl">{data?.kpi.cost ?? '—'}</div>
          <div className="delta"><span className="bad">↑ {data?.kpi.costDelta ?? ''}</span><span>较上月同期</span></div>
          <div className="spark"><Sparkline values={[9800, 10100, 10400, 10700, 11200, 11800, 12480]} color="#D99A2B" width={92} height={32} /></div>
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

        <Panel title="智能体调用分布" sub="近 7 天会话量 · Top 5" footer={<><span>长尾 3 个智能体贡献 1,349 次会话</span><span className="spacer" /><Button kind="ghost" size="sm" onClick={() => navigate('/agents')}>查看全部<Icon name="arrow-right" /></Button></>}>
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
              <thead><tr><th>智能体</th><th>状态</th><th className="num">今日会话</th><th className="num">成功率</th><th className="num">P95</th><th /></tr></thead>
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
                    <td className="num"><Meter value={a.success} tone={a.success < 96 ? 'danger' : ''} />{a.success}%</td>
                    <td className="num">{a.p95}</td>
                    <td className="num" style={{ color: '#B2BAB2' }}><Icon name="chevron-right" /></td>
                  </tr>
                ))}
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

function PulseChart() {
  const width = 320
  const height = 86
  const max = 110
  const points = PULSE.map((v, i) => [(i / (PULSE.length - 1)) * width, height - 6 - (v / max) * (height - 14)])
  const d = points.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join(' ')
  const area = `${d} L ${width} ${height} L 0 ${height} Z`
  const last = points[points.length - 1]
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
