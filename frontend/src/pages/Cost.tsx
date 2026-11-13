import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Agent, type CostSummary } from '../api'
import { Icon } from '../components/icons'
import {
  Badge,
  Button,
  Modal,
  PageHead,
  Panel,
  Sparkline,
  toast,
} from '../components/ui'

export function CostPage() {
  const queryClient = useQueryClient()
  const [budgetOpen, setBudgetOpen] = useState(false)
  const [budget, setBudget] = useState(20000)
  const cost = useQuery({ queryKey: ['cost'], queryFn: () => api.get<CostSummary>('/cost/summary') })
  const agents = useQuery({ queryKey: ['agents'], queryFn: () => api.get<Agent[]>('/agents') })

  const data = cost.data
  const total = data?.total ?? 0
  const limit = data?.budget ?? 20000
  const pct = Math.round((total / Math.max(1, limit)) * 100)
  const daily = data?.daily ?? []

  const saveBudget = useMutation({
    mutationFn: () => api.put<CostSummary>('/cost/budget', { budget }),
    onSuccess: () => { toast('月度预算已更新'); setBudgetOpen(false); queryClient.invalidateQueries({ queryKey: ['cost'] }) },
    onError: () => { toast('预算已保存到本地（演示）'); setBudgetOpen(false) },
  })

  const exportCsv = () => {
    const rows = [['模型', '成本', '占比'], ...(data?.models ?? []).map((m) => [m.name, String(m.value), `${m.share}%`])]
    const url = URL.createObjectURL(new Blob([`﻿${rows.map((r) => r.join(',')).join('\n')}`], { type: 'text/csv;charset=utf-8' }))
    const a = document.createElement('a'); a.href = url; a.download = 'cost.csv'; a.click(); URL.revokeObjectURL(url)
    toast('成本明细已导出')
  }

  if (!data) return <Panel><div className="empty">加载成本数据…</div></Panel>

  return (
    <>
      <PageHead title="成本与用量" sub="按模型与智能体拆解 Token 成本，设置预算并提前预警。成本为估算值，按厂商账单周期结算。">
        <Button onClick={exportCsv}><Icon name="download" />导出明细</Button>
        <Button kind="primary" onClick={() => { setBudget(data.budget); setBudgetOpen(true) }}><Icon name="settings" />调整预算</Button>
      </PageHead>

      <div className="grid-4">
        <div className="panel stat hero-stat"><div className="lb"><Icon name="cost" />本月成本</div><div className="vl">¥{total.toLocaleString()}</div><div className="delta"><span className="bad">↑ 8.2%</span><span>较上月同期</span></div><div className="spark"><Sparkline values={[9800, 10100, 10400, 10700, 11200, 11800, total]} color="#D99A2B" width={190} height={40} /></div></div>
        <div className="panel stat"><div className="lb">预算使用</div><div className="vl">{pct}<small>%</small></div><div className="delta"><span>¥{total.toLocaleString()} / ¥{limit.toLocaleString()}</span></div><div className="budget-track mt-2"><i style={{ width: `${Math.min(100, pct)}%` }} className={pct > 90 ? 'danger' : pct > 75 ? 'warn' : ''} /></div></div>
        <div className="panel stat"><div className="lb">今日成本</div><div className="vl">¥{(daily[daily.length - 1]?.value ?? 0).toLocaleString()}</div><div className="delta"><span className="down">↓ 3.1%</span><span>较昨日</span></div></div>
        <div className="panel stat"><div className="lb">预计月末</div><div className="vl">¥{Math.round(total * 1.18).toLocaleString()}</div><div className="delta"><Badge tone="warn">可能超出预算</Badge></div></div>
      </div>

      <div className="grid-2 mt-3">
        <Panel title="每日成本" sub="按自然日统计" tools={<Badge tone="warn">预算警戒线 75%</Badge>}>
          <div className="bar-list">
            {daily.map((d, i) => (
              <div className="bar-row" key={d.day}>
                <div className="bar-top"><span className="bar-name">{d.day}</span><span className="bar-val">¥{d.value.toLocaleString()}</span><span className="bar-pct">{Math.round((d.value / Math.max(1, Math.max(...daily.map((x) => x.value)))) * 100)}%</span></div>
                <div className="bar-track"><div className={`bar-fill ${i === daily.length - 1 ? '' : 'blue'}`} style={{ width: `${(d.value / Math.max(1, Math.max(...daily.map((x) => x.value)))) * 100}%` }} /></div>
              </div>
            ))}
          </div>
        </Panel>

        <Panel title="按模型" sub="本月累计" noBody>
          <div className="table-wrap">
            <table className="data">
              <thead><tr><th>模型</th><th className="num">成本</th><th className="num">占比</th><th>趋势</th></tr></thead>
              <tbody>{data.models.map((m, i) => <tr key={m.name}><td>{m.name}</td><td className="num">¥{m.value.toLocaleString()}</td><td className="num">{m.share}%</td><td><Sparkline values={[m.value * 0.7, m.value * 0.78, m.value * 0.84, m.value * 0.9, m.value]} color={['#2A78D6', '#EB6834', '#1BAF7A', '#EDA100'][i % 4]} width={78} height={22} /></td></tr>)}</tbody>
            </table>
          </div>
        </Panel>
      </div>

      <Panel className="mt-3" title="按智能体" sub="近 30 天成本与预算占比" noBody>
        <div className="table-wrap">
          <table className="data">
            <thead><tr><th>智能体</th><th>负责人</th><th>模型</th><th className="num">会话</th><th className="num">成本</th><th className="num">占比</th></tr></thead>
            <tbody>{(agents.data ?? []).slice().sort((a, b) => b.cost - a.cost).map((a) => <tr key={a.id}><td><b>{a.name}</b><div className="sub">{a.code}</div></td><td>{a.owner}</td><td>{a.model}</td><td className="num">{a.sessions.toLocaleString()}</td><td className="num">¥{a.cost.toLocaleString()}</td><td className="num">{Math.round((a.cost / Math.max(1, (agents.data ?? []).reduce((s, x) => s + x.cost, 0))) * 100)}%</td></tr>)}</tbody>
          </table>
        </div>
      </Panel>

      <div className="grid-2 mt-3">
        <Panel title="节流建议" sub="按潜在节省金额排序">
          <div className="alert-item"><div className="alert-icon info"><Icon name="spark" /></div><div style={{ flex: 1 }}><div className="al-title">把发票助手的意图识别切到 Haiku</div><div className="al-meta">预计每月节省 ¥1,280 · 准确率影响 &lt; 0.3%</div></div><Button size="sm" onClick={() => toast('已应用建议，下一周期生效')}>应用</Button></div>
          <div className="alert-item"><div className="alert-icon info"><Icon name="spark" /></div><div style={{ flex: 1 }}><div className="al-title">商家经营周报开启提示词缓存</div><div className="al-meta">预计每月节省 ¥460 · 缓存命中率约 62%</div></div><Button size="sm" onClick={() => toast('已应用建议，下一周期生效')}>应用</Button></div>
          <div className="alert-item"><div className="alert-icon info"><Icon name="spark" /></div><div style={{ flex: 1 }}><div className="al-title">风控问答降低 TopK 至 4</div><div className="al-meta">预计每月节省 ¥180 · 召回率下降 0.1%</div></div><Button size="sm" onClick={() => toast('已应用建议，下一周期生效')}>应用</Button></div>
        </Panel>
        <Panel title="预算与预警" sub="当前周期：2026 年 11 月">
          <div className="kv"><dt>月度预算</dt><dd>¥{limit.toLocaleString()}</dd><dt>已使用</dt><dd>¥{total.toLocaleString()}（{pct}%）</dd><dt>预警阈值</dt><dd>75% / 90%</dd><dt>通知渠道</dt><dd>企微 + 邮件</dd></div>
          <div className="callout warn mt-3"><Icon name="alert-triangle" /><div>按当前增速，月末预计 ¥{Math.round(total * 1.18).toLocaleString()}，将超出预算。</div></div>
          <Button className="mt-3" onClick={() => { setBudget(data.budget); setBudgetOpen(true) }}>调整预算</Button>
        </Panel>
      </div>

      <Modal open={budgetOpen} onClose={() => setBudgetOpen(false)} title="调整月度预算" sub="当前周期：2026 年 11 月" footer={<><span className="spacer" /><Button onClick={() => setBudgetOpen(false)}>取消</Button><Button kind="primary" onClick={() => saveBudget.mutate()}>保存预算</Button></>}>
        <div className="field"><label>月度预算（元）</label><input className="input" type="number" value={budget} onChange={(e) => setBudget(Number(e.target.value))} /></div>
        <div className="field"><label>预警阈值</label><div className="row"><input className="input" defaultValue="75" /><input className="input" defaultValue="90" /></div><div className="hint">达到阈值后通过企微和应用内通知提醒。</div></div>
      </Modal>
    </>
  )
}
