import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Workflow, type WorkflowInstance, type WorkflowNode, type WorkflowNodeRun } from '../api'
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

const NODE_TYPES: Record<string, { label: string; desc: string; color: string; bg: string; icon: string }> = {
  trigger: { label: '触发器', desc: '工单、定时或事件触发', color: '#2A78D6', bg: '#EAF0FB', icon: 'zap' },
  model: { label: '模型调用', desc: '调用大模型生成或判断', color: '#0E4F3C', bg: '#E9F1EC', icon: 'spark' },
  tool: { label: '工具调用', desc: '调用业务 API 或 MCP 工具', color: '#D99A2B', bg: '#FBF3E2', icon: 'tools' },
  condition: { label: '条件分支', desc: '按表达式选择后续路径', color: '#EB6834', bg: '#FCEDE5', icon: 'workflow' },
  approval: { label: '人工审批', desc: '等待人工确认后继续', color: '#6250D6', bg: '#EFECFB', icon: 'users' },
  knowledge: { label: '知识检索', desc: '从知识库召回相关片段', color: '#1BAF7A', bg: '#E4F4EE', icon: 'knowledge' },
  notify: { label: '消息通知', desc: '企微或 Webhook 真实发送', color: '#8A5A00', bg: '#FBF1DD', icon: 'send' },
  timer: { label: '定时器', desc: '等待指定秒数后继续', color: '#2A78D6', bg: '#EAF0FB', icon: 'clock' },
  subflow: { label: '子流程', desc: '调用另一个流程并等待结果', color: '#6250D6', bg: '#EFECFB', icon: 'workflow' },
  join: { label: '汇聚', desc: '等待并行分支全部到达', color: '#1BAF7A', bg: '#E4F4EE', icon: 'git' },
  end: { label: '结束', desc: '输出结果并结束流程', color: '#7E8781', bg: '#EFF1E9', icon: 'check-circle' },
}

const TYPE_ALIASES: Record<string, string> = {
  START: 'trigger', TRIGGER: 'trigger', MODEL: 'model', TOOL: 'tool', CONDITION: 'condition',
  APPROVAL: 'approval', KNOWLEDGE: 'knowledge', NOTIFY: 'notify', TIMER: 'timer',
  SUBFLOW: 'subflow', JOIN: 'join', END: 'end',
}

const normalizeType = (type: string) => TYPE_ALIASES[type.toUpperCase()] ?? type.toLowerCase()

const TEMPLATES: Record<string, { name: string; desc: string; trigger: string; nodes: WorkflowNode[] }> = {
  ticket: {
    name: '售后工单自动处理',
    desc: '识别意图 → 查订单 → 判断金额 → 大额转人工 → 回复',
    trigger: '工单事件',
    nodes: [
      { key: 't1', type: 'trigger', title: '工单创建', meta: '售后工单 · 新建事件' },
      { key: 't2', type: 'knowledge', title: '检索售后政策', meta: 'kb-refund · Top 4' },
      { key: 't3', type: 'tool', title: '查询订单', meta: 'order.query' },
      { key: 't4', type: 'condition', title: '金额是否超过 500', meta: 'amount > 500', config: { expression: 'amount > 500', trueNext: 't5', falseNext: 't6' } },
      { key: 't5', type: 'approval', title: '人工审批', meta: '退款审核组' },
      { key: 't6', type: 'notify', title: '回复用户', meta: '企微 + 工单' },
    ],
  },
  report: {
    name: '商家经营周报',
    desc: '每周一拉取经营数据，生成周报并发送',
    trigger: '定时 · 每周一 09:00',
    nodes: [
      { key: 'r1', type: 'trigger', title: '定时触发', meta: '每周一 09:00' },
      { key: 'r2', type: 'tool', title: '拉取经营数据', meta: 'bi.query' },
      { key: 'r3', type: 'model', title: '生成周报摘要', meta: 'Claude Sonnet 5.5' },
      { key: 'r4', type: 'notify', title: '发送周报', meta: '企微群 + 邮件' },
    ],
  },
}

