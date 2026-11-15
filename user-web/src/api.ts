/** API 根路径，开发环境由 Vite 代理到后端 */
const BASE = '/api/v1'
/** localStorage 中 access token 的键 */
const TOKEN_KEY = 'agent_user_token'
/** localStorage 中用户资料的键 */
const USER_KEY = 'agent_user_profile'

/** 当前登录用户信息 */
export interface UserInfo {
  id: string
  username: string
  displayName: string
  roles: string[]
}

/** 用户端可见的智能体摘要 */
export interface UserAgent {
  id: string
  name: string
  code: string
  team: string
  description: string
  model: string
  status: string
  tools: string[]
  knowledgeBases: number
}

/** 知识库文档引用 */
export interface Citation {
  documentId: string
  name: string
  url: string
  sourceType: 'WEB' | 'FILE' | string
}

/** 后端持久化的会话消息 */
export interface BackendMessage {
  id: string
  sessionId: string
  role: string
  content: string
  tokens: number
  model: string
  contextCount: number
  createdAt: string
  citations?: Citation[]
}

/** 后端统一响应包 */
interface Envelope<T> {
  success: boolean
  code: string
  message: string
  data: T
}

/**
 * 用户端登录态：token 与用户资料均保存在 localStorage。
 * 刷新页面后可恢复登录；退出或鉴权失败时一并清除。
 */
export const session = {
  token: () => localStorage.getItem(TOKEN_KEY),
  setToken: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  user: (): UserInfo | null => {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw) as UserInfo
    } catch {
      return null
    }
  },
  setUser: (user: UserInfo) => localStorage.setItem(USER_KEY, JSON.stringify(user)),
  clear: () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  },
}

/**
 * 通用 JSON 请求封装：
 * 自动附加 Bearer token；401 时清除本地登录态；
 * 解析统一响应包，失败抛出带后端 message 的错误。
 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = session.token()
  const response = await fetch(BASE + path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers ?? {}),
    },
  })
  const body = (await response.json().catch(() => null)) as Envelope<T> | null
  if (!response.ok || !body || body.success === false) {
    if (response.status === 401) session.clear()
    throw new Error(body?.message || `请求失败（${response.status}）`)
  }
  return body.data
}

/** 用户端接口集合：登录、智能体目录、会话历史 */
export const api = {
  login: (username: string, password: string) =>
    request<{ accessToken: string; user: UserInfo }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
  agents: () => request<UserAgent[]>('/user/agents'),
  messages: (sessionId: string) => request<BackendMessage[]>(`/sessions/${sessionId}/messages`),
}

/** SSE 工具事件：调用开始或返回时携带的信息 */
interface ToolEvent {
  name: string
  arguments?: unknown
  error?: boolean
  text?: string
}

/** SSE 流式对话的回调集合，各事件均为可选 */
export interface StreamHandlers {
  onMeta?: (meta: Record<string, unknown>) => void
  onDelta?: (text: string) => void
  onTool?: (tool: ToolEvent) => void
  onToolResult?: (result: ToolEvent) => void
  onDone?: (done: Record<string, unknown>) => void
  onError?: (message: string) => void
}

/** 流式对话请求体 */
export interface ChatBody {
  agentId: string
  message: string
  sessionId?: string
}

/**
 * 解析单个 SSE 事件块。
 * 支持 event: 与多行 data: 字段；无数据且无事件名时返回 null。
 */
function parseBlock(raw: string): { event: string; data: string } | null {
  const lines = raw.replace(/\r/g, '').split('\n')
  let event = 'message'
  const data: string[] = []
  for (const line of lines) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
  }
  if (!data.length && event === 'message') return null
  return { event, data: data.join('\n') }
}

/**
 * 将解析后的事件分发给对应回调。
 * meta/delta/tool/tool-result/done/error 六类事件，JSON 解析失败时回退为空对象。
 */
function dispatch(block: { event: string; data: string }, handlers: StreamHandlers) {
  const json = (): Record<string, unknown> => {
    try {
      return JSON.parse(block.data) as Record<string, unknown>
    } catch {
      return {}
    }
  }
  switch (block.event) {
    case 'meta':
      handlers.onMeta?.(json())
      break
    case 'delta':
      handlers.onDelta?.(block.data)
      break
    case 'tool':
      handlers.onTool?.(json() as unknown as ToolEvent)
      break
    case 'tool-result':
      handlers.onToolResult?.(json() as unknown as ToolEvent)
      break
    case 'done':
      handlers.onDone?.(json())
      break
    case 'error': {
      const payload = json()
      handlers.onError?.(String(payload.message ?? '对话失败，请稍后重试'))
      break
    }
    default:
      break
  }
}

/**
 * 下载文件类引用：浏览器直接打开链接不会带 Bearer Token，
 * 因此用 fetch 携带登录态获取 Blob，再触发临时下载。
 *
 * @param citation 文件引用
 */
export async function downloadCitation(citation: Citation) {
  const response = await fetch(citation.url, {
    headers: session.token() ? { Authorization: `Bearer ${session.token()}` } : {},
  })
  if (!response.ok) throw new Error(`文档下载失败（${response.status}）`)
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = citation.name || 'document'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

/**
 * 发起 SSE 流式对话并逐块解析。
 * 以空行（\n\n）切分事件块，处理跨 chunk 的半块数据；
 * 请求失败时尝试从错误响应体提取后端 message，最后冲刷 buffer 中的尾部事件。
 * @param body 对话参数（agentId、message、可选 sessionId）
 * @param handlers 各 SSE 事件回调
 * @param signal 用于取消请求的 AbortSignal
 */
export async function streamChat(body: ChatBody, handlers: StreamHandlers, signal?: AbortSignal) {
  // 手动附加鉴权头，Accept 声明期望 SSE 响应
  const response = await fetch(BASE + '/user/chat:stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(session.token() ? { Authorization: `Bearer ${session.token()}` } : {}),
    },
    body: JSON.stringify(body),
    signal,
  })
  if (!response.ok || !response.body) {
    const text = await response.text().catch(() => '')
    let message = `请求失败（${response.status}）`
    try {
      const parsed = JSON.parse(text) as { message?: string }
      if (parsed.message) message = parsed.message
    } catch {
      /* 非 JSON 错误体 */
    }
    throw new Error(message)
  }

  // 流式读取：每次拼接解码结果，循环取出完整的 \n\n 分隔事件块
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let index = buffer.indexOf('\n\n')
    while (index >= 0) {
      const block = parseBlock(buffer.slice(0, index))
      buffer = buffer.slice(index + 2)
      if (block) dispatch(block, handlers)
      index = buffer.indexOf('\n\n')
    }
  }
  const tail = parseBlock(buffer)
  if (tail) dispatch(tail, handlers)
}
