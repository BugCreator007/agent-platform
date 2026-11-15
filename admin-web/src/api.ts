/** API 根路径，开发环境由 Vite 代理到后端 */
const BASE = '/api/v1'
/** localStorage 中 access token 的存储键 */
const TOKEN_KEY = 'agent_platform_token'

/**
 * 登录态工具：token 读写、清除与登录判断。
 * token 保存在 localStorage，页面刷新后仍可恢复会话。
 */
export const auth = {
  /** 读取当前 access token */
  token: () => localStorage.getItem(TOKEN_KEY),
  /** 登录成功后写入 token */
  setToken: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  /** 退出或鉴权失败时清除本地 token */
  clear: () => localStorage.removeItem(TOKEN_KEY),
  /** 是否已持有 token（仅本地判断，真实有效性由后端校验） */
  isAuthenticated: () => !!localStorage.getItem(TOKEN_KEY),
}

/** 后端统一响应包：success=false 或非 2xx 均视为失败 */
interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
}

/**
 * 通用请求封装。
 * - 自动附加 Bearer token（FormData 请求不设置 Content-Type，交由浏览器生成边界）
 * - 收到 401（登录接口除外）时清除 token 并派发 `auth-expired` 事件，由 App 退回登录页
 * - 解析统一响应包，失败时抛出带后端 message 的错误
 */
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

/** 轻量 HTTP 客户端：按方法封装 JSON 与 FormData 请求 */
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

/** 当前登录用户信息 */
export interface AuthUser { id: string; username: string; displayName: string; roles: string[] }

/** 登录接口返回：access token 及用户资料 */
export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: { id: string; username: string; displayName: string; roles: string[] }
}

/** 智能体聚合信息：基础配置、模型绑定、运行指标与提示词 */
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

/** 聊天会话摘要，trace 为本次会话的执行轨迹 */
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

/** 知识库数据集：索引、分块、检索参数与授权智能体 */
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

/** 数据集中的文档记录（上传文件或网页来源） */
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

/** 检索调试结果：命中分块及相似度分数 */
export interface RetrievalResult {
  query: string
  ms: number
  mode: string
  topK: number
  threshold: number
  hits: Array<{ source: string; chunk: number; score: number; text: string }>
}

/** 模型厂商配置：接入信息、额度、密钥状态与路由权重 */
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

/** 厂商连通性测试结果 */
export interface ProviderTestResult {
  ok: boolean
  status: string
  latency: string
  message: string
}

/** 厂商下的模型条目：定价、能力标签与路由状态 */
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

/** 发布记录：所处阶段（如 eagate/approve/canary）、流量比例与评测分 */
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

/** 评测套件：通过率、用例数与最近运行时间 */
export interface EvalSuite {
  id: string
  name: string
  description: string
  rate: number
  cases: number
  last: string
}

/** 单次评测运行结果 */
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

/** MCP 服务连接配置（Streamable HTTP / SSE / stdio） */
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

/** MCP 服务暴露的工具目录项 */
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

/** MCP 工具调用结果 */
export interface McpCallOutcome {
  error: boolean
  text: string
}

/** 工具目录中的工具及其调用指标与健康状态 */
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

/** 工作流图节点定义 */
export interface WorkflowNode {
  key: string
  type: string
  title: string
  meta: string
  config?: Record<string, unknown>
}

/** 工作流定义：触发方式、版本、运行统计与节点列表 */
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

/** 会话中的单条消息（用户/助手/工具/系统） */
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

/** 工作流运行实例，resumeAt 表示审批/定时恢复时间 */
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

/** 实例中单个节点的执行记录 */
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

/** 告警规则：指标、阈值、窗口与通知渠道 */
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

/** 已触发的告警事件 */
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

/** 按智能体聚合的会话统计 */
export interface AgentSessionStat {
  agentId: string
  agentName: string
  sessions: number
  success: number
  p95: string
  tools: number
}

/** 运营概览页数据：KPI、实时脉搏、趋势序列与快照 */
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

/** 成本汇总：总额度、按模型分布与每日花费 */
export interface CostSummary {
  total: number
  budget: number
  models: Array<{ name: string; value: number; share: number }>
  daily: Array<{ day: string; value: number }>
}

/** 团队成员 */
export interface Member {
  name: string
  email: string
  role: string
  agents: number
  last: string
}

/** 平台 API Key（仅展示脱敏信息） */
export interface ApiKey {
  name: string
  key: string
  scope: string
  created: string
  last: string
  by: string
}

/** 审计日志条目 */
export interface AuditLog {
  who: string
  action: string
  object: string
  time: string
  ip: string
}