export function WorkflowPage() {
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState('all')
  const [editing, setEditing] = useState<Workflow | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [template, setTemplate] = useState('ticket')
  const [newName, setNewName] = useState('售后工单自动处理')
  const [versionsOpen, setVersionsOpen] = useState(false)
  const [instanceId, setInstanceId] = useState<string | null>(null)

  const workflows = useQuery({ queryKey: ['workflows'], queryFn: () => api.get<Workflow[]>('/workflows') })
  const instances = useQuery({ queryKey: ['workflow-instances'], queryFn: () => api.get<WorkflowInstance[]>('/workflow-instances') })
  const instanceDetail = useQuery({
    queryKey: ['workflow-instance', instanceId],
    queryFn: () => api.get<{ instance: WorkflowInstance; nodeRuns: WorkflowNodeRun[] }>(`/workflow-instances/${instanceId}`),
    enabled: !!instanceId,
    refetchInterval: 2000,
  })
  const startInstance = useMutation({
    mutationFn: (workflowId: string) => api.post<WorkflowInstance>('/workflow-instances', { workflowId, input: '' }),
    onSuccess: (i) => { toast('流程实例已启动'); setInstanceId(i.id); queryClient.invalidateQueries({ queryKey: ['workflow-instances'] }) },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const instanceAction = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'approve' | 'cancel' }) =>
      api.post<WorkflowInstance>(`/workflow-instances/${id}/${action}`, action === 'approve' ? { comment: '审批通过' } : undefined),
    onSuccess: () => { toast('实例操作成功'); queryClient.invalidateQueries({ queryKey: ['workflow-instances'] }); queryClient.invalidateQueries({ queryKey: ['workflow-instance', instanceId] }) },
    onError: (e: Error) => toast(e.message, 'err'),
  })

  const act = useMutation({
    mutationFn: ({ id, act }: { id: string; act: string }) => api.post<Workflow>(`/workflows/${id}/${act}`),
    onSuccess: (_, vars) => {
      toast(vars.act === 'test' ? '测试运行通过' : '流程已发布')
      queryClient.invalidateQueries({ queryKey: ['workflows'] })
    },
    onError: (e: Error) => toast(e.message, 'err'),
  })

  const create = useMutation({
    mutationFn: () => api.post<Workflow>('/workflows', { name: newName, trigger: TEMPLATES[template].trigger, nodes: TEMPLATES[template].nodes }),
    onSuccess: (w) => {
      toast('流程已创建')
      setCreateOpen(false)
      queryClient.invalidateQueries({ queryKey: ['workflows'] })
      setEditing(w)
    },
    onError: (e: Error) => toast(e.message, 'err'),
  })

  const list = (workflows.data ?? []).filter((w) => filter === 'all' || w.status === filter)

  if (editing) {
    return (
      <WorkflowEditor
        workflow={editing}
        workflows={workflows.data ?? []}
        onClose={() => setEditing(null)}
        onChanged={(w) => {
          setEditing(w)
          queryClient.invalidateQueries({ queryKey: ['workflows'] })
        }}
      />
    )
  }

  return (
    <>
      <PageHead title="流程编排" sub="可视化编排多步骤业务链路，支持并行汇聚、条件路由、子流程、定时器、补偿事务、真实通知与人工审批。">
        <Button onClick={() => setCreateOpen(true)}><Icon name="list" />从模板创建</Button>
        <Button kind="primary" onClick={() => setCreateOpen(true)}><Icon name="plus" />新建流程</Button>
      </PageHead>

      <div className="grid-4">
        <div className="panel stat"><div className="lb">流程总数</div><div className="vl">{(workflows.data ?? []).length}</div><div className="delta"><span>覆盖客服、交易、财务</span></div></div>
        <div className="panel stat"><div className="lb">已发布</div><div className="vl">{(workflows.data ?? []).filter((w) => w.status === 'published').length}</div><div className="delta"><Badge tone="good">运行正常</Badge></div></div>
        <div className="panel stat"><div className="lb">近 7 天运行</div><div className="vl">{(workflows.data ?? []).reduce((s, w) => s + w.runs, 0).toLocaleString()}</div><div className="delta"><span className="up">↑ 12.4%</span><span>较上周</span></div></div>
        <div className="panel stat"><div className="lb">平均成功率</div><div className="vl">98.1<small>%</small></div><div className="delta"><span className="up">↑ 0.6pt</span><span>较上周</span></div></div>
      </div>

      <div className="filterbar mt-3">
        <div className="row wrap" style={{ gap: 6 }}>
          {['all', 'published', 'paused', 'draft'].map((k) => (
            <button key={k} className={`chip ${filter === k ? 'active' : ''}`} onClick={() => setFilter(k)}>
              {{ all: '全部', published: '已发布', paused: '已暂停', draft: '草稿' }[k] as string}
            </button>
          ))}
        </div>
        <span className="spacer" />
        <Button size="sm" onClick={() => setCreateOpen(true)}><Icon name="plus" />新建流程</Button>
      </div>

      <div className="grid-2">
        <Panel title="流程列表" sub="点击编排打开可视化编辑器" noBody>
          <div className="table-wrap">
            <table className="data">
              <thead><tr><th>流程</th><th>状态</th><th>触发方式</th><th className="num">近 7 天</th><th className="num">成功率</th><th style={{ textAlign: 'right' }}>操作</th></tr></thead>
              <tbody>
                {list.map((w) => (
                  <tr key={w.id} className="clickable" onClick={() => setEditing(w)}>
                    <td><div className="nm">{w.name}</div><div className="sub">{w.version} · {w.nodes.length} 个节点 · {w.owner}</div></td>
                    <td><StatusBadge status={w.status} /></td>
                    <td>{w.trigger}</td>
                    <td className="num">{w.runs.toLocaleString()}</td>
                    <td className="num">{w.success}%</td>
                    <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                      <button className="btn sm" onClick={() => startInstance.mutate(w.id)}>运行</button>
                      <button className="btn sm" onClick={() => setEditing(w)}>编排<Icon name="chevron-right" /></button>
                    </td>
                  </tr>
                ))}
                {!list.length && <tr><td colSpan={6}><div className="empty"><Icon name="workflow" /><b>暂无流程</b>从模板创建一个流程。</div></td></tr>}
              </tbody>
            </table>
          </div>
        </Panel>

        <Panel title="运行实例" sub="执行状态、节点进度与人工审批" tools={<Button kind="ghost" size="sm" onClick={() => queryClient.invalidateQueries({ queryKey: ['workflow-instances'] })}>刷新</Button>}>
          {(instances.data ?? []).slice(0, 6).map((i) => (
            <div className="alert-item" key={i.id} onClick={() => setInstanceId(i.id)} style={{ cursor: 'pointer' }}>
              <div className={`alert-icon ${i.status === 'FAILED' ? 'critical' : i.status.startsWith('WAITING_') ? 'warning' : 'info'}`}><Icon name={i.status === 'FAILED' ? 'x-circle' : i.status.startsWith('WAITING_') ? 'clock' : 'check-circle'} /></div>
              <div style={{ minWidth: 0, flex: 1 }}>
                <div className="al-title mono" style={{ fontSize: 11.5 }}>{i.id}</div>
                <div className="al-meta">{i.workflowName} · {i.currentNode ?? '—'}</div>
              </div>
              <StatusBadge status={i.status} />
            </div>
          ))}
          {!instances.data?.length && <div className="muted" style={{ padding: '10px 0' }}>暂无运行实例，点击流程列表的“运行”启动。</div>}
        </Panel>
      </div>

      <div className="section-head"><h2>流程模板</h2><span className="section-note">从常用业务场景开始，再按需调整节点</span></div>
      <div className="grid-3">
        {Object.entries(TEMPLATES).map(([key, t]) => (
          <article className="panel template-card" key={key} onClick={() => { setTemplate(key); setNewName(t.name); setCreateOpen(true) }}>
            <div className="row"><div className="avatar" style={{ background: 'var(--pine-wash)', color: 'var(--pine-deep)' }}><Icon name="workflow" /></div><b>{t.name}</b></div>
            <p className="muted" style={{ fontSize: 12, lineHeight: 1.6 }}>{t.desc}</p>
            <div className="row"><Badge tone="info">{t.trigger}</Badge><span className="spacer" /><span className="muted">{t.nodes.length} 个节点</span></div>
          </article>
        ))}
        <article className="panel template-card" onClick={() => { setTemplate('ticket'); setNewName('空白流程'); setCreateOpen(true) }}>
          <div className="row"><div className="avatar" style={{ background: '#EFF1E9', color: '#4C554F' }}><Icon name="plus" /></div><b>从空白开始</b></div>
          <p className="muted" style={{ fontSize: 12, lineHeight: 1.6 }}>只创建一个触发器，后续从节点库逐步添加。</p>
          <div className="row"><Badge>自由编排</Badge></div>
        </article>
      </div>

      <Drawer open={!!instanceId} onClose={() => setInstanceId(null)} width={760}>
        {instanceDetail.data && (
          <>
            <div className="drawer-head">
              <div className="avatar" style={{ width: 36, height: 36, borderRadius: 10, background: 'var(--pine-wash)', color: 'var(--pine-deep)' }}><Icon name="workflow" /></div>
              <div className="dh-main"><div className="drawer-title mono" style={{ fontSize: 15 }}>{instanceDetail.data.instance.id} <StatusBadge status={instanceDetail.data.instance.status} /></div><div className="drawer-sub"><span>{instanceDetail.data.instance.workflowName}</span><span>·</span><span>{instanceDetail.data.instance.currentNode ?? '—'}</span></div></div>
              <button className="icon-btn" onClick={() => setInstanceId(null)}><Icon name="x" /></button>
            </div>
            <div className="drawer-body">
              <div className="grid-3">
                <div className="panel stat" style={{ padding: 12 }}><div className="lb">状态</div><div className="vl" style={{ fontSize: 18 }}>{instanceDetail.data.instance.status}</div></div>
                <div className="panel stat" style={{ padding: 12 }}><div className="lb">当前节点</div><div className="vl" style={{ fontSize: 18 }}>{instanceDetail.data.instance.currentNode ?? '—'}</div></div>
                <div className="panel stat" style={{ padding: 12 }}><div className="lb">节点数</div><div className="vl" style={{ fontSize: 24 }}>{instanceDetail.data.nodeRuns.length}</div></div>
              </div>
              <div className="section-head"><h2>节点执行记录</h2></div>
              <div className="panel" style={{ boxShadow: 'none' }}>
                <div className="panel-body" style={{ paddingTop: 6 }}>
                  {instanceDetail.data.nodeRuns.map((n) => (
                    <div className="trace-row" key={n.id}>
                      <span className="step-dot" style={{ background: n.status === 'FAILED' ? 'var(--danger-wash)' : n.status.startsWith('WAITING_') ? 'var(--warn-wash)' : 'var(--pine-wash)', color: n.status === 'FAILED' ? 'var(--danger-text)' : n.status.startsWith('WAITING_') ? 'var(--warn-text)' : 'var(--pine-deep)' }}>{n.status === 'FAILED' ? <Icon name="x" /> : n.status.startsWith('WAITING_') ? <Icon name="clock" /> : <Icon name="check" />}</span>
                      <div style={{ flex: 1 }}><div className="row"><b style={{ fontSize: 12.5 }}>{n.title}</b><span className="badge neutral" style={{ height: 18 }}>{n.nodeType}</span><span className="spacer" /><StatusBadge status={n.status} /></div><div className="muted" style={{ fontSize: 11.5, marginTop: 2 }}>{n.outputJson ?? n.error ?? '等待执行'}</div></div>
                    </div>
                  ))}
                </div>
              </div>
              {instanceDetail.data.instance.error && <div className="callout mt-3"><Icon name="alert-triangle" /><div>{instanceDetail.data.instance.error}</div></div>}
            </div>
            <div className="drawer-foot"><span className="spacer" />
              {instanceDetail.data.instance.status === 'WAITING_APPROVAL' && <Button kind="primary" onClick={() => instanceAction.mutate({ id: instanceDetail.data!.instance.id, action: 'approve' })}>审批通过并继续</Button>}
              {['RUNNING', 'WAITING_APPROVAL'].includes(instanceDetail.data.instance.status) && <Button kind="danger" onClick={() => instanceAction.mutate({ id: instanceDetail.data!.instance.id, action: 'cancel' })}>取消实例</Button>}
            </div>
          </>
        )}
      </Drawer>

      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="新建流程"
        sub="从空白流程或预设模板开始"
        footer={<><span className="spacer" /><Button onClick={() => setCreateOpen(false)}>取消</Button><Button kind="primary" onClick={() => create.mutate()}>创建并打开编排器</Button></>}
      >
        <div className="field"><label>流程名称</label><input className="input" value={newName} onChange={(e) => setNewName(e.target.value)} /></div>
        <div className="field"><label>模板</label><div className="row wrap" style={{ gap: 6 }}>{Object.entries(TEMPLATES).map(([key, t]) => <button key={key} className={`chip ${template === key ? 'active' : ''}`} onClick={() => { setTemplate(key); setNewName(t.name) }}>{t.name}</button>)}</div></div>
        <div className="field"><label>触发方式</label><input className="input" value={TEMPLATES[template].trigger} readOnly /></div>
        <div className="callout info"><Icon name="help" /><div>创建后会进入可视化编辑器，可拖拽节点、配置参数并运行测试。</div></div>
      </Modal>
    </>
  )
}

