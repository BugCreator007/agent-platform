import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Release } from '../api'
import { Icon } from '../components/icons'
import {
  Badge,
  Button,
  Drawer,
  Modal,
  PageHead,
  Panel,
  StatusBadge,
  toast,
} from '../components/ui'

const STAGES = [
  { key: 'build', label: '构建' },
  { key: 'eval', label: '评测' },
  { key: 'approve', label: '审批' },
  { key: 'canary', label: '灰度' },
  { key: 'live', label: '全量' },
]

const stageIndex = (stage: string) => {
  if (stage === 'draft') return 0
  const idx = STAGES.findIndex((s) => s.key === stage)
  return idx < 0 ? 4 : idx
}

export function ReleasePage() {
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState('all')
  const [selected, setSelected] = useState<Release | null>(null)
  const [diffOpen, setDiffOpen] = useState(false)
  const [trafficOpen, setTrafficOpen] = useState(false)
  const [policyOpen, setPolicyOpen] = useState(false)
  const [traffic, setTraffic] = useState(20)

  const releases = useQuery({ queryKey: ['releases'], queryFn: () => api.get<Release[]>('/releases') })

  const act = useMutation({
    mutationFn: ({ id, act, body }: { id: string; act: string; body?: unknown }) => api.post<Release>(`/releases/${id}/${act}`, body),
    onSuccess: (r, vars) => {
      toast(vars.act === 'approve' ? '审批已通过' : vars.act === 'promote' ? '已推进发布阶段' : '已执行回滚')
      queryClient.invalidateQueries({ queryKey: ['releases'] })
      if (selected?.id === r.id) setSelected(r)
    },
    onError: (e: Error) => toast(e.message, 'err'),
  })

  const saveTraffic = useMutation({
    mutationFn: () => api.put<Release>(`/releases/${selected!.id}/traffic`, { traffic }),
    onSuccess: (r) => {
      toast(`灰度流量已调整为 ${r.traffic}%`)
      setTrafficOpen(false)
      setSelected(r)
      queryClient.invalidateQueries({ queryKey: ['releases'] })
    },
    onError: (e: Error) => toast(e.message, 'err'),
  })

  const list = (releases.data ?? []).filter((r) => filter === 'all' || r.stage === filter)
  const counts = (key: string) => (releases.data ?? []).filter((r) => r.stage === key).length
  const pending = counts('approve')
  const canary = counts('canary')

  const open = (r: Release) => {
    setSelected(r)
    setTraffic(r.traffic || 20)
  }

  return (
    <>
      <PageHead title="发布与版本" sub="通过评测门禁、审批和灰度，把版本安全推到全量。所有变更都有版本记录与回滚路径。">
        <Button onClick={() => setPolicyOpen(true)}><Icon name="shield" />发布规则</Button>
      </PageHead>

      <div className="grid-4">
        <div className="panel stat"><div className="lb">待审批</div><div className="vl">{pending} <small>个版本</small></div><div className="delta"><Badge tone="info">需要处理</Badge></div></div>
        <div className="panel stat"><div className="lb">灰度中</div><div className="vl">{canary} <small>个版本</small></div><div className="delta"><span>观察窗口 24h</span></div></div>
        <div className="panel stat"><div className="lb">本月发布</div><div className="vl">6 <small>次</small></div><div className="delta"><span className="up">↑ 2 次</span><span>较上月</span></div></div>
        <div className="panel stat"><div className="lb">回滚</div><div className="vl">1 <small>次</small></div><div className="delta"><Badge tone="good">已恢复</Badge></div></div>
      </div>

      <Panel className="mt-3" title="发布流水线" sub="构建 → 评测 → 审批 → 灰度 → 全量" tools={<div className="row wrap" style={{ gap: 6 }}>{['all', ...STAGES.map((s) => s.key)].map((k) => <button key={k} className={`chip ${filter === k ? 'active' : ''}`} onClick={() => setFilter(k)}>{k === 'all' ? '全部' : STAGES.find((s) => s.key === k)?.label}</button>)}</div>} noBody>
        <div className="pipeline" style={{ padding: '14px 18px' }}>
          {STAGES.map((s, i) => (
            <div className="pipe-item" key={s.key}>
              <div className={`step-dot ${i <= 3 ? 'done' : ''}`}>{i < 4 ? <Icon name="check" /> : <Icon name="more" />}</div>
              <div><div className="step-title">{s.label}</div><div className="step-meta">{counts(s.key)} 个版本</div></div>
              {i < STAGES.length - 1 && <div className="pipe-card" />}
            </div>
          ))}
        </div>
      </Panel>

      <Panel className="mt-3" title="发布记录" sub="按阶段流转，点击版本查看详情" noBody>
        <div className="table-wrap">
          <table className="data">
            <thead><tr><th>版本</th><th>阶段</th><th>灰度</th><th className="num">评测分</th><th>提交人</th><th>说明</th><th>时间</th><th style={{ textAlign: 'right' }}>操作</th></tr></thead>
            <tbody>
              {list.map((r) => (
                <tr key={r.id} className="clickable" onClick={() => open(r)}>
                  <td><b>{r.version}</b><div className="sub">{r.agentName}</div></td>
                  <td><StatusBadge status={r.stage} /></td>
                  <td>{r.traffic}%</td>
                  <td className="num">{r.evalScore ?? '—'}</td>
                  <td>{r.owner}</td>
                  <td className="wrap" style={{ maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.note}</td>
                  <td className="muted">{r.time}</td>
                  <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                    {r.stage === 'approve' && <Button size="sm" kind="primary" onClick={() => act.mutate({ id: r.id, act: 'approve' })}>审批</Button>}
                    {r.stage === 'canary' && <Button size="sm" kind="primary" onClick={() => act.mutate({ id: r.id, act: 'promote' })}>全量</Button>}
                    {r.stage === 'draft' && <Button size="sm" onClick={() => act.mutate({ id: r.id, act: 'promote' })}>提交发布</Button>}
                    {r.stage === 'canary' && <Button size="sm" onClick={() => { open(r); setTrafficOpen(true) }}>调整流量</Button>}
                    {(r.stage === 'live' || r.stage === 'canary') && <Button size="sm" kind="danger" onClick={() => act.mutate({ id: r.id, act: 'rollback' })}>回滚</Button>}
                  </td>
                </tr>
              ))}
              {!list.length && <tr><td colSpan={8}><div className="empty"><Icon name="releases" /><b>暂无发布记录</b>调整筛选条件后重试。</div></td></tr>}
            </tbody>
          </table>
        </div>
      </Panel>

      <div className="grid-2 mt-3">
        <Panel title="发布门禁" sub="平台级规则对全部智能体生效">
          <div className="checklist">
            <div className="ci"><Icon name="check-circle" />评测得分不低于基线 0.5 分</div>
            <div className="ci"><Icon name="check-circle" />工具变更必须重新运行工具选择评测</div>
            <div className="ci"><Icon name="check-circle" />灰度观察不少于 24 小时</div>
            <div className="ci"><Icon name="check-circle" />错误率超过 1% 自动回滚</div>
          </div>
          <div className="callout mt-3"><Icon name="shield" /><div>平台管理员可在「平台设置」中调整发布门禁。</div></div>
        </Panel>
        <Panel title="最近回滚" sub="问题版本与恢复情况">
          {(releases.data ?? []).filter((r) => r.stage === 'rollback').map((r) => (
            <div key={r.id} className="alert-item" onClick={() => open(r)}><div className="alert-icon critical"><Icon name="undo" /></div><div style={{ minWidth: 0 }}><div className="al-title">{r.agentName} · {r.version}</div><div className="al-meta">{r.note} · {r.time}</div></div><StatusBadge status={r.stage} /></div>
          ))}
          {!((releases.data ?? []).some((r) => r.stage === 'rollback')) && <div className="muted">暂无回滚记录</div>}
        </Panel>
      </div>

      <Drawer open={!!selected} onClose={() => setSelected(null)} width={760}>
        {selected && (
          <>
            <div className="drawer-head">
              <div className="avatar" style={{ width: 38, height: 38, borderRadius: 10, background: 'var(--pine-wash)', color: 'var(--pine-deep)' }}><Icon name="releases" /></div>
              <div className="dh-main"><div className="drawer-title">{selected.agentName} · {selected.version} <StatusBadge status={selected.stage} /></div><div className="drawer-sub"><span>{selected.owner} 提交</span><span>·</span><span>{selected.time}</span></div></div>
              <button className="icon-btn" onClick={() => setSelected(null)}><Icon name="x" /></button>
            </div>
            <div className="drawer-body">
              <div className="stepper" style={{ display: 'flex', gap: 6, padding: '4px 0 12px', flexWrap: 'wrap' }}>
                {STAGES.map((s, i) => <div key={s.key} className={`s ${i < stageIndex(selected.stage) ? 'done' : i === stageIndex(selected.stage) ? 'active' : ''}`}><span className="n">{i < stageIndex(selected.stage) ? '✓' : i + 1}</span>{s.label}</div>)}
              </div>
              <div className="grid-3">
                <div className="panel stat" style={{ padding: 13 }}><div className="lb">灰度流量</div><div className="vl" style={{ fontSize: 24 }}>{selected.traffic}%</div></div>
                <div className="panel stat" style={{ padding: 13 }}><div className="lb">评测得分</div><div className="vl" style={{ fontSize: 24 }}>{selected.evalScore ?? '—'}</div></div>
                <div className="panel stat" style={{ padding: 13 }}><div className="lb">发布阶段</div><div className="vl" style={{ fontSize: 20 }}>{STAGES.find((s) => s.key === selected.stage)?.label ?? '草稿'}</div></div>
              </div>
              <div className="section-head"><h2>发布说明</h2></div>
              <p style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.75 }}>{selected.note}</p>
              <div className="section-head"><h2>发布门禁</h2></div>
              <div className="checklist">
                <div className="ci"><Icon name="check-circle" />评测通过 · 得分 {selected.evalScore ?? '—'}</div>
                <div className="ci"><Icon name="check-circle" />工具变更检查通过</div>
                <div className="ci"><Icon name="check-circle" />安全护栏未触发</div>
              </div>
              <div className="section-head"><h2>版本差异</h2></div>
              <Button onClick={() => setDiffOpen(true)}><Icon name="git" />查看 v{Math.max(1, parseInt(selected.version.replace(/\D/g, ''), 10) - 1)} → {selected.version}</Button>
            </div>
            <div className="drawer-foot">
              <Button onClick={() => setDiffOpen(true)}><Icon name="git" />版本差异</Button>
              <span className="spacer" />
              {selected.stage === 'approve' && <Button kind="primary" onClick={() => act.mutate({ id: selected.id, act: 'approve' })}>审批通过</Button>}
              {selected.stage === 'canary' && <><Button onClick={() => setTrafficOpen(true)}>调整流量</Button><Button kind="primary" onClick={() => act.mutate({ id: selected.id, act: 'promote' })}>推进全量</Button></>}
              {(selected.stage === 'live' || selected.stage === 'canary') && <Button kind="danger" onClick={() => act.mutate({ id: selected.id, act: 'rollback' })}>回滚</Button>}
            </div>
          </>
        )}
      </Drawer>

      <Modal open={diffOpen} onClose={() => setDiffOpen(false)} title="版本差异" sub={selected ? `${selected.agentName} · v${Math.max(1, parseInt(selected.version.replace(/\D/g, ''), 10) - 1)} → ${selected.version}` : ''} wide footer={<><span className="spacer" /><Button kind="primary" onClick={() => setDiffOpen(false)}>关闭</Button></>}>
        <div className="diff">
          <div className="diff-row"><span className="diff-key">提示词</span><span className="diff-before">退款金额超过 300 元需人工审批。</span><span className="diff-after">退款金额超过 500 元需人工审批，低风控分订单全部转人工。</span></div>
          <div className="diff-row"><span className="diff-key">工具</span><span className="diff-before">order.query · refund.apply</span><span className="diff-after">order.query · refund.apply · risk.score</span></div>
          <div className="diff-row"><span className="diff-key">知识库</span><span className="diff-before">kb-refund</span><span className="diff-after">kb-refund · kb-refund-rules</span></div>
          <div className="diff-row"><span className="diff-key">模型</span><span className="diff-before">Claude Sonnet 5.5</span><span className="diff-after">Claude Opus 5.5</span></div>
        </div>
      </Modal>

      <Modal open={trafficOpen} onClose={() => setTrafficOpen(false)} title={`调整灰度流量 · ${selected?.version ?? ''}`} sub="按比例把线上流量切到新版本" footer={<><span className="spacer" /><Button onClick={() => setTrafficOpen(false)}>取消</Button><Button kind="primary" onClick={() => saveTraffic.mutate()}>保存流量</Button></>}>
        <div className="field"><label>新版本流量比例</label><input className="input" type="range" min={0} max={100} value={traffic} onChange={(e) => setTraffic(Number(e.target.value))} /><div className="row" style={{ marginTop: 8 }}><span className="muted">0%</span><span className="spacer" /><b>{traffic}%</b><span className="spacer" /><span className="muted">100%</span></div></div>
        <div className="callout info"><Icon name="shield" /><div>灰度期间持续观察错误率与人工接管率；超过门禁自动回滚。</div></div>
      </Modal>

      <Modal open={policyOpen} onClose={() => setPolicyOpen(false)} title="发布规则" sub="平台级发布门禁" footer={<><span className="spacer" /><Button kind="primary" onClick={() => setPolicyOpen(false)}>知道了</Button></>}>
        <div className="checklist">
          <div className="ci"><Icon name="check-circle" />评测得分不低于基线 0.5 分</div>
          <div className="ci"><Icon name="check-circle" />工具变更必须重新运行工具选择评测</div>
          <div className="ci"><Icon name="check-circle" />灰度观察不少于 24 小时</div>
          <div className="ci"><Icon name="check-circle" />错误率超过 1% 自动回滚</div>
        </div>
      </Modal>
    </>
  )
}
