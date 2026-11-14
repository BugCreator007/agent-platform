const BASE = '/api/v1'
const TOKEN_KEY = 'agent_platform_token'

export const auth = {
  token: () => localStorage.getItem(TOKEN_KEY),
  setToken: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
  isAuthenticated: () => !!localStorage.getItem(TOKEN_KEY),
}

interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const isForm = init?.body instanceof FormData
  const token = auth.token()
  const response = await fetch(BASE + path, {
    headers: {
      ...(isForm ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers ?? {}),
    },
    ...init,
  })
  if (response.status === 401 && !path.startsWith('/auth/login')) {
    auth.clear()
    window.dispatchEvent(new CustomEvent('auth-expired'))
  }
  const body = (await response.json().catch(() => null)) as ApiEnvelope<T> | null
  if (!response.ok || !body || body.success === false) {
    throw new Error(body?.message || `请求失败：${response.status}`)
  }
  return body.data
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }),
  postForm: <T>(path: string, form: FormData) => request<T>(path, { method: 'POST', body: form }),
  putForm: <T>(path: string, form: FormData) => request<T>(path, { method: 'PUT', body: form }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}

export interface AuthUser { id: string; username: string; displayName: string; roles: string[] }

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: { id: string; username: string; displayName: string; roles: string[] }
}

export interface Agent {
  id: string
  name: string
  code: string
  status: string
  version: string
  model: string
  providerId: string | null
  modelId: string | null
  owner: string
  team: string
  release: string
  sessions: number
  success: number
  p95: string
  cost: number
  updated: string
  prompt: string
  tools: string[]
  kb: string[]
}

export interface ChatSession {
  id: string
  agentId: string
  agentName: string
  status: string
  turns: number
  tools: number
  tokens: number
  ms: number
  time: string
  topic: string
  trace: Array<{ kind: string; name: string; ms: number; tokens: number; status: string; detail: string }>
}

export interface Dataset {
  id: string
  name: string
  owner: string
  description: string
  embeddingModel: string
  indexType: string
  metric: string
  parser: string
  chunkStrategy: string
  chunkSize: number
  overlap: number
  retrievalMode: string
  topK: number
  threshold: number
  rerank: boolean
  needsReindex: boolean
  status: string
  docs: number
  chunks: number
  updated: string
  authAgents: string[]
}

export interface Document {
  id: string
  datasetId: string
  name: string
  version: number
  chunks: number
  size: string
  status: string
  updated: string
  sourceType?: string
  sourceUrl?: string | null
}

export interface RetrievalResult {
  query: string
  ms: number
  mode: string
  topK: number
  threshold: number
  hits: Array<{ source: string; chunk: number; score: number; text: string }>
}

export interface Provider {
  id: string
  name: string
  status: string
  endpoint: string
  protocol: string
  models: number
  calls: number
  share: number
  latency: string
  success: number
  quota: number
  used: number
  key: string
  keyConfigured: boolean
  region: string
  route: string
}

export interface ProviderTestResult {
  ok: boolean
  status: string
  latency: string
  message: string
}

export interface ModelInfo {
  id: string
  providerId: string
  name: string
  context: string
  inPrice: number
  outPrice: number
  capabilities: string[]
  calls: number
  latency: string
  status: string
  routeWeight: number
  isDefault: boolean
  upstreamModel: string
}

export interface Release {
  id: string
  agentId: string
  agentName: string
  version: string
  stage: string
  traffic: number
  evalScore: number | null
  owner: string
  time: string
  note: string
}

export interface EvalSuite {
  id: string
  name: string
  description: string
  rate: number
  cases: number
  last: string
}

export interface EvalRun {
  id: string
  suite: string
  agent: string
  version: string
  passed: string
  score: number
  duration: string
  time: string
  status: string
}

export interface McpServer {
  id: string
  name: string
  transport: string
  url: string | null
  command: string | null
  args: string | null
  authType: string | null
  enabled: boolean
  status: string
  lastError: string | null
  lastConnectedAt: string | null
  toolCount: number
}

export interface McpTool {
  id: string
  serverId: string
  toolKey: string
  name: string
  description: string
  inputSchema: string
  enabled: boolean
  riskLevel: string
}

export interface McpCallOutcome {
  error: boolean
  text: string
}

export interface Tool {
  name: string
  type: string
  owner: string
  calls: number
  errorRate: number
  health: string
  checked: string
  enabled: boolean
  description: string
}

export interface WorkflowNode {
  key: string
  type: string
  title: string
  meta: string
  config?: Record<string, unknown>
}

export interface Workflow {
  id: string
  name: string
  status: string
  trigger: string
  runs: number
  success: number
  owner: string
  version: string
  updated: string
  nodes: WorkflowNode[]
}

export interface SessionMessage {
  id: string
  sessionId: string
  role: 'user' | 'assistant' | 'tool' | 'system'
  content: string
  tokens: number
  model: string | null
  contextCount: number
  createdAt: string
}

export interface WorkflowInstance {
  id: string
  workflowId: string
  workflowName: string
  status: string
  triggerType: string
  inputJson: string
  outputJson: string | null
  currentNode: string | null
  startedAt: string
  finishedAt: string | null
  error: string | null
  resumeAt?: string | null
}

export interface WorkflowNodeRun {
  id: string
  instanceId: string
  nodeKey: string
  nodeType: string
  title: string
  status: string
  inputJson: string | null
  outputJson: string | null
  attempt: number
  startedAt: string
  finishedAt: string | null
  error: string | null
}

export interface AlertRule {
  id: string
  name: string
  metric: string
  level: string
  thresholdValue: number
  windowLabel: string
  channel: string
  enabled: boolean
}

export interface Alert {
  id: string
  severity: string
  title: string
  rule: string
  target: string
  time: string
  ago: string
  status: string
  metric: string
  detail: string
}

export interface AgentSessionStat {
  agentId: string
  agentName: string
  sessions: number
  success: number
  p95: string
  tools: number
}

export interface Overview {
  kpi: {
    todaySessions: string
    sessionsDelta: string
    successRate: string
    successDelta: string
    p95: string
    p95Delta: string
    cost: string
    costDelta: string
  }
  pulse: {
    runningSessions: number
    onlineAgents: number
    errorRate: string
    p99: string
    peakRpm: number
    throughput: number[]
    throughputLabels: string[]
  }
  series: {
    labels: string[]
    sessions: number[]
    tools: number[]
    success: number[]
    p95: number[]
    cost: number[]
  }
  snapshot: { generatedAt: string; rangeDays: number }
  agents: Agent[]
  alerts: Alert[]
  releases: Release[]
}

export interface CostSummary {
  total: number
  budget: number
  models: Array<{ name: string; value: number; share: number }>
  daily: Array<{ day: string; value: number }>
}

export interface Member {
  name: string
  email: string
  role: string
  agents: number
  last: string
}

export interface ApiKey {
  name: string
  key: string
  scope: string
  created: string
  last: string
  by: string
}

export interface AuditLog {
  who: string
  action: string
  object: string
  time: string
  ip: string
}
