import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Alert, type AlertRule } from '../api'
import { Icon } from '../components/icons'
import {
  Badge,
  Button,
  Drawer,
  Modal,
  PageHead,
  Panel,
  StatusBadge,
  Tabs,
  toast,
} from '../components/ui'

const RULES = [
  { name: '智能体错误率', metric: 'error_rate', level: 'critical', threshold: '> 2% · 5 分钟', channel: '企微 + 短信', on: true },
  { name: '工具错误率', metric: 'tool_error_rate', level: 'warning', threshold: '> 5% · 10 分钟', channel: '企微', on: true },
  { name: 'P95 首响', metric: 'p95_latency', level: 'warning', threshold: '> 3s · 10 分钟', channel: '企微', on: true },
  { name: '知识库召回率', metric: 'recall_rate', level: 'warning', threshold: '< 80% · 30 分钟', channel: '企微', on: true },
  { name: '模型额度', metric: 'quota_used', level: 'critical', threshold: '> 90%', channel: '企微 + 短信', on: true },
]

export function AlertsPage() {
  const queryClient = useQueryClient()
  const [tab, setTab] = useState('active')
  const [selected, setSelected] = useState<Alert | null>(null)
  const [rulesOpen, setRulesOpen] = useState(false)

  const alerts = useQuery({ queryKey: ['alerts'], queryFn: () => api.get<Alert[]>('/alerts') })
  const ruleQuery = useQuery({ queryKey: ['alert-rules'], queryFn: () => api.get<AlertRule[]>('/alert-rules') })
  const toggleRule = useMutation({
    mutationFn: (rule: AlertRule) => api.put<AlertRule>(`/alert-rules/${rule.id}`, { ...rule, enabled: !rule.enabled }),
    onSuccess: () => { toast('告警规则已更新'); queryClient.invalidateQueries({ queryKey: ['alert-rules'] }) },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const rules = ruleQuery.data ?? RULES.map((r, i) => ({ id: `rule-${i}`, name: r.name, metric: r.metric, level: r.level, thresholdValue: 0, windowLabel: r.threshold, channel: r.channel, enabled: r.on }))
  const act = useMutation({
    mutationFn: ({ id, action }: { id: string; action: string }) => api.post<Alert>(`/alerts/${id}/${action}`),
    onSuccess: (a) => {
      toast(a.status === 'resolved' ? '告警已解决' : '告警已认领')
      queryClient.invalidateQueries({ queryKey: ['alerts'] })
      if (selected?.id === a.id) setSelected(a)
    },
    onError: (e: Error) => toast(e.message, 'err'),
  })

  const list = alerts.data ?? []
  const active = list.filter((a) => a.status === 'active')
  const ack = list.filter((a) => a.status === 'ack')
  const resolved = list.filter((a) => a.status === 'resolved')
  const filtered = tab === 'all' ? list : list.filter((a) => a.status === tab)

  return (
    <>
      <PageHead title="监控告警" sub="跟踪智能体、工具与成本的运行异常。告警规则变更立即生效，历史告警不受影响。">
        <Button onClick={() => setRulesOpen(true)}><Icon name="settings" />告警规则</Button>
      </PageHead>

      <div className="grid-4">
        <div className="panel stat"><div className="lb">待处理</div><div className="vl">{active.length}</div><div className="delta"><Badge tone="danger">需要处理</Badge></div></div>
        <div className="panel stat"><div className="lb">已认领</div><div className="vl">{ack.length}</div><div className="delta"><span>处理中</span></div></div>
        <div className="panel stat"><div className="lb">今日已恢复</div><div className="vl">{resolved.length}</div><div className="delta"><Badge tone="good">已解决</Badge></div></div>
        <div className="panel stat"><div className="lb">平均恢复时间</div><div className="vl">18<small> 分钟</small></div><div className="delta"><span className="down">↓ 4 分钟</span></div></div>
      </div>

      <div className="grid-2 mt-3">
        <Panel
          title="告警列表"
          sub="严重告警置顶显示"
          tools={<Tabs active={tab} onChange={setTab} items={[{ key: 'active', label: `待处理 ${active.length}` }, { key: 'ack', label: `已认领 ${ack.length}` }, { key: 'resolved', label: '已恢复' }, { key: 'all', label: '全部' }]} />}
          bodyStyle={{ paddingTop: 4 }}
        >
          {filtered.map((a) => (
            <div key={a.id} className="alert-item" onClick={() => setSelected(a)}>
              <div className={`alert-icon ${a.severity}`}><Icon name={a.severity === 'critical' ? 'alert-triangle' : 'clock'} /></div>
              <div style={{ minWidth: 0, flex: 1 }}><div className="al-title">{a.title}</div><div className="al-meta">{a.target}<span>·</span>{a.ago}<span>·</span>{a.metric}</div></div>
              <div className="al-actions"><StatusBadge status={a.status} /></div>
            </div>
          ))}
          {!filtered.length && <div className="empty"><Icon name="alerts" /><b>没有告警</b>当前筛选条件下没有记录。</div>}
        </Panel>

        <div className="stack">
          <Panel title="告警规则" sub="规则变更立即生效" tools={<Button size="sm" onClick={() => setRulesOpen(true)}>编辑规则</Button>} noBody>
            <div className="table-wrap">
              <table className="data">
                <thead><tr><th>规则</th><th>级别</th><th>阈值</th><th>通知</th></tr></thead>
                <tbody>{rules.slice(0, 4).map((r) => <tr key={r.id}><td>{r.name}</td><td>{r.level === 'critical' ? <Badge tone="danger">严重</Badge> : <Badge tone="warn">警告</Badge>}</td><td className="mono" style={{ fontSize: 11 }}>{r.metric} &gt; {r.thresholdValue} · {r.windowLabel}</td><td>{r.channel}</td></tr>)}</tbody>
              </table>
            </div>
          </Panel>
          <Panel title="值班与通知" sub="主值班 · 09:00 – 21:00">
            <div className="member-cell"><div className="avatar" style={{ background: 'var(--pine-wash)', color: 'var(--pine-deep)' }}>顾</div><div><div className="nm">顾先路</div><div className="em">主值班 · 09:00 – 21:00</div></div><span className="spacer" /><Badge tone="good">在线</Badge></div>
            <div className="member-cell"><div className="avatar" style={{ background: '#EAF0FB', color: '#1F4E9C' }}>李</div><div><div className="nm">李婧</div><div className="em">备岗 · 21:00 – 次日 09:00</div></div><span className="spacer" /><Badge tone="neutral">离线</Badge></div>
            <div className="callout info mt-3"><Icon name="bell" /><div>严重告警同时发送企微与短信；警告仅发送企微。</div></div>
          </Panel>
        </div>
      </div>

      <Drawer open={!!selected} onClose={() => setSelected(null)} width={640}>
        {selected && (
          <>
            <div className="drawer-head"><div className={`alert-icon ${selected.severity}`} style={{ width: 38, height: 38 }}><Icon name={selected.severity === 'critical' ? 'alert-triangle' : 'clock'} /></div><div className="dh-main"><div className="drawer-title">{selected.title} <StatusBadge status={selected.status} /></div><div className="drawer-sub"><span>{selected.target}</span><span>·</span><span>{selected.time}</span></div></div><button className="icon-btn" onClick={() => setSelected(null)}><Icon name="x" /></button></div>
            <div className="drawer-body">
              <div className="grid-2"><div className="panel stat" style={{ padding: 13 }}><div className="lb">当前指标</div><div className="vl" style={{ fontSize: 24 }}>{selected.metric}</div></div><div className="panel stat" style={{ padding: 13 }}><div className="lb">级别</div><div className="vl" style={{ fontSize: 24 }}>{selected.severity === 'critical' ? '严重' : '警告'}</div></div></div>
              <div className="section-head"><h2>告警详情</h2></div>
              <p style={{ fontSize: 13, lineHeight: 1.75, color: 'var(--ink-2)' }}>{selected.detail}</p>
              <div className="section-head"><h2>触发规则</h2></div>
              <div className="kv"><dt>规则</dt><dd>{selected.rule}</dd><dt>目标</dt><dd>{selected.target}</dd><dt>首次触发</dt><dd>{selected.time}</dd><dt>持续</dt><dd>{selected.ago}</dd></div>
              <div className="section-head"><h2>影响范围</h2></div>
              <div className="callout warn"><Icon name="alert-triangle" /><div>最近 1 小时共 38 个会话受影响，其中 12 个转人工。</div></div>
            </div>
            <div className="drawer-foot"><span className="spacer" />{selected.status === 'active' && <Button onClick={() => act.mutate({ id: selected.id, action: 'ack' })}>认领告警</Button>}{selected.status !== 'resolved' && <Button kind="primary" onClick={() => act.mutate({ id: selected.id, action: 'resolve' })}>标记已解决</Button>}{selected.status === 'resolved' && <Button kind="primary" onClick={() => setSelected(null)}>关闭</Button>}</div>
          </>
        )}
      </Drawer>

      <Modal open={rulesOpen} onClose={() => setRulesOpen(false)} title="告警规则" sub="规则变更立即生效，历史告警不受影响" wide footer={<><span className="spacer" /><Button kind="primary" onClick={() => { setRulesOpen(false); toast('告警规则已保存') }}>保存规则</Button></>}>
        <div className="table-wrap"><table className="data"><thead><tr><th>规则</th><th>级别</th><th>阈值</th><th>通知渠道</th><th>启用</th></tr></thead><tbody>{rules.map((r) => <tr key={r.id}><td>{r.name}</td><td>{r.level === 'critical' ? <Badge tone="danger">严重</Badge> : <Badge tone="warn">警告</Badge>}</td><td>{r.metric} &gt; {r.thresholdValue} · {r.windowLabel}</td><td>{r.channel}</td><td><button className={`toggle ${r.enabled ? 'on' : ''}`} onClick={() => ruleQuery.data && toggleRule.mutate(r)} aria-label={`切换 ${r.name}`} /></td></tr>)}</tbody></table></div>
      </Modal>
    </>
  )
}
