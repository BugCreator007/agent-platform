import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  api, auth,
  type Agent,
  type AgentSessionStat,
  type Dataset,
  type Document,
  type ModelInfo,
  type Provider,
  type Tool,
} from '../api'
import { Icon } from '../components/icons'
import {
  Avatar,
  Badge,
  Button,
  Drawer,
  Empty,
  Meter,
  Modal,
  PageHead,
  Panel,
  StatusBadge,
  Tabs,
  toast,
} from '../components/ui'

/** 智能体状态筛选选项 */
const STATUS_FILTERS = [
  { key: 'all', label: '全部' },
  { key: 'running', label: '运行中' },
  { key: 'grayscale', label: '灰度中' },
  { key: 'paused', label: '已暂停' },
  { key: 'draft', label: '草稿' },
]

/** 头像配色循环使用的色调 */
const TONES = ['blue', 'amber', 'aqua', 'violet', 'orange']

/** 智能体管理页：列表筛选、详情抽屉、编辑弹窗、新建向导与沙盒调试 */
export function AgentsPage() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState('all')
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<Agent | null>(null)
  const [detailTab, setDetailTab] = useState('overview')
  const [promptDraft, setPromptDraft] = useState('')
  const [debugAgent, setDebugAgent] = useState<Agent | null>(null)
  const [wizardOpen, setWizardOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<Agent | null>(null)
  const [editForm, setEditForm] = useState({ name: '', owner: '', team: '', providerId: '', modelId: '', model: '', prompt: '', tools: [] as string[], kb: [] as string[], citationKb: [] as string[], answerMode: 'HYBRID' })

  // 基础数据查询：智能体、今日统计（60 秒轮询）、工具、知识库、模型与厂商
  const agents = useQuery({ queryKey: ['agents'], queryFn: () => api.get<Agent[]>('/agents') })
  const agentStats = useQuery({
    queryKey: ['agent-stats'],
    queryFn: () => api.get<AgentSessionStat[]>('/sessions/stats?days=1'),
    refetchInterval: 60000,
  })
  const tools = useQuery({ queryKey: ['tools'], queryFn: () => api.get<Tool[]>('/tools') })
  const datasets = useQuery({ queryKey: ['datasets'], queryFn: () => api.get<Dataset[]>('/datasets') })
  // 严格引用模式只允许向量/混合检索数据集；纯关键词路没有阈值，拒答会失效
  const editHasKeywordKb = (datasets.data ?? []).some((d) => editForm.kb.includes(d.id) && d.retrievalMode === 'keyword')
  const models = useQuery({ queryKey: ['models'], queryFn: () => api.get<ModelInfo[]>('/models') })
  const providers = useQuery({ queryKey: ['providers'], queryFn: () => api.get<Provider[]>('/providers') })

  /** 使智能体列表缓存失效，触发重新拉取 */
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['agents'] })

  /**
   * 智能体操作（暂停 / 恢复 / 发布）。
   * 成功后先本地更新列表缓存，再失效重新请求，减少界面闪烁。
   */
  const action = useMutation({
    mutationFn: ({ id, act }: { id: string; act: string }) => api.post<Agent>(`/agents/${id}/${act}`),
    onSuccess: (agent, vars) => {
      toast(vars.act === 'pause' ? '智能体已暂停' : vars.act === 'resume' ? '智能体已恢复' : '版本已发布')
      queryClient.setQueryData<Agent[]>(['agents'], (old) => old?.map((a) => a.id === agent.id ? agent : a))
      if (selected?.id === agent.id) setSelected(agent)
      refresh()
    },
    onError: (e: Error) => toast(e.message, 'err'),
  })

  /** 保存提示词草稿（不直接发布线上版本） */
  const savePrompt = useMutation({
    mutationFn: () => api.put<Agent>(`/agents/${selected!.id}`, {
      prompt: promptDraft,
      model: selected!.model,
      providerId: selected!.providerId,
      modelId: selected!.modelId,
      tools: selected!.tools,
      kb: selected!.kb,
      citationKb: selected!.citationKb,
    }),
    onSuccess: (agent) => {
      toast('提示词已保存为草稿')
      setSelected(agent)
      queryClient.setQueryData<Agent[]>(['agents'], (old) => old?.map((a) => a.id === agent.id ? agent : a))
      refresh()
    },
    onError: (e: Error) => toast(e.message, 'err'),
  })

  /** 保存编辑弹窗中的完整智能体配置 */
  const saveEdit = useMutation({
    mutationFn: () => api.put<Agent>(`/agents/${editTarget!.id}`, editForm),
    onSuccess: (agent) => {
      toast('智能体已更新')
      queryClient.setQueryData<Agent[]>(['agents'], (old) => old?.map((a) => a.id === agent.id ? agent : a))
      if (selected?.id === agent.id) { setSelected(agent); setPromptDraft(agent.prompt) }
      setEditTarget(null)
      refresh()
    },
    onError: (e: Error) => toast(e.message, 'err'),
  })

  // 切换选中的智能体时，重置提示词草稿并回到概览页签
  useEffect(() => {
    if (selected) {
      setPromptDraft(selected.prompt)
      setDetailTab('overview')
    }
  }, [selected?.id])

  // 将统计列表转成 Map，避免渲染时逐个 find 造成 O(n²)
  const statsById = useMemo(() => new Map((agentStats.data ?? []).map((s) => [s.agentId, s])), [agentStats.data])

  // 把会话统计合并进智能体基础数据，供列表与详情展示
  const enriched = useMemo(() => (agents.data ?? []).map((a) => {
    const s = statsById.get(a.id)
    return { ...a, sessions: s?.sessions ?? 0, success: s?.success ?? 0, p95: s?.p95 ?? '—' }
  }), [agents.data, statsById])

  // 按状态与关键词过滤，关键词匹配名称、标识、负责人与业务线
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return enriched.filter((a) =>
      (status === 'all' || a.status === status) &&
      (!q || `${a.name}${a.code}${a.owner}${a.team}`.toLowerCase().includes(q)),
    )
  }, [enriched, status, query])

  const selectedStat = selected ? statsById.get(selected.id) : undefined
  const selectedSessions = selectedStat?.sessions ?? 0
  const selectedSuccess = selectedStat?.success ?? 0
  const selectedP95 = selectedStat?.p95 ?? '—'

  /** 打开详情抽屉 */
  const openAgent = (agent: Agent) => {
    setSelected(agent)
    setPromptDraft(agent.prompt)
  }

  /** 打开编辑弹窗，并用当前智能体数据初始化表单（数组做浅拷贝避免引用污染） */
  const openEdit = (agent: Agent) => {
    setEditTarget(agent)
    setEditForm({
      name: agent.name, owner: agent.owner, team: agent.team,
      providerId: agent.providerId ?? '', modelId: agent.modelId ?? '', model: agent.model,
      prompt: agent.prompt, tools: [...agent.tools], kb: [...agent.kb], citationKb: [...(agent.citationKb ?? [])],
      answerMode: agent.answerMode ?? 'HYBRID',
    })
  }

  return (
    <>
      <PageHead
        title="智能体"
        sub="创建、调试和运营平台上的智能体。每个智能体拥有独立的提示词、工具与知识库配置。"
      >
        <Button onClick={() => toast('已打开评测中心，可在评测中心运行回归')}><Icon name="evals" />运行评测</Button>
        <Button kind="primary" onClick={() => setWizardOpen(true)}><Icon name="plus" />新建智能体</Button>
      </PageHead>

      <div className="filterbar">
        <div className="row wrap" style={{ gap: 6 }}>
          {STATUS_FILTERS.map((f) => {
            const count = f.key === 'all' ? (agents.data?.length ?? 0) : (agents.data ?? []).filter((a) => a.status === f.key).length
            return (
              <button key={f.key} className={`chip ${status === f.key ? 'active' : ''}`} onClick={() => setStatus(f.key)}>
                {f.label}<span className="count">{count}</span>
              </button>
            )
          })}
        </div>
        <span className="spacer" />
        <div className="search-inline">
          <Icon name="search" />
          <input className="input" placeholder="按名称、负责人筛选" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
        <button className="btn"><Icon name="filter" />业务线</button>
      </div>

      {filtered.length ? (
        <section className="panel">
          <div className="table-wrap">
            <table className="data">
              <thead><tr><th>智能体</th><th>状态</th><th>版本 · 模型</th><th className="num">今日会话</th><th className="num">成功率</th><th className="num">P95 首响</th><th>负责人</th><th style={{ textAlign: 'right' }}>操作</th></tr></thead>
              <tbody>
                {filtered.map((a, i) => (
                  <tr key={a.id} className="clickable" onClick={() => openAgent(a)}>
                    <td>
                      <div className="entity">
                        <Avatar text={a.name.slice(0, 1)} tone={TONES[i % TONES.length]} />
                        <div><div className="nm">{a.name}</div><div className="sub">{a.code} · {a.team}</div></div>
                      </div>
                    </td>
                    <td><StatusBadge status={a.status} /></td>
                    <td>
                      <div style={{ fontSize: 12 }}>{a.version} <span className="muted">· {a.model}</span></div>
                      <div className="sub" style={{ fontSize: 10.5, color: 'var(--muted)' }}>{a.release}</div>
                    </td>
                    <td className="num">{a.sessions ? a.sessions.toLocaleString() : '—'}</td>
                    <td className="num">{a.sessions ? <><Meter value={a.success} tone={a.success < 96 ? 'warn' : ''} />{a.success}%</> : '—'}</td>
                    <td className="num">{a.p95}</td>
                    <td><span style={{ fontSize: 12 }}>{a.owner}</span></td>
                    <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                      <button className="btn sm ghost" title="编辑" onClick={() => openEdit(a)}><Icon name="settings" />编辑</button>
                      <button className="btn sm ghost" title="沙盒调试" onClick={() => setDebugAgent(a)}><Icon name="play" />调试</button>
                      <button className="btn sm" onClick={() => openAgent(a)}>详情<Icon name="chevron-right" /></button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="pager"><span>共 {filtered.length} 个智能体</span><span className="spacer" /><button className="btn" disabled><Icon name="chevron-left" /></button><span>第 1 / 1 页</span><button className="btn" disabled><Icon name="chevron-right" /></button></div>
        </section>
      ) : (
        <Panel><Empty icon="agents" title="没有匹配的智能体">调整筛选条件，或新建一个智能体。<div className="mt-3"><Button kind="primary" onClick={() => setWizardOpen(true)}><Icon name="plus" />新建智能体</Button></div></Empty></Panel>
      )}

      <div className="callout info mt-3">
        <Icon name="help" />
        <div><b style={{ fontWeight: 500 }}>提示：</b>点击任意一行打开智能体详情，可修改提示词、调试对话、发布灰度版本。点击“调试”直接进入沙盒。</div>
      </div>

      <Drawer open={!!selected} onClose={() => setSelected(null)} width={760}>
        {selected && (
          <>
            <div className="drawer-head">
              <Avatar text={selected.name.slice(0, 1)} tone="blue" size={38} />
              <div className="dh-main">
                <div className="drawer-title">{selected.name} <StatusBadge status={selected.status} /></div>
                <div className="drawer-sub"><span>{selected.code}</span><span>·</span><span>{selected.team}</span><span>·</span><span>{selected.version}</span></div>
              </div>
              <button className="icon-btn" onClick={() => setSelected(null)}><Icon name="x" /></button>
            </div>
            <div className="drawer-body">
              <Tabs
                active={detailTab}
                onChange={setDetailTab}
                items={[
                  { key: 'overview', label: '概览' },
                  { key: 'prompt', label: '提示词' },
                  { key: 'tools', label: '工具与知识' },
                  { key: 'versions', label: '版本记录' },
                ]}
              />
              {detailTab === 'overview' && (
                <>
                  <div className="grid-3" style={{ marginBottom: 14 }}>
                    <div className="panel stat" style={{ padding: 13 }}><div className="lb">今日会话</div><div className="vl" style={{ fontSize: 24 }}>{selectedSessions.toLocaleString()}</div></div>
                    <div className="panel stat" style={{ padding: 13 }}><div className="lb">成功率</div><div className="vl" style={{ fontSize: 24 }}>{selectedSessions > 0 ? `${selectedSuccess}%` : '—'}</div></div>
                    <div className="panel stat" style={{ padding: 13 }}><div className="lb">P95 首响</div><div className="vl" style={{ fontSize: 24 }}>{selectedP95}</div></div>
                  </div>
                  <div className="panel" style={{ boxShadow: 'none' }}>
                    <div className="panel-head"><div><h2 className="panel-title">运行配置</h2></div></div>
                    <div className="panel-body">
                      <div className="kv">
                        <dt>模型</dt><dd>{selected.model}{selected.providerId ? ` · ${providers.data?.find(p => p.id === selected.providerId)?.name ?? selected.providerId}` : ''}</dd>
                        <dt>负责人</dt><dd>{selected.owner}</dd>
                        <dt>发布状态</dt><dd>{selected.release}</dd>
                        <dt>更新时间</dt><dd>{selected.updated}</dd>
                        <dt>知识库</dt><dd>{selected.kb.length ? selected.kb.join('、') : '未绑定'}</dd>
                        <dt>回答模式</dt><dd>{selected.answerMode === 'STRICT' ? '严格引用 · 无资料时拒答' : '混合回答'}</dd>
                      </div>
                    </div>
                  </div>
                  <div className="section-head"><h2>系统提示词</h2></div>
                  <div className="prompt-box">{selected.prompt}</div>
                </>
              )}
              {detailTab === 'prompt' && (
                <>
                  <div className="row" style={{ marginBottom: 10 }}>
                    <span className="badge warn"><span className="bd" />草稿 · 未发布</span>
                    <span className="muted" style={{ fontSize: 11 }}>修改将保存为草稿，不影响线上 {selected.version}</span>
                    <span className="spacer" />
                    <Button size="sm" onClick={() => toast('历史版本：v' + (parseInt(selected.version.slice(1), 10) - 1) + ' · 3 天前')}>历史版本</Button>
                  </div>
                  <div className="field">
                    <label>系统提示词</label>
                    <textarea className="input" rows={12} value={promptDraft} onChange={(e) => setPromptDraft(e.target.value)} />
                    <div className="hint">建议写清角色、边界、工具调用顺序和拒答策略。发布前可先在沙盒调试。</div>
                  </div>
                  <div className="row">
                    <Button onClick={() => setPromptDraft(selected.prompt)}>撤销修改</Button>
                    <span className="spacer" />
                    <Button kind="primary" onClick={() => savePrompt.mutate()} disabled={savePrompt.isPending}>保存草稿</Button>
                  </div>
                </>
              )}
              {detailTab === 'tools' && (
                <>
                  <div className="section-head"><h2>已绑定工具</h2></div>
                  <div className="node-list">
                    {selected.tools.length ? selected.tools.map((name) => (
                      <div className="node-card" key={name}><span className="node-index"><Icon name="tools" /></span><div><b className="mono">{name}</b><div className="muted">工具调用权限已开启</div></div></div>
                    )) : <div className="muted">未绑定工具</div>}
                  </div>
                  <div className="section-head"><h2>已授权知识库</h2></div>
                  <div className="node-list">
                    {selected.kb.length ? selected.kb.map((id) => (
                      <div className="node-card" key={id}><span className="node-index"><Icon name="knowledge" /></span><div><b>{datasets.data?.find((d) => d.id === id)?.name ?? id}</b><div className="muted">检索范围 · TopK 由数据集配置决定</div></div></div>
                    )) : <div className="muted">未绑定知识库</div>}
                  </div>
                </>
              )}
              {detailTab === 'versions' && (
                <div className="stepper" style={{ display: 'grid', gap: 0 }}>
                  {versionList(selected).map((v, i) => (
                    <div className={`step-item ${i === 0 ? 'current' : 'done'}`} key={v.version}>
                      <div className="step-dot">{i === 0 ? <Icon name="git" /> : <Icon name="check" />}</div>
                      <div className="step-title">{v.version} {i === 0 && <span className="badge good"><span className="bd" />当前</span>}</div>
                      <div className="step-meta">{v.date} · {v.note}</div>
                      {i > 0 && <Button size="sm" kind="ghost" className="mt-2" onClick={() => toast(`已回滚到 ${v.version}（演示）`)}>回滚至此</Button>}
                    </div>
                  ))}
                </div>
              )}
            </div>
            <div className="drawer-foot">
              <Button onClick={() => openEdit(selected)}><Icon name="settings" />编辑</Button>
              <Button onClick={() => setDebugAgent(selected)}><Icon name="play" />沙盒调试</Button>
              <span className="spacer" />
              {selected.status === 'running'
                ? <Button kind="danger" onClick={() => action.mutate({ id: selected.id, act: 'pause' })}>暂停</Button>
                : <Button onClick={() => action.mutate({ id: selected.id, act: selected.status === 'paused' ? 'resume' : 'publish' })}>{selected.status === 'paused' ? '恢复' : '发布'}</Button>}
              <Button kind="primary" onClick={() => action.mutate({ id: selected.id, act: 'publish' })}>发布新版本</Button>
            </div>
          </>
        )}
      </Drawer>

      <DebugDrawer agent={debugAgent} onClose={() => setDebugAgent(null)} />

      <Modal
        open={!!editTarget}
        onClose={() => setEditTarget(null)}
        title={`编辑智能体 · ${editTarget?.name ?? ''}`}
        sub="修改基本信息、模型绑定、提示词、工具和知识库"
        footer={<><span className="spacer" /><Button onClick={() => setEditTarget(null)}>取消</Button><Button kind="primary" disabled={!editForm.name.trim() || saveEdit.isPending} onClick={() => saveEdit.mutate()}>保存修改</Button></>}
      >
        <div className="field-grid">
          <div className="field"><label>名称</label><input className="input" value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} /></div>
          <div className="field"><label>标识</label><input className="input" value={editTarget?.code ?? ''} readOnly /></div>
          <div className="field"><label>负责人</label><input className="input" value={editForm.owner} onChange={(e) => setEditForm({ ...editForm, owner: e.target.value })} /></div>
          <div className="field"><label>业务线</label><input className="input" value={editForm.team} onChange={(e) => setEditForm({ ...editForm, team: e.target.value })} /></div>
        </div>
        <div className="field"><label>模型厂商</label><select className="select" value={editForm.providerId} onChange={(e) => {
          const providerId = e.target.value
          const first = models.data?.find((m) => m.providerId === providerId)
          setEditForm({ ...editForm, providerId, modelId: first?.id ?? '', model: first?.name ?? '' })
        }}><option value="">未绑定</option>{(providers.data ?? []).map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</select></div>
        <div className="field"><label>模型</label><select className="select" value={editForm.modelId} onChange={(e) => {
          const model = models.data?.find((m) => m.id === e.target.value)
          setEditForm({ ...editForm, modelId: model?.id ?? '', model: model?.name ?? '' })
        }}><option value="">未绑定</option>{(models.data ?? []).filter((m) => m.providerId === editForm.providerId).map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}</select></div>
        <div className="field"><label>系统提示词</label><textarea className="input" rows={7} value={editForm.prompt} onChange={(e) => setEditForm({ ...editForm, prompt: e.target.value })} /></div>
        <div className="field">
          <label>回答模式</label>
          <select className="select" value={editForm.answerMode} onChange={(e) => setEditForm({ ...editForm, answerMode: e.target.value })}>
            <option value="HYBRID">混合回答 · 无资料时仍由模型回答</option>
            <option value="STRICT" disabled={!editForm.kb.length || editHasKeywordKb}>严格引用 · 无资料时直接拒答</option>
          </select>
          <div className="hint">严格模式要求至少绑定一个知识库，且不能包含纯关键词检索数据集；检索不到资料时不调用模型。</div>
        </div>
        <div className="field"><label>工具</label><div className="row wrap" style={{ gap: 6 }}>{(tools.data ?? []).map((t) => <button key={t.name} className={`chip ${editForm.tools.includes(t.name) ? 'active' : ''}`} onClick={() => setEditForm({ ...editForm, tools: editForm.tools.includes(t.name) ? editForm.tools.filter((x) => x !== t.name) : [...editForm.tools, t.name] })}>{t.name}</button>)}</div></div>
        <div className="field">
          <label>知识库</label>
          <div className="row wrap" style={{ gap: 6 }}>
            {(datasets.data ?? []).map((d) => {
              const selectedKb = editForm.kb.includes(d.id)
              return <button key={d.id} className={`chip ${selectedKb ? 'active' : ''}`} onClick={() => setEditForm({
                ...editForm,
                kb: selectedKb ? editForm.kb.filter((x) => x !== d.id) : [...editForm.kb, d.id],
                citationKb: selectedKb ? editForm.citationKb.filter((x) => x !== d.id) : [...editForm.citationKb, d.id],
                // 移除最后一个知识库或选入纯关键词数据集时，严格模式不再合法，自动回退混合回答
                answerMode: (selectedKb && editForm.kb.length === 1 && editForm.answerMode === 'STRICT')
                  || (!selectedKb && editForm.answerMode === 'STRICT' && d.retrievalMode === 'keyword')
                  ? 'HYBRID' : editForm.answerMode,
              })}>{d.name}</button>
            })}
          </div>
          {!!editForm.kb.length && (
            <div className="checklist mt-2">
              <div className="hint">选择哪些知识库需要在回答末尾返回文档链接：</div>
              {editForm.kb.map((id) => (
                <label className="ci" key={id}>
                  <input type="checkbox" checked={editForm.citationKb.includes(id)} onChange={(e) => setEditForm({
                    ...editForm,
                    citationKb: e.target.checked ? [...editForm.citationKb, id] : editForm.citationKb.filter((x) => x !== id),
                  })} />
                  {datasets.data?.find((d) => d.id === id)?.name ?? id} · 返回文档链接
                </label>
              ))}
            </div>
          )}
        </div>
      </Modal>

      <CreateAgentWizard
        open={wizardOpen}
        onClose={() => setWizardOpen(false)}
        models={models.data ?? []}
        providers={providers.data ?? []}
        tools={tools.data ?? []}
        datasets={datasets.data ?? []}
        onCreated={(agent) => {
          setWizardOpen(false)
          refresh()
          toast('智能体已创建')
          setSelected(agent)
        }}
      />
    </>
  )
}

/** 根据当前版本号生成最近 4 个版本的演示数据 */
function versionList(agent: Agent) {
  const current = parseInt(agent.version.replace(/\D/g, ''), 10) || 1
  const notes = ['提示词调整，补充退款边界', '增加大额退款审批工具', '优化工具调用顺序', '初始版本']
  return Array.from({ length: 4 }, (_, i) => ({
    version: `v${current - i}`,
    date: i === 0 ? '刚刚' : `${i + 1} 天前`,
    note: notes[i],
  }))
}

/**
 * 沙盒调试抽屉。
 * 通过 fetch + ReadableStream 读取 SSE 流：按空行切分事件块，解析 event/data 字段，
 * 处理 meta（会话与检索信息）、delta（增量文本）、tool / tool-result（工具轨迹）、
 * done（结束）与 error（错误提示）。组件卸载或切换智能体时中止请求。
 */
function DebugDrawer({ agent, onClose }: { agent: Agent | null; onClose: () => void }) {
  const [messages, setMessages] = useState<Array<{ role: 'user' | 'bot'; text: string }>>([])
  const [input, setInput] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [sessionId, setSessionId] = useState('')
  const [contexts, setContexts] = useState(0)
  const [toolNotes, setToolNotes] = useState<string[]>([])
  const abortRef = useRef<AbortController | null>(null)
  // 切换智能体时重置会话状态，并中止上一个未完成的流
  useEffect(() => { setMessages([]); setInput(''); setStreaming(false); setSessionId(''); setContexts(0); setToolNotes([]); abortRef.current?.abort() }, [agent?.id])
  // 组件卸载时确保中止进行中的请求
  useEffect(() => () => abortRef.current?.abort(), [])

  /** 发送消息并以 SSE 流式接收回复 */
  const send = async () => {
    if (!input.trim() || !agent || streaming) return
    const question = input.trim()
    setMessages((prev) => [...prev, { role: 'user', text: question }, { role: 'bot', text: '' }])
    setInput('')
    setStreaming(true)
    abortRef.current = new AbortController()
    try {
      // 手动附带 Authorization 头，SSE 请求同样需要鉴权 token
      const token = auth.token()
      const response = await fetch('/api/v1/runtime/sessions:stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: JSON.stringify({ agentId: agent.id, message: question }),
        signal: abortRef.current.signal,
      })
      if (!response.ok || !response.body) throw new Error(`运行失败：${response.status}`)
      // 流式读取：以 \n\n 分隔 SSE 事件块，最后一段不完整的数据留在 buffer 中等待下一块
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const events = buffer.split('\n\n')
        buffer = events.pop() ?? ''
        for (const block of events) {
          // 每个事件块包含 event: 与可多行的 data: 字段
          const lines = block.split('\n')
          const event = lines.find((l) => l.startsWith('event:'))?.slice(6).trim()
          const data = lines.filter((l) => l.startsWith('data:')).map((l) => l.slice(5).trim()).join('\n')
          if (!event || !data) continue
          if (event === 'meta') {
            try {
              const meta = JSON.parse(data)
              setSessionId(meta.sessionId ?? '')
              setContexts(meta.contexts ?? 0)
            } catch { /* ignore */ }
          } else if (event === 'delta') {
            // 增量文本追加到最后一条机器人消息上
            setMessages((prev) => prev.map((m, i) => i === prev.length - 1 ? { ...m, text: m.text + data } : m))
          } else if (event === 'tool') {
            try { const t = JSON.parse(data); setToolNotes((prev) => [...prev, `调用工具 · ${t.name}`]) } catch { /* ignore */ }
          } else if (event === 'tool-result') {
            try { const t = JSON.parse(data); setToolNotes((prev) => [...prev, `工具返回 · ${t.name} · ${t.error ? '失败' : '成功'}`]) } catch { /* ignore */ }
          } else if (event === 'done') {
            try { const d = JSON.parse(data); setSessionId(d.sessionId ?? '') } catch { /* ignore */ }
            toast('沙盒运行完成')
          } else if (event === 'error') {
            try { toast(JSON.parse(data).message ?? '运行失败', 'err') } catch { toast('运行失败', 'err') }
          }
        }
      }
    } catch (e) {
      // 主动中止不提示错误，其余异常通过 Toast 展示
      if ((e as Error).name !== 'AbortError') toast((e as Error).message, 'err')
    } finally {
      setStreaming(false)
    }
  }

  return (
    <Drawer open={!!agent} onClose={onClose} width={640}>
      {agent && (
        <>
          <div className="drawer-head">
            <Avatar text={agent.name.slice(0, 1)} tone="amber" size={36} />
            <div className="dh-main"><div className="drawer-title">沙盒调试 · {agent.name}</div><div className="drawer-sub"><span>{agent.model}</span><span>·</span><span>不影响线上会话</span></div></div>
            <button className="icon-btn" onClick={onClose}><Icon name="x" /></button>
          </div>
          <div className="drawer-body">
            <div className="chat">
              {!messages.length && (
                <div className="chat-note"><Icon name="spark" /><div>试试问一个业务问题。调试沙盒会展示模型回复，但不会调用真实外部工具。</div></div>
              )}
              {messages.map((m, i) => (
                <div key={i} className={`msg ${m.role}`}>
                  <div className="who">{m.role === 'user' ? '你' : 'AI'}</div>
                  <div className="bubble">{m.text}</div>
                </div>
              ))}
              {toolNotes.map((note, i) => <div key={`tool-${i}`} className="chat-note"><Icon name="tools" /><div>{note}</div></div>)}
            </div>
            <div className="chat-input">
              <input className="input" value={input} onChange={(e) => setInput(e.target.value)} placeholder={streaming ? '模型生成中…' : '输入问题，回车发送…'} disabled={streaming} onKeyDown={(e) => { if (e.key === 'Enter') send() }} />
              <Button kind="primary" onClick={send} disabled={streaming || !input.trim()}>{streaming ? '生成中…' : <><Icon name="send" />发送</>}</Button>
            </div>
            {(sessionId || contexts > 0) && <div className="chat-note" style={{ marginTop: 8 }}>会话 {sessionId} · 检索到 {contexts} 条知识片段 · 流式输出</div>}
            <div className="chat-suggest">
              {['订单已签收还能退款吗？', '查询订单 20261113001', '帮我生成退款审核意见'].map((s) => (
                <button key={s} className="chip" onClick={() => setInput(s)}>{s}</button>
              ))}
            </div>
          </div>
          <div className="drawer-foot"><span className="muted">沙盒会话不产生费用</span><span className="spacer" /><Button onClick={() => setMessages([])}>清空</Button><Button kind="primary" onClick={onClose}>完成</Button></div>
        </>
      )}
    </Drawer>
  )
}

