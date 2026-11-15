import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import {
  api,
  session,
  streamChat,
  type UserAgent,
  type UserInfo,
} from './api'

/** 单次工具调用在气泡下方的状态芯片 */
type ToolChip = { name: string; status: 'running' | 'done' | 'error'; detail?: string }
/** 聊天消息（用户或助手），pending 表示正在流式生成 */
type ChatMessage = {
  id: string
  role: 'user' | 'assistant'
  content: string
  pending?: boolean
  tools?: ToolChip[]
}
/** localStorage 中按用户与智能体保存的对话快照 */
type StoredChat = { sessionId: string | null; messages: ChatMessage[] }

/** 对话留存键：按用户名 + 智能体隔离，不同用户/智能体的记录互不覆盖 */
const storageKey = (user: UserInfo, agentId: string) => `agent_user_chat_${user.username}_${agentId}`
/** 构建版本号，由 Vite define 注入；本地开发时显示 dev */
const BUILD_STAMP = typeof __BUILD_STAMP__ === 'undefined' ? 'dev' : __BUILD_STAMP__

/** 生成消息唯一 ID，优先使用 crypto.randomUUID，旧环境回退为时间戳 + 随机串 */
function uid() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`
}

/** 从 localStorage 恢复对话；数据缺失或损坏时返回空状态，不影响页面渲染 */
function loadStored(user: UserInfo, agentId: string): StoredChat {
  try {
    const raw = localStorage.getItem(storageKey(user, agentId))
    if (!raw) return { sessionId: null, messages: [] }
    const parsed = JSON.parse(raw) as StoredChat
    return {
      sessionId: parsed.sessionId ?? null,
      messages: Array.isArray(parsed.messages) ? parsed.messages : [],
    }
  } catch {
    return { sessionId: null, messages: [] }
  }
}

/**
 * 用户端根组件：登录态、智能体目录与聊天页切换。
 * 登录信息来自 localStorage；进入聊天页后由 Chat 组件接管。
 */
export default function App() {
  const [user, setUser] = useState<UserInfo | null>(() => session.user())
  const [agents, setAgents] = useState<UserAgent[]>([])
  const [active, setActive] = useState<UserAgent | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  // 登录后加载可用智能体；用 cancelled 标记避免组件卸载后写状态
  useEffect(() => {
    if (!user) return
    let cancelled = false
    setLoading(true)
    setError('')
    api
      .agents()
      .then((list) => {
        if (!cancelled) setAgents(list)
      })
      .catch((err: Error) => {
        if (cancelled) return
        // token 缺失说明登录态已失效，直接退回登录页
        if (!session.token()) {
          setUser(null)
          return
        }
        setError(err.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [user])

  /** 退出登录：清除本地 token/资料与当前页面状态 */
  const logout = () => {
    session.clear()
    setUser(null)
    setAgents([])
    setActive(null)
  }

  if (!user) {
    return (
      <Login
        onLogin={(profile) => {
          session.setToken(profile.accessToken)
          session.setUser(profile.user)
          setUser(profile.user)
        }}
      />
    )
  }

  if (active) {
    return <Chat user={user} agent={active} onBack={() => setActive(null)} />
  }

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">AI</span>
          <div>
            <strong>企业智能助手</strong>
            <span className="brand-sub">Agent Portal</span>
          </div>
        </div>
        <div className="userbox">
          <span className="avatar">{user.displayName.slice(0, 1)}</span>
          <span className="user-name">{user.displayName}</span>
          <button className="ghost" onClick={logout}>退出</button>
        </div>
      </header>

      <main className="home">
        <section className="hero">
          <p className="eyebrow">欢迎回来，{user.displayName}</p>
          <h1>从一个智能体开始</h1>
          <p className="hero-sub">选择一个业务助手，直接提问。回答会引用企业知识库，并保留你的对话记录。</p>
        </section>

        {error && <div className="banner error">{error}</div>}

        {loading ? (
          <div className="grid">
            {[0, 1, 2].map((i) => <div className="card skeleton" key={i} />)}
          </div>
        ) : agents.length === 0 ? (
          <div className="empty">暂时没有开放的智能体，请联系管理员上线后再试。</div>
        ) : (
          <div className="grid">
            {agents.map((agent, index) => (
              <button className="card agent-card" key={agent.id} onClick={() => setActive(agent)}>
                <div className="card-top">
                  <span className={`agent-avatar tone-${index % 5}`}>{agent.name.slice(0, 1)}</span>
                  <span className={`status ${agent.status}`}>{agent.status === 'grayscale' ? '灰度开放' : '在线'}</span>
                </div>
                <h2>{agent.name}</h2>
                <p>{agent.description}</p>
                <div className="chips">
                  <span className="chip">{agent.model}</span>
                  {agent.knowledgeBases > 0 && <span className="chip">{agent.knowledgeBases} 个知识库</span>}
                  {agent.tools.length > 0 && <span className="chip">{agent.tools.length} 个工具</span>}
                </div>
                <span className="card-cta">开始对话 <Arrow /></span>
              </button>
            ))}
          </div>
        )}

        <p className="footnote">对话由企业 Agent 平台驱动 · 构建 {BUILD_STAMP}</p>
      </main>
    </div>
  )
}

/** 登录页：提交账号密码，成功后把 token 与用户资料写入 localStorage */
function Login({ onLogin }: { onLogin: (profile: { accessToken: string; user: UserInfo }) => void }) {
  const [username, setUsername] = useState('viewer')
  const [password, setPassword] = useState('viewer123')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  /** 提交登录表单，失败时展示后端错误 */
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!username.trim() || !password) return
    setLoading(true)
    setError('')
    try {
      onLogin(await api.login(username.trim(), password))
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <section className="login-brand">
        <span className="brand-mark large">AI</span>
        <h1>企业智能助手</h1>
        <p>把业务知识、工具和模型能力，交到每一位同事手里。</p>
        <ul>
          <li>售后、退款、发票等场景即问即答</li>
          <li>回答引用知识库来源，可追溯</li>
          <li>对话记录自动保留，随时继续</li>
        </ul>
      </section>
      <section className="login-panel">
        <form onSubmit={submit}>
          <h2>登录</h2>
          <p className="muted">使用平台账号进入智能助手</p>
          <label>
            账号
            <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
          </label>
          <label>
            密码
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
            />
          </label>
          {error && <div className="banner error">{error}</div>}
          <button className="primary" disabled={loading}>{loading ? '登录中…' : '进入助手'}</button>
          <p className="hint">演示账号：viewer / viewer123</p>
        </form>
      </section>
    </div>
  )
}

/**
 * 聊天页。
 * 负责消息列表、SSE 流式接收、工具调用状态展示、localStorage 会话留存、
 * 历史消息恢复与中止/新建对话。
 */
function Chat({ user, agent, onBack }: { user: UserInfo; agent: UserAgent; onBack: () => void }) {
  // 首次进入时从 localStorage 恢复该用户与该智能体的会话
  const stored = useMemo(() => loadStored(user, agent.id), [user, agent.id])
  const [sessionId, setSessionId] = useState<string | null>(stored.sessionId)
  const [messages, setMessages] = useState<ChatMessage[]>(stored.messages)
  const [input, setInput] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [error, setError] = useState('')
  const abortRef = useRef<AbortController | null>(null)
  const listRef = useRef<HTMLDivElement>(null)
  const historyLoaded = useRef(false)
  const initialSessionId = useRef(stored.sessionId)

  // 若存在历史会话，仅首次进入时从后端拉取消息；失败则保留本地记录
  useEffect(() => {
    const sid = initialSessionId.current
    if (!sid || historyLoaded.current) return
    historyLoaded.current = true
    let cancelled = false
    api
      .messages(sid)
      .then((list) => {
        if (cancelled) return
        const restored: ChatMessage[] = list
          .filter((m) => m.role === 'user' || m.role === 'assistant')
          .map((m) => ({ id: m.id, role: m.role as 'user' | 'assistant', content: m.content }))
        if (restored.length) setMessages(restored)
      })
      .catch(() => {
        /* 保留本地记录 */
      })
    return () => {
      cancelled = true
    }
  }, [])

  // 会话留存：消息或 sessionId 变化后延迟 250ms 写入 localStorage，避免流式期间频繁写盘
  useEffect(() => {
    const timer = setTimeout(() => {
      localStorage.setItem(storageKey(user, agent.id), JSON.stringify({ sessionId, messages }))
    }, 250)
    return () => clearTimeout(timer)
  }, [messages, sessionId, user, agent.id])

  // 新消息到达时滚动到底部
  useEffect(() => {
    const node = listRef.current
    if (node) node.scrollTop = node.scrollHeight
  }, [messages])

  // 组件卸载时中止正在进行的流式请求
  useEffect(() => () => abortRef.current?.abort(), [])

  /** 按 id 更新指定助手消息的局部状态 */
  const updateAssistant = (id: string, update: (message: ChatMessage) => ChatMessage) => {
    setMessages((prev) => prev.map((message) => (message.id === id ? update(message) : message)))
  }

  /**
   * 发送消息并通过 SSE 接收回复。
   * 先乐观插入用户消息与空的助手消息；收到首个 delta 前显示打字动画，
   * 工具事件更新对应芯片状态；结束后清理 pending。
   */
  const send = async () => {
    const text = input.trim()
    if (!text || streaming) return
    setError('')
    let received = false
    const assistantId = `a_${uid()}`
    try {
      setInput('')
      setMessages((prev) => [
        ...prev,
        { id: `u_${uid()}`, role: 'user', content: text },
        { id: assistantId, role: 'assistant', content: '', pending: true },
      ])
      setStreaming(true)
      const controller = new AbortController()
      abortRef.current = controller
      await streamChat(
        { agentId: agent.id, message: text, sessionId: sessionId ?? undefined },
        {
          onMeta: (meta) => {
            // 后端在 meta 中返回会话 ID，用于后续消息续接
            if (typeof meta.sessionId === 'string' && meta.sessionId) setSessionId(meta.sessionId)
          },
          onDelta: (delta) => {
            received = true
            updateAssistant(assistantId, (m) => ({ ...m, content: m.content + delta }))
          },
          onTool: (tool) =>
            // 记录工具调用开始，detail 截断展示参数
            updateAssistant(assistantId, (m) => ({
              ...m,
              tools: [...(m.tools ?? []), { name: tool.name, status: 'running', detail: formatArgs(tool.arguments) }],
            })),
          onToolResult: (result) =>
            // 回填最近一次同名工具调用的结果状态
            updateAssistant(assistantId, (m) => {
              const tools = [...(m.tools ?? [])]
              const index = tools.map((t) => t.name).lastIndexOf(result.name)
              if (index >= 0) {
                tools[index] = {
                  ...tools[index],
                  status: result.error ? 'error' : 'done',
                  detail: result.text ? String(result.text).slice(0, 120) : tools[index].detail,
                }
              }
              return { ...m, tools }
            }),
          onError: (message) => {
            setError(message)
            updateAssistant(assistantId, (m) => ({ ...m, pending: false }))
          },
        },
        controller.signal,
      )
      // 流结束但未收到任何增量文本，提示重试
      if (!received) setError('没有收到回复，请稍后重试')
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      // 主动停止不视为错误
      if (!(err instanceof Error && err.name === 'AbortError')) {
        setError(message || '发送失败')
        updateAssistant(assistantId, (m) => ({ ...m, pending: false }))
      }
    } finally {
      setStreaming(false)
      abortRef.current = null
      updateAssistant(assistantId, (m) => ({ ...m, pending: false }))
    }
  }

  /** 停止当前的流式生成 */
  const stop = () => {
    abortRef.current?.abort()
    setStreaming(false)
  }

  /** 新建对话：中止请求、删除该智能体的本地记录并清空消息 */
  const newChat = () => {
    abortRef.current?.abort()
    localStorage.removeItem(storageKey(user, agent.id))
    setSessionId(null)
    setMessages([])
    setError('')
  }

  return (
    <div className="chat-page">
      <header className="chat-header">
        <button className="icon-button" onClick={onBack} aria-label="返回"><Back /></button>
        <span className="agent-avatar small">{agent.name.slice(0, 1)}</span>
        <div className="chat-title">
          <strong>{agent.name}</strong>
          <span>{agent.team} · {agent.model}</span>
        </div>
        <button className="ghost" onClick={newChat}>新对话</button>
      </header>

      <main className="messages" ref={listRef}>
        {messages.length === 0 && (
          <div className="welcome">
            <span className="agent-avatar large">{agent.name.slice(0, 1)}</span>
            <h2>{agent.name}</h2>
            <p>{agent.description}</p>
            <div className="suggestions">
              {['我想了解退款政策', '帮我查一下订单状态', '发票抬头写错了怎么办'].map((item) => (
                <button key={item} className="suggestion" onClick={() => setInput(item)}>{item}</button>
              ))}
            </div>
          </div>
        )}

        {messages.map((message) => (
          <article key={message.id} className={`bubble-row ${message.role}`}>
            {message.role === 'assistant' && <span className="mini-avatar">{agent.name.slice(0, 1)}</span>}
            <div className={`bubble ${message.role}`}>
              {message.content && <p>{message.content}</p>}
              {!!message.tools?.length && (
                <div className="tool-list">
                  {message.tools.map((tool, index) => (
                    <span key={`${tool.name}-${index}`} className={`tool ${tool.status}`}>
                      <span className="dot" />
                      {tool.name}
                      {tool.status === 'running' ? ' 调用中' : tool.status === 'error' ? ' 失败' : ' 完成'}
                    </span>
                  ))}
                </div>
              )}
              {message.pending && !message.content && (
                <span className="typing"><i /><i /><i /></span>
              )}
            </div>
          </article>
        ))}
      </main>

      {error && <div className="banner error chat-banner">{error}</div>}

      <footer className="composer">
        <div className="composer-box">
          <textarea
            value={input}
            placeholder={`向 ${agent.name} 提问…`}
            rows={1}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                send()
              }
            }}
          />
          {streaming ? (
            <button className="stop" onClick={stop}>停止</button>
          ) : (
            <button className="send" onClick={send} disabled={!input.trim()} aria-label="发送"><Send /></button>
          )}
        </div>
        <p className="composer-hint">Enter 发送 · Shift + Enter 换行</p>
      </footer>
    </div>
  )
}

/** 格式化工具参数为单行 JSON，空对象或序列化失败时返回空串 */
function formatArgs(args: unknown) {
  if (args == null) return ''
  try {
    const text = JSON.stringify(args)
    return text === '{}' ? '' : text.slice(0, 120)
  } catch {
    return ''
  }
}

/** 右箭头图标 */
function Arrow() {
  return <svg viewBox="0 0 24 24" className="icon" aria-hidden="true"><path d="M5 12h13M13 6l6 6-6 6" /></svg>
}
/** 返回箭头图标 */
function Back() {
  return <svg viewBox="0 0 24 24" className="icon" aria-hidden="true"><path d="M19 12H6M11 6l-6 6 6 6" /></svg>
}
/** 发送图标 */
function Send() {
  return <svg viewBox="0 0 24 24" className="icon" aria-hidden="true"><path d="M4 12l16-8-6 16-2.5-6.5L4 12z" /></svg>
}
