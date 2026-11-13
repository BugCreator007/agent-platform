import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type EvalRun, type EvalSuite } from '../api'
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

export function EvalPage() {
  const queryClient = useQueryClient()
  const [selectedSuite, setSelectedSuite] = useState<EvalSuite | null>(null)
  const [selectedRun, setSelectedRun] = useState<EvalRun | null>(null)
  const [reportOpen, setReportOpen] = useState(false)
  const [compareOpen, setCompareOpen] = useState(false)
  const [runOpen, setRunOpen] = useState(false)

  const suites = useQuery({ queryKey: ['eval-suites'], queryFn: () => api.get<EvalSuite[]>('/eval-suites') })
  const runs = useQuery({ queryKey: ['eval-runs'], queryFn: () => api.get<EvalRun[]>('/eval-runs') })

  const run = useMutation({
    mutationFn: (suite: EvalSuite) => api.post<EvalRun>('/eval-runs', { suiteId: suite.id, agentName: '售后工单助手', version: 'v25' }),
    onSuccess: (r) => {
      toast(`评测完成：${r.score} 分`)
      setRunOpen(false)
      setSelectedRun(r)
      setReportOpen(true)
      queryClient.invalidateQueries({ queryKey: ['eval-runs'] })
    },
    onError: (e: Error) => toast(e.message, 'err'),
  })

  const suiteList = suites.data ?? []
  const runList = runs.data ?? []

  return (
    <>
      <PageHead title="评测中心" sub="用固定数据集回归智能体表现，发布前自动运行门禁评测。支持逐条失败分析与版本对比。">
        <Button onClick={() => setCompareOpen(true)}><Icon name="git" />版本对比</Button>
        <Button kind="primary" onClick={() => setRunOpen(true)}><Icon name="play" />新建评测运行</Button>
      </PageHead>

      <div className="grid-4">
        <div className="panel stat"><div className="lb">评测集</div><div className="vl">{suiteList.length} <small>个</small></div><div className="delta"><span>覆盖客服、交易、风控</span></div></div>
        <div className="panel stat"><div className="lb">累计用例</div><div className="vl">{suiteList.reduce((s, x) => s + x.cases, 0).toLocaleString()}</div><div className="delta"><span>自动回归</span></div></div>
        <div className="panel stat"><div className="lb">平均通过率</div><div className="vl">{suiteList.length ? Math.round(suiteList.reduce((s, x) => s + x.rate, 0) / suiteList.length) : 0}<small>%</small></div><div className="delta"><Badge tone="good">稳定</Badge></div></div>
        <div className="panel stat"><div className="lb">最近运行</div><div className="vl">{runList[0]?.score ?? '—'} <small>分</small></div><div className="delta"><span>{runList[0]?.status === 'success' ? '高于基线' : '有回归'}</span></div></div>
      </div>

      <div className="section-head"><h2>评测集</h2><span className="section-note">点击卡片查看用例、评分器与历史运行</span></div>
      <div className="grid-3">
        {suiteList.map((s) => (
          <article key={s.id} className="panel suite-card" onClick={() => setSelectedSuite(s)}>
            <div className="row"><div className="avatar" style={{ background: 'var(--pine-wash)', color: 'var(--pine-deep)' }}><Icon name="evals" /></div><div><b>{s.name}</b><div className="sc-meta"><span>{s.cases} 条用例</span><span>·</span><span>{s.last}</span></div></div><span className="spacer" /><Badge tone="good">稳定</Badge></div>
            <p className="muted" style={{ fontSize: 12, lineHeight: 1.6 }}>{s.description}</p>
            <div className="row" style={{ alignItems: 'baseline' }}><span style={{ fontSize: 28, fontWeight: 600 }}>{s.rate}%</span><span className="muted">通过</span><span className="spacer" /><Button size="sm" onClick={(e) => { e.stopPropagation(); run.mutate(s) }}>运行</Button></div>
          </article>
        ))}
      </div>

      <div className="grid-2 mt-3">
        <Panel title="最近评测" sub="按时间倒序" noBody>
          <div className="table-wrap">
            <table className="data">
              <thead><tr><th>评测集</th><th>智能体</th><th>版本</th><th>通过</th><th className="num">得分</th><th>状态</th><th style={{ textAlign: 'right' }}>操作</th></tr></thead>
              <tbody>
                {runList.map((r) => (
                  <tr key={r.id} className="clickable" onClick={() => { setSelectedRun(r); setReportOpen(true) }}>
                    <td>{r.suite}</td><td>{r.agent}</td><td>{r.version}</td><td>{r.passed}</td><td className="num">{r.score}</td><td>{r.status === 'success' ? <StatusBadge status="success" /> : <Badge tone="warn">有回归</Badge>}</td>
                    <td style={{ textAlign: 'right' }}><Button size="sm" onClick={(e) => { e.stopPropagation(); setSelectedRun(r); setReportOpen(true) }}>报告</Button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>

        <Panel title="版本对比" sub="售后工单助手 · v24 → v25" tools={<Badge tone="good">+2.1 分</Badge>}>
          {[
            ['意图识别', 94.2, 96.8],
            ['工具选择', 91.5, 96.2],
            ['政策引用', 88.7, 92.4],
            ['多轮指代', 90.1, 89.8],
          ].map(([name, before, after]) => (
            <div className="bar-row" key={String(name)}>
              <div className="bar-top"><span className="bar-name">{name}</span><span className="bar-val">{after}%</span><span className="bar-pct">{Number(after) - Number(before) >= 0 ? '+' : ''}{(Number(after) - Number(before)).toFixed(1)}</span></div>
              <div className="bar-track"><div className="bar-fill" style={{ width: `${after}%` }} /></div>
            </div>
          ))}
          <Button size="sm" className="mt-3" onClick={() => setCompareOpen(true)}>查看完整差异<Icon name="arrow-right" /></Button>
        </Panel>
      </div>

      <Drawer open={!!selectedSuite} onClose={() => setSelectedSuite(null)} width={680}>
        {selectedSuite && (
          <>
            <div className="drawer-head"><div className="avatar" style={{ width: 36, height: 36, borderRadius: 10, background: 'var(--pine-wash)', color: 'var(--pine-deep)' }}><Icon name="evals" /></div><div className="dh-main"><div className="drawer-title">{selectedSuite.name}</div><div className="drawer-sub"><span>{selectedSuite.cases} 条用例</span><span>·</span><span>最近运行 {selectedSuite.last}</span></div></div><button className="icon-btn" onClick={() => setSelectedSuite(null)}><Icon name="x" /></button></div>
            <div className="drawer-body">
              <div className="grid-3"><div className="panel stat" style={{ padding: 12 }}><div className="lb">通过率</div><div className="vl" style={{ fontSize: 24 }}>{selectedSuite.rate}%</div></div><div className="panel stat" style={{ padding: 12 }}><div className="lb">用例</div><div className="vl" style={{ fontSize: 24 }}>{selectedSuite.cases}</div></div><div className="panel stat" style={{ padding: 12 }}><div className="lb">基线</div><div className="vl" style={{ fontSize: 24 }}>95</div></div></div>
              <div className="section-head"><h2>评测维度</h2></div>
              {['意图识别', '工具选择', '政策引用', '多轮指代'].map((x, i) => <div className="bar-row" key={x}><div className="bar-top"><span className="bar-name">{x}</span><span className="bar-val">{[96.8, 96.2, 92.4, 89.8][i]}%</span></div><div className="bar-track"><div className="bar-fill" style={{ width: `${[96.8, 96.2, 92.4, 89.8][i]}%` }} /></div></div>)}
              <div className="section-head"><h2>评分器</h2></div>
              <div className="node-list"><div className="node-card"><span className="node-index">1</span><div><b>规则匹配</b><div className="muted">关键词与结构化字段断言</div></div></div><div className="node-card"><span className="node-index">2</span><div><b>LLM Judge</b><div className="muted">Claude Sonnet 5.5 · 评分一致性 0.91</div></div></div></div>
            </div>
            <div className="drawer-foot"><span className="spacer" /><Button onClick={() => setSelectedSuite(null)}>关闭</Button><Button kind="primary" onClick={() => run.mutate(selectedSuite)}><Icon name="play" />运行评测</Button></div>
          </>
        )}
      </Drawer>

      <Modal open={runOpen} onClose={() => setRunOpen(false)} title="新建评测运行" sub="选择评测集与目标版本" footer={<><span className="spacer" /><Button onClick={() => setRunOpen(false)}>取消</Button><Button kind="primary" onClick={() => suites.data?.[0] && run.mutate(suites.data[0])}>开始运行</Button></>}>
        <div className="field"><label>评测集</label><select className="select">{suiteList.map((s) => <option key={s.id}>{s.name}</option>)}</select></div>
        <div className="field"><label>智能体</label><select className="select"><option>售后工单助手</option><option>退款审核 Copilot</option></select></div>
        <div className="field"><label>版本</label><select className="select"><option>v25</option><option>v24</option></select></div>
        <div className="callout info"><Icon name="evals" /><div>评测会消耗模型额度，预计运行 2–3 分钟。</div></div>
      </Modal>

      <Modal open={reportOpen} onClose={() => setReportOpen(false)} title="评测报告" sub={selectedRun ? `${selectedRun.suite} · ${selectedRun.agent} · ${selectedRun.version}` : ''} footer={<><span className="spacer" /><Button onClick={() => setReportOpen(false)}>关闭</Button></>}>
        {selectedRun && (
          <>
            <div className="grid-3"><div className="panel stat" style={{ padding: 13 }}><div className="lb">得分</div><div className="vl" style={{ fontSize: 24 }}>{selectedRun.score}</div></div><div className="panel stat" style={{ padding: 13 }}><div className="lb">通过</div><div className="vl" style={{ fontSize: 24 }}>{selectedRun.passed}</div></div><div className="panel stat" style={{ padding: 13 }}><div className="lb">耗时</div><div className="vl" style={{ fontSize: 24 }}>{selectedRun.duration}</div></div></div>
            <div className="section-head"><h2>失败用例</h2></div>
            <div className="panel" style={{ boxShadow: 'none' }}><div className="panel-body" style={{ paddingTop: 6 }}><div className="case-row"><span className="badge danger" style={{ height: 18 }}>失败</span><div><div className="q">优惠券分摊后实际可退金额是多少？</div><div className="a">期望 ¥338.00，实际 ¥368.00。</div></div></div><div className="case-row"><span className="badge danger" style={{ height: 18 }}>失败</span><div><div className="q">跨境订单可以七天无理由退款吗？</div><div className="a">政策引用错误。</div></div></div></div></div>
          </>
        )}
      </Modal>

      <Modal open={compareOpen} onClose={() => setCompareOpen(false)} title="版本对比" sub="售后工单助手 · v24 → v25" wide footer={<><span className="spacer" /><Button kind="primary" onClick={() => setCompareOpen(false)}>关闭</Button></>}>
        <div className="table-wrap"><table className="data"><thead><tr><th>维度</th><th className="num">v24</th><th className="num">v25</th><th className="num">变化</th></tr></thead><tbody>{[['意图识别', 94.2, 96.8], ['工具选择', 91.5, 96.2], ['政策引用', 88.7, 92.4], ['多轮指代', 90.1, 89.8]].map(([n, a, b]) => <tr key={String(n)}><td>{n}</td><td className="num">{a}%</td><td className="num">{b}%</td><td className="num" style={{ color: Number(b) >= Number(a) ? 'var(--good-text)' : 'var(--danger-text)' }}>{Number(b) >= Number(a) ? '+' : ''}{(Number(b) - Number(a)).toFixed(1)}</td></tr>)}</tbody></table></div>
      </Modal>
    </>
  )
}
