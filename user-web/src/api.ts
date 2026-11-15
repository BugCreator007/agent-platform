const BASE = '/api/v1'
const TOKEN_KEY = 'agent_user_token'
const USER_KEY = 'agent_user_profile'

export interface UserInfo {
  id: string
  username: string
  displayName: string
  roles: string[]
}

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

export interface BackendMessage {
  id: string
  sessionId: string
  role: string
  content: string
  tokens: number
  model: string
  contextCount: number
  createdAt: string
}

interface Envelope<T> {
  success: boolean
  code: string
  message: string
  data: T
}

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

export const api = {
  login: (username: string, password: string) =>
    request<{ accessToken: string; user: UserInfo }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
  agents: () => request<UserAgent[]>('/user/agents'),
  messages: (sessionId: string) => request<BackendMessage[]>(`/sessions/${sessionId}/messages`),
}

interface ToolEvent {
  name: string
  arguments?: unknown
  error?: boolean
  text?: string
}

export interface StreamHandlers {
  onMeta?: (meta: Record<string, unknown>) => void
  onDelta?: (text: string) => void
  onTool?: (tool: ToolEvent) => void
  onToolResult?: (result: ToolEvent) => void
  onDone?: (done: Record<string, unknown>) => void
  onError?: (message: string) => void
}

export interface ChatBody {
  agentId: string
  message: string
  sessionId?: string
}

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

export async function streamChat(body: ChatBody, handlers: StreamHandlers, signal?: AbortSignal) {
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