/**
 * 新建智能体三步向导：基本信息 → 模型与能力 → 确认创建。
 * 打开时默认选择 DeepSeek V4（找不到时回退到 DeepSeek 或第一个模型）。
 */
function CreateAgentWizard({
  open, onClose, models, providers, tools, datasets, onCreated,
}: {
  open: boolean
  onClose: () => void
  models: ModelInfo[]
  providers: Provider[]
  tools: Tool[]
  datasets: Dataset[]
  onCreated: (agent: Agent) => void
}) {
  const [step, setStep] = useState(0)
  const [form, setForm] = useState({
    name: '', code: '', owner: '顾先路', team: '客服平台',
    providerId: (models.find((m) => m.providerId === 'deepseek' && m.name.includes('V4')) ?? models.find((m) => m.providerId === 'deepseek') ?? models[0])?.providerId ?? '',
    modelId: (models.find((m) => m.providerId === 'deepseek' && m.name.includes('V4')) ?? models.find((m) => m.providerId === 'deepseek') ?? models[0])?.id ?? '',
    model: (models.find((m) => m.providerId === 'deepseek' && m.name.includes('V4')) ?? models.find((m) => m.providerId === 'deepseek') ?? models[0])?.name ?? '', prompt: '你是企业的智能助手，回答前先核实信息，不承诺系统外的事项。',
    tools: [] as string[], kb: [] as string[], citationKb: [] as string[], answerMode: 'HYBRID',
  })
  /** 创建提交，成功后交给父组件刷新列表并打开详情 */
  const create = useMutation({
    mutationFn: () => api.post<Agent>('/agents', form),
    onSuccess: onCreated,
    onError: (e: Error) => toast(e.message, 'err'),
  })
  // 每次打开向导时回到第一步，并把默认模型同步到表单
  useEffect(() => {
    if (open && models.length) {
      const preferred = models.find((m) => m.providerId === 'deepseek' && m.name.includes('V4')) ?? models.find((m) => m.providerId === 'deepseek') ?? models[0]
      setStep(0)
      setForm((f) => ({ ...f, providerId: preferred.providerId, modelId: preferred.id, model: preferred.name }))
    }
  }, [open, models.length])

  const update = (patch: Partial<typeof form>) => setForm((f) => ({ ...f, ...patch }))
  // 严格模式只能绑定向量/混合检索数据集
  const hasKeywordKb = datasets.some((d) => form.kb.includes(d.id) && d.retrievalMode === 'keyword')
  const toggle = (key: 'tools' | 'kb', value: string) => setForm((f) => {
    const selected = f[key].includes(value)
    const keyword = key === 'kb' && datasets.find((d) => d.id === value)?.retrievalMode === 'keyword'
    return {
      ...f,
      [key]: selected ? f[key].filter((x) => x !== value) : [...f[key], value],
      // 勾选知识库时默认开启引用返回；取消知识库时同步移除引用配置。
      ...(key === 'kb' ? { citationKb: selected ? f.citationKb.filter((x) => x !== value) : [...f.citationKb, value] } : {}),
      // 移除最后一个知识库或选入纯关键词数据集时，严格模式不再合法，自动回退混合回答
      ...(key === 'kb' && f.answerMode === 'STRICT' && ((selected && f.kb.length === 1) || (!selected && keyword))
        ? { answerMode: 'HYBRID' } : {}),
    }
  })

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="新建智能体"
      sub="三步完成：基本信息、模型与能力、确认创建"
      footer={
        <>
          {step > 0 && <Button onClick={() => setStep(step - 1)}>上一步</Button>}
          <span className="spacer" />
          <Button onClick={onClose}>取消</Button>
          {step < 2
            ? <Button kind="primary" disabled={step === 0 && (!form.name.trim() || !form.code.trim())} onClick={() => setStep(step + 1)}>下一步</Button>
            : <Button kind="primary" onClick={() => create.mutate()} disabled={create.isPending || (form.answerMode === 'STRICT' && (!form.kb.length || hasKeywordKb))}>创建智能体</Button>}
        </>
      }
    >
      <div className="wizard-steps">
        {['基本信息', '模型与能力', '确认创建'].map((label, i) => (
          <div key={label} style={{ display: 'flex', alignItems: 'center', flex: i < 2 ? 1 : 'none' }}>
            <div className={`wizard-step ${step === i ? 'active' : step > i ? 'done' : ''}`}>
              <span className="n">{step > i ? '✓' : i + 1}</span>{label}
            </div>
            {i < 2 && <span className="wizard-bar" />}
          </div>
        ))}
      </div>
      <div className="wizard-body">
        {step === 0 && (
          <>
            <div className="field-grid">
              <div className="field"><label>名称</label><input className="input" value={form.name} onChange={(e) => update({ name: e.target.value })} placeholder="售后工单助手" /></div>
              <div className="field"><label>标识</label><input className="input" value={form.code} onChange={(e) => update({ code: e.target.value })} placeholder="svc-ticket" /></div>
              <div className="field"><label>负责人</label><input className="input" value={form.owner} onChange={(e) => update({ owner: e.target.value })} /></div>
              <div className="field"><label>业务线</label><input className="input" value={form.team} onChange={(e) => update({ team: e.target.value })} /></div>
            </div>
            <div className="field"><label>简介</label><div className="hint">创建后可继续配置提示词、工具、知识库和发布策略。</div></div>
          </>
        )}
        {step === 1 && (
          <>
            <div className="field"><label>模型厂商</label><select className="select" value={form.providerId} onChange={(e) => {
              const providerId = e.target.value
              const first = models.find((m) => m.providerId === providerId)
              update({ providerId, modelId: first?.id ?? '', model: first?.name ?? '' })
            }}>{providers.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}</select></div>
            <div className="field"><label>模型</label><select className="select" value={form.modelId} onChange={(e) => {
              const model = models.find((m) => m.id === e.target.value)
              update({ modelId: model?.id ?? '', model: model?.name ?? '' })
            }}>{models.filter((m) => m.providerId === form.providerId).map((m) => <option key={m.id} value={m.id}>{m.name} · {m.context} 上下文</option>)}</select></div>
            <div className="field"><label>系统提示词</label><textarea className="input" rows={5} value={form.prompt} onChange={(e) => update({ prompt: e.target.value })} /></div>
            <div className="field">
              <label>回答模式</label>
              <select className="select" value={form.answerMode} onChange={(e) => update({ answerMode: e.target.value })}>
                <option value="HYBRID">混合回答 · 无资料时仍由模型回答</option>
                <option value="STRICT" disabled={!form.kb.length || hasKeywordKb}>严格引用 · 无资料时直接拒答</option>
              </select>
              <div className="hint">严格模式要求至少绑定一个知识库，且不能包含纯关键词检索数据集。</div>
            </div>
            <div className="field">
              <label>工具</label>
              <div className="row wrap" style={{ gap: 6 }}>
                {tools.slice(0, 8).map((t) => <button key={t.name} className={`chip ${form.tools.includes(t.name) ? 'active' : ''}`} onClick={() => toggle('tools', t.name)}>{t.name}</button>)}
              </div>
            </div>
            <div className="field">
              <label>知识库</label>
              <div className="row wrap" style={{ gap: 6 }}>
                {datasets.map((d) => <button key={d.id} className={`chip ${form.kb.includes(d.id) ? 'active' : ''}`} onClick={() => toggle('kb', d.id)}>{d.name}</button>)}
              </div>
              {!!form.kb.length && (
                <div className="checklist mt-2">
                  <div className="hint">选择哪些知识库需要在回答末尾返回文档链接：</div>
                  {form.kb.map((id) => (
                    <label className="ci" key={id}>
                      <input type="checkbox" checked={form.citationKb.includes(id)} onChange={(e) => update({
                        citationKb: e.target.checked ? [...form.citationKb, id] : form.citationKb.filter((x) => x !== id),
                      })} />
                      {datasets.find((d) => d.id === id)?.name ?? id} · 返回文档链接
                    </label>
                  ))}
                </div>
              )}
            </div>
          </>
        )}
        {step === 2 && (
          <div className="panel" style={{ boxShadow: 'none' }}>
            <div className="panel-head"><div><h2 className="panel-title">{form.name || '未命名智能体'}</h2><p className="panel-sub">{form.code} · {form.team}</p></div><span className="badge outline"><span className="bd" />草稿</span></div>
            <div className="panel-body">
              <div className="kv">
                <dt>模型厂商</dt><dd>{providers.find((p) => p.id === form.providerId)?.name ?? '未绑定'}</dd>
                <dt>模型</dt><dd>{form.model}</dd>
                <dt>负责人</dt><dd>{form.owner}</dd>
                <dt>工具</dt><dd>{form.tools.length ? form.tools.join('、') : '未绑定'}</dd>
                <dt>知识库</dt><dd>{form.kb.length ? form.kb.map((id) => datasets.find((d) => d.id === id)?.name ?? id).join('、') : '未绑定'}</dd>
                <dt>返回文档链接</dt><dd>{form.citationKb.length ? form.citationKb.map((id) => datasets.find((d) => d.id === id)?.name ?? id).join('、') : '关闭'}</dd>
                <dt>回答模式</dt><dd>{form.answerMode === 'STRICT' ? '严格引用 · 无资料时拒答' : '混合回答'}</dd>
              </div>
              <div className="section-head"><h2>系统提示词</h2></div>
              <div className="prompt-box">{form.prompt}</div>
            </div>
          </div>
        )}
      </div>
    </Modal>
  )
}
