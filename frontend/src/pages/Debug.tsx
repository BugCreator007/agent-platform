import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api, auth, type Agent, type ChatSession, type ModelInfo, type Provider, type SessionMessage } from '../api'
import { Icon } from '../components/icons'
import { Badge, Button, PageHead, Panel, StatusBadge, toast } from '../components/ui'

type ChatItem = { role: 'user' | 'assistant'; content: string }

export function DebugPage() {
  const queryClient = useQueryClient()
  const [agentId, setAgentId] = useState('')
  const [sessionId, setSessionId] = useState('')
  const [messages, setMessages] = useState<ChatItem[]>([])
  const [input, setInput] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [contexts, setContexts] = useState(0)
  const [runtimeModel, setRuntimeModel] = useState('')
  const [toolNotes, setToolNotes] = useState<string[]>([])
  const abortRef = useRef<AbortController | null>(null)

  const agents = useQuery({ queryKey: ['agents'], queryFn: () => api.get<Agent[]>('/agents') })
  const sessions = useQuery({ queryKey: ['sessions'], queryFn: () => api.get<ChatSession[]>('/sessions') })
  const providers = useQuery({ queryKey: ['providers'], queryFn: () => api.get<Provider[]>('/providers') })
  const models = useQuery({ queryKey: ['models'], queryFn: () => api.get<ModelInfo[]>('/models') })

  useEffect(() => {
    if (!agentId && agents.data?.length) {
      const preferred = agents.data.find((a) => a.providerId === 'deepseek') ?? agents.data[0]
      setAgentId(preferred.id)
    }
  }, [agents.data, agentId])
  useEffect(() => () => abortRef.current?.abort(), [])

  const agent = agents.data?.find((a) => a.id === agentId)
  const provider = providers.data?.find((p) => p.id === agent?.providerId)
  const boundModel = models.data?.find((m) => m.id === agent?.modelId)
  const agentSessions = useMemo(() => (sessions.data ?? []).filter((s) => !agentId || s.agentId === agentId), [sessions.data, agentId])

  const loadSession = async (session: ChatSession) => {
    abortRef.current?.abort()
    setAgentId(session.agentId)
    setSessionId(session.id)
    setContexts(0)
    setRuntimeModel('')
    setToolNotes([])
    try {
      const list = await api.get<SessionMessage[]>(`/sessions/${session.id}/messages`)
      const persisted = list.filter((m) => m.role === 'user' || m.role === 'assistant')
        .map((m) => ({ role: m.role as 'user' | 'assistant', content: m.content }))
      setMessages(persisted.length ? persisted : (session.topic ? [{ role: 'user', content: session.topic }] : []))
    } catch {
      setMessages([])
    }
  }

  const newSession = () => {
    abortRef.current?.abort()
    setSessionId('')
    setMessages([])
    setContexts(0)
    setRuntimeModel('')
    setToolNotes([])
  }

  const send = async () => {
    if (!input.trim() || !agent || streaming) return
    const question = input.trim()
    setMessages((prev) => [...prev, { role: 'user', content: question }, { role: 'assistant', content: '' }])
    setInput('')
    setStreaming(true)
    abortRef.current = new AbortController()
    try {
      const token = auth.token()
      const response = await fetch('/api/v1/runtime/sessions:stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: JSON.stringify({ agentId: agent.id, message: question, sessionId: sessionId || undefined }),
        signal: abortRef.current.signal,
      })
      if (!response.ok || !response.body) throw new Error(`运行失败：${response.status}`)
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const blocks = buffer.split('\n\n')
        buffer = blocks.pop() ?? ''
        for (const block of blocks) {
          const lines = block.split('\n')
          const event = lines.find((l) => l.startsWith('event:'))?.slice(6).trim()
          const data = lines.filter((l) => l.startsWith('data:')).map((l) => l.slice(5).trim()).join('\n')
          if (!event || !data) continue
          if (event === 'meta') {
            try {
              const meta = JSON.parse(data)
              setSessionId(meta.sessionId ?? '')
              setContexts(meta.contexts ?? 0)
              setRuntimeModel(meta.model ?? '')
            } catch { /* ignore */ }
          } else if (event === 'delta') {
            setMessages((prev) => prev.map((m, i) => i === prev.length - 1 ? { ...m, content: m.content + data } : m))
          } else if (event === 'tool') {
            try { const t = JSON.parse(data); setToolNotes((prev) => [...prev, `调用工具 · ${t.name}`]) } catch { /* ignore */ }
          } else if (event === 'tool-result') {
            try { const t = JSON.parse(data); setToolNotes((prev) => [...prev, `工具返回 · ${t.name} · ${t.error ? '失败' : '成功'}`]) } catch { /* ignore */ }
          } else if (event === 'done') {
            try { const d = JSON.parse(data); setSessionId(d.sessionId ?? sessionId); setRuntimeModel(d.model ?? runtimeModel) } catch { /* ignore */ }
            queryClient.invalidateQueries({ queryKey: ['sessions'] })
          } else if (event === 'error') {
            try { toast(JSON.parse(data).message ?? '运行失败', 'err') } catch { toast('运行失败', 'err') }
          }
        }
      }
    } catch (e) {
      if ((e as Error).name !== 'AbortError') toast((e as Error).message, 'err')
    } finally {
      setStreaming(false)
    }
  }

  const selectAgent = (id: string) => {
    setAgentId(id)
    newSession()
  }

  return (
    <>
      <PageHead title="会话调试" sub="选择智能体和模型绑定，实时查看流式回复、知识检索和会话上下文。">
        <Button onClick={newSession} kind="primary"><Icon name="plus" />新建调试会话</Button>
      </PageHead>

      <div className="debug-layout">
        <section className="panel debug-history">
          <div className="panel-head" style={{ paddingBottom: 10 }}>
            <div><h2 className="panel-title">调试会话</h2><p className="panel-sub">按智能体筛选历史</p></div>
          </div>
          <div style={{ padding: '0 14px 12px' }}>
            <select className="select" style={{ width: '100%' }} value={agentId} onChange={(e) => selectAgent(e.target.value)}>
              {(agents.data ?? []).map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
            </select>
          </div>
          <div className="debug-session-list">
            {agentSessions.map((s) => (
              <button key={s.id} className={`debug-session ${s.id === sessionId ? 'active' : ''}`} onClick={() => loadSession(s)}>
                <div className="row"><span className="mono" style={{ fontSize: 10.5 }}>{s.id}</span><span className="spacer" /><StatusBadge status={s.status} /></div>
                <div className="debug-session-topic">{s.topic}</div>
                <div className="muted" style={{ fontSize: 10.5 }}>{s.time} · {s.turns} 轮 · {s.tokens.toLocaleString()} tokens</div>
              </button>
            ))}
            {!agentSessions.length && <div className="muted" style={{ padding: 14, fontSize: 12 }}>还没有调试会话，发送第一条消息即可创建。</div>}
          </div>
        </section>

        <Panel className="debug-chat-panel" noBody>
          <div className="debug-chat-head">
            <div className="row">
              <div className="avatar" style={{ background: 'var(--pine-wash)', color: 'var(--pine-deep)' }}>{agent?.name.slice(0, 1) ?? 'A'}</div>
              <div><div className="nm">{agent?.name ?? '选择智能体'}</div><div className="muted" style={{ fontSize: 11 }}>{provider?.name ?? '未绑定厂商'} · {boundModel?.name ?? agent?.model ?? '未绑定模型'}</div></div>
            </div>
            <span className="spacer" />
            <Badge tone={runtimeModel ? 'good' : 'neutral'}>{runtimeModel || agent?.modelId || '未调用'}</Badge>
            <Button size="sm" onClick={newSession}>清空</Button>
          </div>
          <div className="debug-chat-body">
            {!messages.length && (
              <div className="empty" style={{ marginTop: 40 }}>
                <Icon name="terminal" />
                <b>开始一轮调试</b>
                <div style={{ fontSize: 12, color: 'var(--muted)' }}>消息会实时流式返回，并记录到会话历史。</div>
                <div className="row" style={{ justifyContent: 'center', flexWrap: 'wrap', gap: 6, marginTop: 8 }}>
                  {['已签收商品还能退款吗？', '查询订单 202611130097', '帮我生成退款审核意见'].map((s) => (
                    <button key={s} className="chip" onClick={() => setInput(s)}>{s}</button>
                  ))}
                </div>
              </div>
            )}
            {messages.map((m, i) => (
              <div key={i} className={`msg ${m.role}`}>
                <div className="who">{m.role === 'user' ? '你' : 'AI'}</div>
                <div className="bubble">{m.content}{m.role === 'assistant' && streaming && i === messages.length - 1 && <span className="typing">▍</span>}</div>
              </div>
            ))}
            {toolNotes.map((note, i) => <div key={`tool-${i}`} className="chat-note"><Icon name="tools" /><div>{note}</div></div>)}
          </div>
          <div className="debug-composer">
            <textarea className="input" rows={2} value={input} disabled={streaming} placeholder={streaming ? '模型生成中…' : '输入消息，Enter 发送，Shift+Enter 换行'} onChange={(e) => setInput(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send() } }} />
            <Button kind="primary" onClick={send} disabled={streaming || !input.trim()}>{streaming ? '生成中…' : <><Icon name="send" />发送</>}</Button>
          </div>
        </Panel>

        <div className="stack">
          <Panel title="运行配置" sub="当前智能体绑定">
            <div className="kv">
              <dt>智能体</dt><dd>{agent?.name ?? '—'}</dd>
              <dt>模型厂商</dt><dd>{provider?.name ?? '未绑定'}</dd>
              <dt>模型</dt><dd>{boundModel?.name ?? agent?.model ?? '未绑定'}</dd>
              <dt>模型 ID</dt><dd className="mono" style={{ fontSize: 11 }}>{agent?.modelId ?? '—'}</dd>
              <dt>会话</dt><dd className="mono" style={{ fontSize: 11 }}>{sessionId || '新会话'}</dd>
              <dt>知识上下文</dt><dd>{contexts} 条</dd>
            </div>
          </Panel>
          <Panel title="可用能力" sub="智能体绑定">
            <div className="field"><label>工具</label><div className="row wrap" style={{ gap: 5 }}>{(agent?.tools ?? []).map((t) => <span className="wf-tag" key={t}>{t}</span>)}{!agent?.tools?.length && <span className="muted">未绑定工具</span>}</div></div>
            <div className="field"><label>知识库</label><div className="row wrap" style={{ gap: 5 }}>{(agent?.kb ?? []).map((k) => <span className="wf-tag" key={k}>{k}</span>)}{!agent?.kb?.length && <span className="muted">未绑定知识库</span>}</div></div>
            {agent?.providerId && agent.providerId !== 'deepseek' && (
              <div className="callout warn"><Icon name="alert-triangle" /><div>当前实时网关为 DeepSeek；该智能体绑定的 {provider?.name ?? agent.providerId} 适配器尚未启用，调试会使用 DeepSeek 网关。</div></div>
            )}
            <div className="callout info"><Icon name="spark" /><div>调试会话会调用真实模型并写入会话历史，但不会影响线上会话。</div></div>
          </Panel>
        </div>
      </div>
    </>
  )
}