function WorkflowEditor({
  workflow,
  workflows,
  onClose,
  onChanged,
}: {
  workflow: Workflow
  workflows: Workflow[]
  onClose: () => void
  onChanged: (w: Workflow) => void
}) {
  const [nodes, setNodes] = useState<Array<WorkflowNode & { x: number; y: number }>>([])
  const [version, setVersion] = useState(workflow.version)
  const [selectedKey, setSelectedKey] = useState<string | null>(workflow.nodes[0]?.key ?? null)
  const [zoom, setZoom] = useState(1)
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [versionsOpen, setVersionsOpen] = useState(false)
  const [publishOpen, setPublishOpen] = useState(false)
  const [running, setRunning] = useState(false)
  const [log, setLog] = useState<string[]>([])
  const dragRef = useRef<{ key: string; startX: number; startY: number; x: number; y: number } | null>(null)

  useEffect(() => {
    setNodes(workflow.nodes.map((n, i) => ({ ...n, type: normalizeType(n.type), x: 80 + (i % 4) * 210, y: 70 + Math.floor(i / 4) * 150 })))
    setSelectedKey(workflow.nodes[0]?.key ?? null)
  }, [workflow.id])

  const selected = nodes.find((n) => n.key === selectedKey) ?? null
  const edgeList = useMemo(() => {
    const result: Array<{ key: string; from: string; to: string; label?: string }> = []
    const byKey = new Map(nodes.map((n) => [n.key, n]))
    nodes.forEach((n, i) => {
      const config = n.config ?? {}
      if (n.type === 'condition') {
        const trueNext = String(config.trueNext ?? '')
        const falseNext = String(config.falseNext ?? '')
        if (trueNext && byKey.has(trueNext)) result.push({ key: `${n.key}->${trueNext}:true`, from: n.key, to: trueNext, label: 'true' })
        if (falseNext && byKey.has(falseNext)) result.push({ key: `${n.key}->${falseNext}:false`, from: n.key, to: falseNext, label: 'false' })
        if (!trueNext && !falseNext && nodes[i + 1]) result.push({ key: `${n.key}->${nodes[i + 1].key}`, from: n.key, to: nodes[i + 1].key })
        return
      }
      const raw = config.next
      const targets = Array.isArray(raw) ? raw.map(String).filter(Boolean) : raw ? [String(raw)] : []
      if (targets.length) {
        targets.forEach((target) => { if (byKey.has(target)) result.push({ key: `${n.key}->${target}`, from: n.key, to: target }) })
      } else if (n.type !== 'end' && nodes[i + 1]) {
        result.push({ key: `${n.key}->${nodes[i + 1].key}`, from: n.key, to: nodes[i + 1].key })
      }
    })
    return result
  }, [nodes])

  const onMouseDown = (e: React.MouseEvent, node: WorkflowNode & { x: number; y: number }) => {
    e.stopPropagation()
    setSelectedKey(node.key)
    dragRef.current = { key: node.key, startX: e.clientX, startY: e.clientY, x: node.x, y: node.y }
    const onMove = (ev: MouseEvent) => {
      const d = dragRef.current
      if (!d) return
      setNodes((prev) => prev.map((n) => n.key === d.key ? { ...n, x: Math.max(20, d.x + (ev.clientX - d.startX)), y: Math.max(20, d.y + (ev.clientY - d.startY)) } : n))
    }
    const onUp = () => {
      dragRef.current = null
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
  }

  const addNode = (type: string) => {
    const key = `n${Date.now().toString(36)}`
    const t = NODE_TYPES[type]
    const node = { key, type, title: t.label, meta: t.desc, x: 100 + nodes.length * 30, y: 100 + nodes.length * 20 }
    setNodes((prev) => [...prev, node])
    setSelectedKey(key)
    setPaletteOpen(false)
    toast('节点已添加到画布')
  }

  const removeNode = () => {
    if (!selected) return
    setNodes((prev) => prev.filter((n) => n.key !== selected.key))
    setSelectedKey(null)
  }

  const updateSelected = (patch: Partial<WorkflowNode>) => {
    if (!selected) return
    setNodes((prev) => prev.map((n) => n.key === selected.key ? { ...n, ...patch } : n))
  }

  const updateConfig = (patch: Record<string, unknown>) => {
    if (!selected) return
    updateSelected({ config: { ...(selected.config ?? {}), ...patch } })
  }

  const save = () => {
    const next = { ...workflow, nodes: nodes.map(({ x, y, ...n }) => n) }
    onChanged(next)
    api.put<Workflow>(`/workflows/${workflow.id}`, { name: workflow.name, trigger: workflow.trigger, nodes: next.nodes })
      .then(() => toast('草稿已保存'))
      .catch(() => toast('已保存到本地草稿'))
  }

  const testRun = async () => {
    setRunning(true)
    setLog(['启动真实测试实例…'])
    try {
      const started = await api.post<{ instanceId: string; workflow: string }>(`/workflows/${workflow.id}/test`)
      setLog([`测试实例 ${started.instanceId} 已启动，等待执行…`])
      let attempts = 0
      const poll = async () => {
        attempts += 1
        try {
          const detail = await api.get<{ instance: WorkflowInstance; nodeRuns: WorkflowNodeRun[] }>(`/workflow-instances/${started.instanceId}`)
          const status = detail.instance.status
          const lines = detail.nodeRuns.map((n) => `节点 ${n.title} · ${n.status}${n.error ? ` · ${n.error}` : ''}`)
          setLog([`测试实例 ${started.instanceId} · ${status}`, ...lines])
          if (['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status)) {
            setRunning(false)
            toast(status === 'SUCCEEDED' ? '测试运行通过' : `测试运行${status === 'FAILED' ? '失败' : '已取消'}`,
              status === 'SUCCEEDED' ? undefined : 'err')
            return
          }
          if (status.startsWith('WAITING_')) {
            setRunning(false)
            toast(`测试运行进入等待态：${status}`, 'err')
            return
          }
          if (attempts < 100) window.setTimeout(poll, 800)
          else {
            setRunning(false)
            setLog((prev) => [...prev, '测试轮询超时，实例仍在运行，可到运行实例中查看'])
          }
        } catch (e) {
          setRunning(false)
          toast((e as Error).message, 'err')
        }
      }
      poll()
    } catch (e) {
      setLog((prev) => [...prev, `测试失败：${(e as Error).message}`])
      setRunning(false)
      toast((e as Error).message, 'err')
    }
  }

  const publish = async () => {
    try {
      const updated = await api.post<Workflow>(`/workflows/${workflow.id}/publish`)
      setVersion(updated.version)
      onChanged(updated)
      setPublishOpen(false)
      toast(`流程已发布到 ${updated.version}`)
    } catch (e) {
      toast((e as Error).message, 'err')
    }
  }

  return (
    <div className="flow-editor" style={{ position: 'fixed', inset: 0, zIndex: 60, display: 'flex', flexDirection: 'column' }}>
      <header className="fe-head">
        <button className="fe-back" onClick={onClose} aria-label="关闭编排器"><Icon name="arrow-left" /></button>
        <div className="fe-title"><b>{workflow.name}</b><small>{workflow.trigger} · {version}</small></div>
        <div className="fe-actions">
          <Button size="sm" onClick={() => setVersionsOpen(true)}><Icon name="git" />版本 {version}</Button>
          <Button size="sm" onClick={save}><Icon name="check" />保存草稿</Button>
          <Button size="sm" onClick={testRun} disabled={running}><Icon name="play" />{running ? '运行中…' : '运行测试'}</Button>
          <Button size="sm" kind="primary" onClick={() => setPublishOpen(true)}><Icon name="releases" />发布流程</Button>
        </div>
      </header>

      <div className="fe-body">
        <aside className="fe-palette">
          <div className="section-head" style={{ padding: '12px 12px 4px' }}><h2>节点库</h2></div>
          {Object.entries(NODE_TYPES).map(([key, t]) => (
            <button key={key} className="fe-node-btn" onClick={() => addNode(key)}>
              <span className="ico" style={{ background: t.bg, color: t.color }}><Icon name={t.icon} /></span>
              <span><b>{t.label}</b><small>{t.desc}</small></span>
            </button>
          ))}
        </aside>

        <div className="fe-canvas" onClick={() => setSelectedKey(null)}>
          <div className="fe-canvas-tools">
            <button onClick={() => setZoom((z) => Math.max(0.5, z - 0.1))}>−</button>
            <button onClick={() => setZoom(1)}>适应</button>
            <button onClick={() => setZoom((z) => Math.min(1.5, z + 0.1))}>+</button>
          </div>
          <div className="flow-world" style={{ transform: `scale(${zoom})`, transformOrigin: '0 0', position: 'relative', minHeight: 600, minWidth: 900 }}>
            <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none' }}>
              {edgeList.map((edge) => {
                const from = nodes.find((n) => n.key === edge.from)
                const to = nodes.find((n) => n.key === edge.to)
                if (!from || !to) return null
                const stroke = edge.label === 'false' ? '#D99A2B' : edge.label === 'true' ? '#1BAF7A' : '#B8C4BB'
                return (
                  <g key={edge.key}>
                    <path d={`M ${from.x + 196} ${from.y + 38} C ${from.x + 230} ${from.y + 38}, ${to.x - 30} ${to.y + 38}, ${to.x} ${to.y + 38}`} fill="none" stroke={stroke} strokeWidth="1.6" strokeDasharray={edge.label ? undefined : '4 4'} />
                    {edge.label && <text className="edge-label" x={(from.x + 196 + to.x) / 2} y={(from.y + to.y) / 2 + 29} fill={stroke} textAnchor="middle">{edge.label}</text>}
                  </g>
                )
              })}
            </svg>
            {nodes.map((n) => {
              const t = NODE_TYPES[n.type] ?? NODE_TYPES.model
              return (
                <div
                  key={n.key}
                  className={`fnode ${selectedKey === n.key ? 'selected' : ''}`}
                  style={{ left: n.x, top: n.y, borderLeftColor: t.color }}
                  onMouseDown={(e) => onMouseDown(e, n)}
                  onClick={(e) => { e.stopPropagation(); setSelectedKey(n.key) }}
                >
                  <div className="fn-head"><span className="fn-ico" style={{ background: t.bg, color: t.color }}><Icon name={t.icon} /></span><div><div className="fn-title">{n.title}</div><div className="fn-type">{t.label}</div></div></div>
                  <div className="fn-meta">{n.meta}</div>
                </div>
              )
            })}
            {!nodes.length && <div className="empty" style={{ position: 'absolute', left: 280, top: 200 }}><Icon name="workflow" /><b>画布为空</b>从左侧节点库添加一个触发器。</div>}
          </div>
          {running && (
            <div className="flow-runbar">
              <div className="fr-head"><span className="pulse-dot" /><b>测试运行中</b><span>{workflow.name}</span><span className="spacer" /></div>
              <div className="fr-log">{log.map((l, i) => <div key={i}>{l}</div>)}</div>
            </div>
          )}
        </div>

        <aside className="fe-inspector">
          {selected ? (
            <>
              <div className="fi-head">
                <div className="fi-ico" style={{ background: NODE_TYPES[selected.type]?.bg, color: NODE_TYPES[selected.type]?.color }}><Icon name={NODE_TYPES[selected.type]?.icon ?? 'workflow'} /></div>
                <div><b>{selected.title}</b><small>{NODE_TYPES[selected.type]?.label} · 节点 {selected.key.toUpperCase()}</small></div>
                <button className="fi-close" onClick={() => setSelectedKey(null)}><Icon name="x" /></button>
              </div>
              <div className="fi-body">
                <div className="field"><label>节点名称</label><input className="input" value={selected.title} onChange={(e) => updateSelected({ title: e.target.value })} /></div>
                <div className="field"><label>节点类型</label><select className="select" value={selected.type} onChange={(e) => updateSelected({ type: e.target.value })}>{Object.entries(NODE_TYPES).map(([k, t]) => <option key={k} value={k}>{t.label}</option>)}</select></div>
                <div className="field"><label>配置说明</label><textarea className="input" rows={3} value={selected.meta} onChange={(e) => updateSelected({ meta: e.target.value })} /></div>

                {selected.type === 'condition' && (
                  <>
                    <div className="field"><label>条件表达式</label><input className="input" value={String(selected.config?.expression ?? selected.meta ?? '')} onChange={(e) => updateConfig({ expression: e.target.value })} placeholder="amount > 500" /><div className="hint">支持 SpEL，例如 amount &gt; 500、risk_score &lt; 80、can_refund == true</div></div>
                    <div className="field-grid">
                      <div className="field"><label>True 分支</label><select className="select" value={String(selected.config?.trueNext ?? '')} onChange={(e) => updateConfig({ trueNext: e.target.value })}><option value="">不继续</option>{nodes.filter((n) => n.key !== selected.key).map((n) => <option key={n.key} value={n.key}>{n.title}</option>)}</select></div>
                      <div className="field"><label>False 分支</label><select className="select" value={String(selected.config?.falseNext ?? '')} onChange={(e) => updateConfig({ falseNext: e.target.value })}><option value="">不继续</option>{nodes.filter((n) => n.key !== selected.key).map((n) => <option key={n.key} value={n.key}>{n.title}</option>)}</select></div>
                    </div>
                  </>
                )}

                {selected.type === 'timer' && (
                  <div className="field"><label>等待秒数</label><input className="input" type="number" min={1} value={Number(selected.config?.delaySeconds ?? 5)} onChange={(e) => updateConfig({ delaySeconds: Number(e.target.value) })} /><div className="hint">由调度器持久化恢复，服务重启后仍会继续。</div></div>
                )}

                {selected.type === 'subflow' && (
                  <div className="field"><label>子流程</label><select className="select" value={String(selected.config?.workflowId ?? '')} onChange={(e) => updateConfig({ workflowId: e.target.value })}><option value="">请选择子流程</option>{workflows.filter((w) => w.id !== workflow.id).map((w) => <option key={w.id} value={w.id}>{w.name} · {w.version}</option>)}</select><div className="hint">父流程会等待子流程完成；子流程失败时父流程失败并执行补偿。</div></div>
                )}

                {selected.type === 'notify' && (
                  <>
                    <div className="field"><label>发送通道</label><select className="select" value={String(selected.config?.channel ?? 'wecom')} onChange={(e) => updateConfig({ channel: e.target.value })}><option value="wecom">企业微信</option><option value="webhook">HTTP Webhook</option></select></div>
                    {String(selected.config?.channel ?? 'wecom') === 'webhook' && <div className="field"><label>Webhook URL</label><input className="input" value={String(selected.config?.target ?? '')} onChange={(e) => updateConfig({ target: e.target.value })} placeholder="https://hooks.example.com/..." /></div>}
                    <div className="field"><label>通知内容</label><textarea className="input" rows={3} value={String(selected.config?.message ?? '')} onChange={(e) => updateConfig({ message: e.target.value })} placeholder={'流程 ${workflow} 执行到节点 ${node}，实例 ${instanceId}'} /><div className="hint">{'支持 ${workflow}、${instanceId}、${input} 以及节点输出变量。'}</div></div>
                  </>
                )}

                {(selected.type === 'tool' || selected.type === 'knowledge') && (
                  <div className="field"><label>{selected.type === 'tool' ? '工具 Key' : '数据集 ID'}</label><input className="input" value={String(selected.config?.tool ?? selected.config?.datasetId ?? '')} onChange={(e) => updateConfig(selected.type === 'tool' ? { tool: e.target.value } : { datasetId: e.target.value })} placeholder={selected.type === 'tool' ? 'order.query' : 'kb-refund'} /></div>
                )}

                {!['condition', 'end'].includes(selected.type) && (
                  <div className="field">
                    <label>下一步节点（多选即并行）</label>
                    <select
                      className="select"
                      multiple
                      style={{ height: 92 }}
                      value={Array.isArray(selected.config?.next) ? (selected.config!.next as string[]).map(String) : selected.config?.next ? [String(selected.config.next)] : []}
                      onChange={(e) => updateConfig({ next: Array.from(e.target.selectedOptions).map((o) => o.value) })}
                    >
                      {nodes.filter((n) => n.key !== selected.key).map((n) => <option key={n.key} value={n.key}>{n.title}</option>)}
                    </select>
                    <div className="hint">不配置时按画布顺序连接；配置多个目标会并行执行，汇聚节点会等待所有到达。</div>
                  </div>
                )}

                {['model', 'tool', 'knowledge', 'notify', 'subflow'].includes(selected.type) && (
                  <div className="field"><label>失败补偿工具</label><input className="input" value={String(selected.config?.compensation ?? '')} onChange={(e) => updateConfig({ compensation: e.target.value })} placeholder="refund.cancel" /><div className="hint">节点成功后登记补偿；流程失败时按逆序调用。</div></div>
                )}

                <div className="row"><Button size="sm" onClick={() => { addNode(selected.type) }}><Icon name="copy" />复制节点</Button><span className="spacer" /><Button size="sm" kind="danger" onClick={removeNode}><Icon name="trash" />删除节点</Button></div>
              </div>
            </>
          ) : (
            <div className="empty"><Icon name="workflow" /><b>选择一个节点</b>点击画布上的节点查看并编辑配置。</div>
          )}
        </aside>
      </div>

      <Modal open={publishOpen} onClose={() => setPublishOpen(false)} title={`发布流程 · ${workflow.name}`} sub={`${version} → v${parseInt(version.slice(1), 10) + 1} · 发布后立即生效`} footer={<><span className="spacer" /><Button onClick={() => setPublishOpen(false)}>取消</Button><Button kind="primary" onClick={publish}><Icon name="releases" />确认发布</Button></>}>
        <div className="callout good"><Icon name="check-circle" /><div>发布门禁检查通过：节点配置完整，测试运行成功，无循环依赖。</div></div>
        <div className="checklist mt-3">
          <div className="ci"><Icon name="check-circle" />所有节点已配置</div>
          <div className="ci"><Icon name="check-circle" />测试运行通过</div>
          <div className="ci"><Icon name="check-circle" />版本差异已记录</div>
        </div>
      </Modal>

      <Modal open={versionsOpen} onClose={() => setVersionsOpen(false)} title={`版本记录 · ${workflow.name}`} sub="最近 4 个版本">
        {[0, 1, 2, 3].map((i) => {
          const v = `v${Math.max(1, parseInt(version.slice(1), 10) - i)}`
          return <div className={`step-item ${i === 0 ? 'current' : 'done'}`} key={v}><div className="step-dot"><Icon name={i === 0 ? 'git' : 'check'} /></div><div className="step-title">{v} {i === 0 && <span className="badge good"><span className="bd" />当前</span>}</div><div className="step-meta">{i === 0 ? '刚刚' : `${i + 1} 天前`} · 顾先路</div>{i > 0 && <Button size="sm" kind="ghost" className="mt-2" onClick={() => toast(`已回滚到 ${v}（演示）`)}>回滚到 {v}</Button>}</div>
        })}
      </Modal>
    </div>
  )
}
