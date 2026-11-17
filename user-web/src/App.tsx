import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import {
  api,
  downloadCitation,
  session,
  streamChat,
  type Citation,
  type Feedback,
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
  citations?: Citation[]
  /** 后端持久化的助手消息 ID；用于提交反馈，用户消息为空 */
  serverId?: string
  /** 助手回答类型：正常回答或严格模式拒答 */
  answerType?: 'ANSWER' | 'REFUSAL'
  /** 是否低置信（知识库未命中） */
  lowConfidence?: boolean
  /** 当前用户对这条回答的反馈 */
  feedback?: Feedback | null
}

/** 点踩原因选项：编码与后端 FeedbackReason 保持一致，文案只用于展示 */
const DOWN_REASONS = [
  { code: 'NOT_RELEVANT', label: '答非所问' },
  { code: 'INCOMPLETE', label: '不完整' },
  { code: 'OUTDATED', label: '信息过时' },
  { code: 'WRONG_SOURCE', label: '依据不对' },
  { code: 'NO_SOURCE', label: '没有依据' },
  { code: 'OTHER', label: '其他' },
]
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
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const historyLoaded = useRef(false)
  const initialSessionId = useRef(stored.sessionId)
  // 反馈交互状态：当前展开原因面板的消息、原因选择、备注与提交状态
  const [feedbackTarget, setFeedbackTarget] = useState<string | null>(null)
  const [reasonDraft, setReasonDraft] = useState<string[]>([])
  const [commentDraft, setCommentDraft] = useState('')
  const [followUpDraft, setFollowUpDraft] = useState(false)
  const [feedbackBusy, setFeedbackBusy] = useState(false)
  const [feedbackError, setFeedbackError] = useState('')

  // 进入聊天页上报一次 agent_opened，用于漏斗第一层；失败静默，不影响聊天
  useEffect(() => {
    void api.trackEvent({ name: 'agent_opened', agentId: agent.id }).catch(() => {})
  }, [agent.id])

  // 若存在历史会话，仅首次进入时从后端拉取消息与当前用户的反馈；失败则保留本地记录
  useEffect(() => {
    const sid = initialSessionId.current
    if (!sid || historyLoaded.current) return
    historyLoaded.current = true
    let cancelled = false
    api
      .messages(sid)
      .then(async (list) => {
        if (cancelled) return
        const restored: ChatMessage[] = list
          .filter((m) => m.role === 'user' || m.role === 'assistant')
          .map((m) => ({
            id: m.id,
            role: m.role as 'user' | 'assistant',
            content: m.content,
            citations: m.citations,
            serverId: m.id,
            answerType: m.answerType === 'REFUSAL' ? 'REFUSAL' : 'ANSWER',
            lowConfidence: !!m.lowConfidence,
          }))
        if (restored.length) setMessages(restored)
        // 反馈状态按当前用户单独读取，避免把别人的点赞状态带出来
        try {
          const feedbacks = await api.sessionFeedback(sid)
          if (cancelled) return
          setMessages((prev) => prev.map((message) => {
            const feedback = feedbacks.find((f) => f.messageId === message.serverId)
            return feedback ? { ...message, feedback } : message
          }))
        } catch {
          /* 反馈状态读取失败不影响历史消息展示 */
        }
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

  /** 将后端返回的反馈写回对应消息，null 表示撤销 */
  const applyFeedback = (serverId: string, feedback: Feedback | null) => {
    setMessages((prev) => prev.map((message) => (message.serverId === serverId ? { ...message, feedback } : message)))
  }

  /** 关闭原因面板并清空草稿 */
  const closeFeedbackPanel = () => {
    setFeedbackTarget(null)
    setReasonDraft([])
    setCommentDraft('')
    setFollowUpDraft(false)
    setFeedbackError('')
  }

  /**
   * 打开点踩原因面板。
   * @param message  助手消息
   * @param followUp 是否从“留下问题”进入（固定带 NO_SOURCE 原因）
   */
  const openDownPanel = (message: ChatMessage, followUp = false) => {
    if (!message.serverId) return
    const existing = message.feedback?.rating === 'DOWN' ? message.feedback : null
    setFeedbackTarget(message.serverId)
    setReasonDraft(existing ? existing.reasons.map((r) => r.code) : followUp ? ['NO_SOURCE'] : [])
    setCommentDraft(existing?.comment ?? '')
    setFollowUpDraft(followUp || !!existing?.followUpRequested)
    setFeedbackError('')
  }

  /** 提交反馈；成功后更新消息上的反馈状态 */
  const doSubmitFeedback = async (
    message: ChatMessage,
    rating: 'UP' | 'DOWN',
    reasons: string[],
    comment: string,
    followUp: boolean,
  ) => {
    if (!message.serverId) return
    setFeedbackBusy(true)
    setFeedbackError('')
    try {
      const feedback = await api.submitFeedback(message.serverId, {
        rating,
        reasons,
        comment: comment.trim() || undefined,
        followUpRequested: followUp,
      })
      applyFeedback(message.serverId, feedback)
      closeFeedbackPanel()
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      setFeedbackError(message)
      // 点赞失败时原因面板可能未展开，同时用全局提示兜底
      setError(message)
    } finally {
      setFeedbackBusy(false)
    }
  }

  /** 撤销当前用户对这条回答的反馈 */
  const doRemoveFeedback = async (message: ChatMessage) => {
    if (!message.serverId) return
    setFeedbackBusy(true)
    setFeedbackError('')
    try {
      await api.deleteFeedback(message.serverId)
      applyFeedback(message.serverId, null)
      closeFeedbackPanel()
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      setFeedbackError(message)
      setError(message)
    } finally {
      setFeedbackBusy(false)
    }
  }

  /** 点赞：已点赞则撤销，否则立即提交 */
  const toggleUp = (message: ChatMessage) => {
    if (message.feedback?.rating === 'UP') {
      void doRemoveFeedback(message)
    } else {
      void doSubmitFeedback(message, 'UP', [], '', false)
    }
  }

  /** 复制回答；成功后上报采纳事件，失败时提示手动选择文本 */
  const copyAnswer = async (message: ChatMessage) => {
    if (!message.content) return
    try {
      await navigator.clipboard.writeText(message.content)
      if (message.serverId) {
        void api.trackEvent({
          name: 'answer_copied',
          sessionId: sessionId ?? undefined,
          messageId: message.serverId,
          agentId: agent.id,
          properties: { length: message.content.length },
        }).catch(() => {})
      }
    } catch {
      setError('复制失败，请手动选择文本')
    }
  }

  /** 引用打开/下载成功后上报 citation_downloaded，用于采纳口径 */
  const trackCitation = (citation: Citation, message: ChatMessage) => {
    if (!message.serverId) return
    void api.trackEvent({
      name: 'citation_downloaded',
      sessionId: sessionId ?? undefined,
      messageId: message.serverId,
      agentId: agent.id,
      properties: { documentId: citation.documentId, sourceType: citation.sourceType },
    }).catch(() => {})
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
    // 拒答场景没有 delta，用 done 事件判断本轮已正常结束，避免误报“没有收到回复”
    let settledByDone = false
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
          onDone: (done) => {
            // 后端在 done 事件中返回引用、消息 ID、回答类型与置信标记
            settledByDone = true
            const citations = Array.isArray(done.citations) ? done.citations as Citation[] : []
            const serverId = typeof done.assistantMessageId === 'string' ? done.assistantMessageId : undefined
            const answerType = done.answerType === 'REFUSAL' ? 'REFUSAL' : 'ANSWER'
            const lowConfidence = done.lowConfidence === true
            const refusalContent = answerType === 'REFUSAL' && typeof done.content === 'string' ? done.content : undefined
            updateAssistant(assistantId, (m) => ({
              ...m,
              citations,
              serverId,
              answerType,
              lowConfidence,
              // 拒答没有流式增量，用 done 中的固定文案填充气泡
              content: refusalContent ?? m.content,
            }))
          },
          onError: (message) => {
            setError(message)
            updateAssistant(assistantId, (m) => ({ ...m, pending: false }))
          },
        },
        controller.signal,
      )
      // 流结束但既没有增量文本、也没有收到 done，才提示重试
      if (!received && !settledByDone) setError('没有收到回复，请稍后重试')
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
    closeFeedbackPanel()
    setFeedbackBusy(false)
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
              {message.role === 'assistant' && message.lowConfidence && !message.pending && (
                <div className="low-confidence">未检索到相关资料，回答可能不准确</div>
              )}
              {message.role === 'assistant' && message.answerType === 'REFUSAL' && !message.pending ? (
                <div className="refusal-card">
                  <strong>暂时没有找到答案</strong>
                  <p>{message.content}</p>
                  <div className="refusal-actions">
                    <button className="refusal-primary" onClick={() => openDownPanel(message, true)}>留下问题</button>
                    <button className="refusal-ghost" onClick={() => inputRef.current?.focus()}>换个问法</button>
                    {message.feedback?.followUpRequested && <span className="refusal-recorded">已记录</span>}
                  </div>
                </div>
              ) : (
                message.content && <p>{message.content}</p>
              )}
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
              {!!message.citations?.length && (
                <div className="citations">
                  <div className="citations-title">参考资料</div>
                  {message.citations.map((citation) => (
                    citation.sourceType === 'WEB' ? (
                      <a key={citation.documentId} className="citation-link" href={citation.url} target="_blank" rel="noreferrer" onClick={() => trackCitation(citation, message)}>
                        {citation.name}
                      </a>
                    ) : (
                      <button key={citation.documentId} className="citation-link" onClick={() => downloadCitation(citation).then(() => trackCitation(citation, message)).catch((e: Error) => setError(e.message))}>
                        {citation.name}
                      </button>
                    )
                  ))}
                </div>
              )}
              {message.role === 'assistant' && !message.pending && message.serverId && message.answerType !== 'REFUSAL' && (
                <div className="feedback-bar">
                  <button
                    className={`fb-btn ${message.feedback?.rating === 'UP' ? 'active' : ''}`}
                    onClick={() => toggleUp(message)}
                    disabled={feedbackBusy}
                    aria-label="这条回答有用"
                  >👍 有用</button>
                  <button
                    className={`fb-btn ${message.feedback?.rating === 'DOWN' ? 'active' : ''}`}
                    onClick={() => openDownPanel(message)}
                    disabled={feedbackBusy}
                    aria-label="这条回答没用"
                  >👎 没用</button>
                  {message.feedback?.rating === 'DOWN' && <span className="fb-note">已反馈</span>}
                  <button
                    className="fb-btn"
                    onClick={() => void copyAnswer(message)}
                    disabled={feedbackBusy}
                    aria-label="复制这条回答"
                  >复制</button>
                </div>
              )}
              {feedbackTarget === message.serverId && (
                <div className="feedback-panel">
                  <div className="feedback-panel-title">
                    {message.answerType === 'REFUSAL' ? '把问题留下，知识运营会补充资料' : '哪里没帮到你？'}
                  </div>
                  {message.answerType !== 'REFUSAL' && (
                    <div className="reason-chips">
                      {DOWN_REASONS.map((reason) => (
                        <button
                          key={reason.code}
                          className={`reason-chip ${reasonDraft.includes(reason.code) ? 'active' : ''}`}
                          onClick={() => setReasonDraft((prev) => prev.includes(reason.code)
                            ? prev.filter((code) => code !== reason.code)
                            : prev.length >= 2 ? prev : [...prev, reason.code])}
                        >{reason.label}</button>
                      ))}
                    </div>
                  )}
                  <textarea
                    className="fb-comment"
                    rows={2}
                    maxLength={200}
                    value={commentDraft}
                    onChange={(e) => setCommentDraft(e.target.value)}
                    placeholder={message.answerType === 'REFUSAL' ? '补充说明（选填）' : '补充说明（选填，200 字以内）'}
                  />
                  {feedbackError && <div className="fb-error">{feedbackError}</div>}
                  <div className="fb-actions">
                    {message.feedback?.rating === 'DOWN' && (
                      <button className="fb-link danger" onClick={() => doRemoveFeedback(message)} disabled={feedbackBusy}>撤销反馈</button>
                    )}
                    <span className="spacer" />
                    <button className="fb-link" onClick={closeFeedbackPanel} disabled={feedbackBusy}>取消</button>
                    <button
                      className="fb-submit"
                      disabled={feedbackBusy || (message.answerType !== 'REFUSAL' && reasonDraft.length === 0)}
                      onClick={() => doSubmitFeedback(
                        message,
                        'DOWN',
                        message.answerType === 'REFUSAL' ? ['NO_SOURCE'] : reasonDraft,
                        commentDraft,
                        followUpDraft || message.answerType === 'REFUSAL',
                      )}
                    >{feedbackBusy ? '提交中…' : '提交'}</button>
                  </div>
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
            ref={inputRef}
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
