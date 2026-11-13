import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { api, auth, type Alert, type Agent, type AuthUser, type ChatSession, type Provider, type Release, type Workflow } from './api'
import { Icon, IconSprite } from './components/icons'
import { StatusBadge, ToastHost } from './components/ui'
import { modules, type ModuleKey } from './modules'
import { LoginPage } from './pages/Login'
import {
  AgentsPage,
  AlertsPage,
  CostPage,
  EvalPage,
  KnowledgePage,
  ModelsPage,
  OverviewPage,
  ReleasePage,
  SessionsPage,
  DebugPage,
  SettingsPage,
  ToolsPage,
  WorkflowPage,
} from './pages'

const iconMap: Record<ModuleKey, string> = {
  overview: 'overview',
  agents: 'agents',
  sessions: 'sessions',
  debug: 'terminal',
  workflow: 'workflow',
  models: 'cpu',
  release: 'releases',
  eval: 'evals',
  tools: 'tools',
  knowledge: 'knowledge',
  alerts: 'alerts',
  cost: 'cost',
  settings: 'settings',
}

const pageMap: Record<ModuleKey, React.ComponentType> = {
  overview: OverviewPage,
  agents: AgentsPage,
  sessions: SessionsPage,
  debug: DebugPage,
  workflow: WorkflowPage,
  models: ModelsPage,
  release: ReleasePage,
  eval: EvalPage,
  tools: ToolsPage,
  knowledge: KnowledgePage,
  alerts: AlertsPage,
  cost: CostPage,
  settings: SettingsPage,
}

interface SystemInfo {
  success: boolean
  data?: { service: string; status: string }
}

async function fetchSystemInfo(): Promise<SystemInfo> {
  const response = await fetch('/api/v1/system/info')
  if (!response.ok) throw new Error('backend unavailable')
  return response.json()
}

interface NavStats {
  agents: number
  sessions: number
  workflows: number
  providers: number
  releases: number
  alerts: number
}

function useNavStats(): NavStats {
  const agents = useQuery({ queryKey: ['agents'], queryFn: () => api.get<Agent[]>('/agents') })
  const sessions = useQuery({ queryKey: ['sessions'], queryFn: () => api.get<ChatSession[]>('/sessions') })
  const workflows = useQuery({ queryKey: ['workflows'], queryFn: () => api.get<Workflow[]>('/workflows') })
  const providers = useQuery({ queryKey: ['providers'], queryFn: () => api.get<Provider[]>('/providers') })
  const releases = useQuery({ queryKey: ['releases'], queryFn: () => api.get<Release[]>('/releases') })
  const alerts = useQuery({ queryKey: ['alerts'], queryFn: () => api.get<Alert[]>('/alerts') })
  return {
    agents: agents.data?.length ?? 0,
    sessions: sessions.data?.filter((s) => s.status === 'running').length ?? 0,
    workflows: workflows.data?.length ?? 0,
    providers: providers.data?.length ?? 0,
    releases: releases.data?.filter((r) => r.stage === 'approve').length ?? 0,
    alerts: alerts.data?.filter((a) => a.status === 'active').length ?? 0,
  }
}

const ENVS = [
  { id: 'prod', name: '生产', region: '华东 1', detail: '真实流量 · 受保护' },
  { id: 'pre', name: '预发', region: '华东 1', detail: '发布前验证 · 独立数据' },
  { id: 'test', name: '测试', region: '华东 1', detail: '开发联调 · 可写' },
]

function Shell({ onLogout }: { onLogout: () => void }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { data: system, isError } = useQuery({ queryKey: ['system-info'], queryFn: fetchSystemInfo })
  const stats = useNavStats()
  const me = useQuery({ queryKey: ['me'], queryFn: () => api.get<AuthUser>('/auth/me') })
  const agentsQuery = useQuery({ queryKey: ['agents'], queryFn: () => api.get<Agent[]>('/agents') })
  const sessionsQuery = useQuery({ queryKey: ['sessions'], queryFn: () => api.get<ChatSession[]>('/sessions') })
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [paletteQuery, setPaletteQuery] = useState('')
  const [notifOpen, setNotifOpen] = useState(false)
  const [envOpen, setEnvOpen] = useState(false)
  const [env, setEnv] = useState(ENVS[0])
  const [tourOpen, setTourOpen] = useState(false)
  const [tourStep, setTourStep] = useState(0)

  const selected = modules.find((m) => location.pathname.startsWith(m.path))?.key ?? 'overview'
  const current = modules.find((m) => m.key === selected)!
  const groups = [
    { label: '工作台', items: modules.filter((m) => m.group === '工作台') },
    { label: '构建', items: modules.filter((m) => m.group === '构建') },
    { label: '运维', items: modules.filter((m) => m.group === '运维') },
  ]
  const badgeFor = (key: ModuleKey): string | null => {
    if (key === 'agents' && stats.agents) return String(stats.agents)
    if (key === 'sessions' && stats.sessions) return String(stats.sessions)
    if (key === 'workflow' && stats.workflows) return String(stats.workflows)
    if (key === 'models' && stats.providers) return String(stats.providers)
    if (key === 'release' && stats.releases) return String(stats.releases)
    if (key === 'alerts' && stats.alerts) return String(stats.alerts)
    return null
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setPaletteOpen((v) => !v)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const openModule = (key: ModuleKey) => {
    setPaletteOpen(false)
    setPaletteQuery('')
    navigate(modules.find((m) => m.key === key)?.path ?? '/overview')
  }

  const paletteItems = useMemo(() => {
    const q = paletteQuery.trim().toLowerCase()
    return modules.filter((m) => !q || `${m.label}${m.description}${m.key}`.toLowerCase().includes(q))
  }, [paletteQuery])
  const paletteAgents = useMemo(() => {
    const q = paletteQuery.trim().toLowerCase()
    if (!q) return []
    return (agentsQuery.data ?? []).filter((a) => `${a.name}${a.code}${a.owner}${a.team}`.toLowerCase().includes(q)).slice(0, 4)
  }, [agentsQuery.data, paletteQuery])
  const paletteSessions = useMemo(() => {
    const q = paletteQuery.trim().toLowerCase()
    if (!q) return []
    return (sessionsQuery.data ?? []).filter((s) => `${s.id}${s.agentName}${s.topic}`.toLowerCase().includes(q)).slice(0, 4)
  }, [sessionsQuery.data, paletteQuery])

  const tourSteps = [
    { view: 'overview' as ModuleKey, title: '运营概览', text: '实时脉搏、核心指标、智能体健康度、告警与待办发布都在这里汇总。' },
    { view: 'agents' as ModuleKey, title: '智能体', text: '创建智能体，打开详情修改提示词、绑定工具与知识库，并在沙盒中调试。' },
    { view: 'knowledge' as ModuleKey, title: '知识库', text: '数据集、文档、解析分块、Embedding、索引、检索参数和智能体授权全部可配置。' },
    { view: 'workflow' as ModuleKey, title: '流程编排', text: '从节点库拖拽节点，配置分支与审批，测试通过后发布新版本。' },
    { view: 'models' as ModuleKey, title: '模型厂商', text: '接入厂商、轮换密钥、调整额度，并配置默认模型与故障转移顺序。' },
    { view: 'release' as ModuleKey, title: '发布与版本', text: '评测门禁、审批、灰度放量和回滚组成完整发布流水线。' },
  ]

  return (
    <>
      <IconSprite />
      <div className="app">
        <aside className="rail" aria-label="主导航">
          <div className="brand">
            <span className="brand-mark" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                <circle cx="6" cy="6" r="2.2" />
                <circle cx="18" cy="6" r="2.2" />
                <circle cx="12" cy="18" r="2.2" />
                <path d="M7.8 7.8 10.5 16M16.2 7.8 13.5 16M8.2 6h7.6" />
              </svg>
            </span>
            <div>
              <div className="brand-name">Agent 平台</div>
              <div className="brand-sub">智核科技 · 管理后台</div>
            </div>
          </div>
          <button className="org-switch" onClick={() => setEnvOpen(true)}>
            <span className="dot" />
            <span>{env.name}环境 · {env.region}</span>
            <Icon name="chevron-down" />
          </button>
          <nav className="nav" aria-label="模块">
            {groups.map((group) => (
              <div key={group.label}>
                <div className="nav-group">{group.label}</div>
                {group.items.map((m) => {
                  const badge = badgeFor(m.key)
                  return (
                    <button
                      key={m.key}
                      className={`nav-item ${selected === m.key ? 'active' : ''}`}
                      onClick={() => openModule(m.key)}
                      title={m.label}
                    >
                      <Icon name={iconMap[m.key]} />
                      <span>{m.label}</span>
                      {badge && <b className={`nav-badge ${m.key === 'alerts' ? 'alert' : ''}`}>{badge}</b>}
                    </button>
                  )
                })}
              </div>
            ))}
          </nav>
          <div className="rail-foot">
            <div className="rail-health">
              <div className="h-title">
                <span className="pulse-dot" />
                {isError ? '后端连接异常' : '系统运行正常'}
              </div>
              <div className="h-row"><span>网关延迟</span><b>{isError ? '—' : '32 ms'}</b></div>
              <div className="h-row"><span>队列深度</span><b>0</b></div>
              <div className="h-row"><span>版本</span><b>2026.11.13</b></div>
            </div>
            <div className="rail-user">
              <div className="avatar dark">顾</div>
              <div className="who">顾先路<small>平台管理员</small></div>
              <Icon name="chevron-down" style={{ color: '#6E7B72', width: 14, height: 14 }} />
            </div>
          </div>
        </aside>

        <div className="stage">
          <header className="topbar">
            <div className="crumb">
              Agent 平台 <Icon name="chevron-right" /> <b>{current.label}</b>
            </div>
            <button className="search-trigger" onClick={() => setPaletteOpen(true)} aria-label="打开全局搜索">
              <Icon name="search" style={{ width: 14, height: 14 }} />
              <span>搜索页面、智能体、会话…</span>
              <span className="kbd">⌘ K</span>
            </button>
            <div className="topbar-right">
              <button className="env-pill" onClick={() => setEnvOpen(true)} title="切换环境">
                <span className="dot" />
                <span>{env.name}</span>
                <Icon name="chevron-down" />
              </button>
              <button className="icon-btn" onClick={() => { setTourStep(0); setTourOpen(true) }} aria-label="交互导览">
                <Icon name="help" />
              </button>
              <button className="icon-btn" onClick={() => setNotifOpen((v) => !v)} aria-label="通知">
                <Icon name="bell" />
                {stats.alerts > 0 && <span className="dot" />}
              </button>
              <button className="icon-btn" onClick={onLogout} aria-label="退出登录" title="退出登录">
                <Icon name="arrow-left" />
              </button>
              <div className="avatar sm" title={me.data?.displayName ?? '顾先路'}>{(me.data?.displayName ?? '顾').slice(0, 1)}</div>
            </div>
          </header>
          <main className="main">
            <div className="view">
              <Routes>
                <Route path="/" element={<Navigate to="/overview" replace />} />
                {modules.map((m) => {
                  const Page = pageMap[m.key]
                  return <Route key={m.key} path={m.path} element={<Page />} />
                })}
                <Route path="*" element={<Navigate to="/overview" replace />} />
              </Routes>
            </div>
          </main>
        </div>
      </div>

      {notifOpen && (
        <div className="popover" style={{ right: 92, top: 54 }}>
          <div className="popover-head">
            通知
            <button onClick={() => setNotifOpen(false)}>全部已读</button>
          </div>
          <div className="pop-item" onClick={() => { setNotifOpen(false); openModule('alerts') }}>
            <div className="alert-icon critical"><Icon name="alert-triangle" /></div>
            <div><b>工具错误率突增</b><small>risk.score · 11 分钟前</small></div>
          </div>
          <div className="pop-item" onClick={() => { setNotifOpen(false); openModule('release') }}>
            <div className="alert-icon info"><Icon name="releases" /></div>
            <div><b>v12 等待你审批</b><small>退款审核 Copilot · 35 分钟前</small></div>
          </div>
          <div className="pop-item" onClick={() => { setNotifOpen(false); openModule('agents') }}>
            <div className="alert-icon info"><Icon name="check-circle" /></div>
            <div><b>发票助手 v15 发布成功</b><small>客服平台 · 1 小时前</small></div>
          </div>
        </div>
      )}

      {paletteOpen && (
        <div className="modal-scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) setPaletteOpen(false) }}>
          <div className="palette-box">
            <div className="palette-input">
              <Icon name="search" />
              <input
                autoFocus
                value={paletteQuery}
                onChange={(e) => setPaletteQuery(e.target.value)}
                placeholder="搜索页面、智能体、会话…"
              />
              <span className="kbd">ESC</span>
            </div>
            <div className="palette-list">
              <div className="palette-group">页面</div>
              {paletteItems.map((m) => (
                <button key={m.key} className="palette-item" onClick={() => openModule(m.key)}>
                  <Icon name={iconMap[m.key]} />
                  <span>{m.label}</span>
                  <small>{m.group}</small>
                </button>
              ))}
              {paletteAgents.length > 0 && (
                <>
                  <div className="palette-group">智能体</div>
                  {paletteAgents.map((a) => (
                    <button key={a.id} className="palette-item" onClick={() => { setPaletteOpen(false); setPaletteQuery(''); navigate('/agents') }}>
                      <Icon name="agents" />
                      <span>{a.name}</span>
                      <small>{a.code} · {a.owner}</small>
                    </button>
                  ))}
                </>
              )}
              {paletteSessions.length > 0 && (
                <>
                  <div className="palette-group">会话</div>
                  {paletteSessions.map((s) => (
                    <button key={s.id} className="palette-item" onClick={() => { setPaletteOpen(false); setPaletteQuery(''); navigate('/sessions') }}>
                      <Icon name="sessions" />
                      <span className="mono" style={{ fontSize: 11.5 }}>{s.id}</span>
                      <small>{s.agentName} · {s.topic}</small>
                    </button>
                  ))}
                </>
              )}
              {!paletteItems.length && !paletteAgents.length && !paletteSessions.length && <div className="palette-empty">没有匹配的页面、智能体或会话</div>}
            </div>
          </div>
        </div>
      )}

      {envOpen && (
        <div className="modal-scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) setEnvOpen(false) }}>
          <div className="modal" style={{ width: 520 }}>
            <div className="modal-head">
              <div><div className="modal-title">切换环境</div><div className="modal-sub">不同环境使用独立的数据与密钥</div></div>
              <button className="icon-btn" onClick={() => setEnvOpen(false)}><Icon name="x" /></button>
            </div>
            <div className="modal-body">
              {ENVS.map((item) => (
                <button
                  key={item.id}
                  className="alert-item"
                  style={{ width: '100%', textAlign: 'left', borderColor: env.id === item.id ? 'var(--pine)' : undefined }}
                  onClick={() => { setEnv(item); setEnvOpen(false) }}
                >
                  <div className="alert-icon info"><Icon name="cpu" /></div>
                  <div style={{ minWidth: 0 }}>
                    <div className="al-title">{item.name}环境 · {item.region}</div>
                    <div className="al-meta">{item.detail}</div>
                  </div>
                  {env.id === item.id && <span className="badge good"><span className="bd" />当前</span>}
                </button>
              ))}
            </div>
            <div className="modal-foot"><span className="spacer" /><button className="btn primary" onClick={() => setEnvOpen(false)}>完成</button></div>
          </div>
        </div>
      )}

      {tourOpen && createTour(tourSteps, tourStep, setTourStep, setTourOpen, openModule)}

      <ToastHost />
    </>
  )
}

function createTour(
  steps: Array<{ view: ModuleKey; title: string; text: string }>,
  step: number,
  setStep: (n: number) => void,
  setOpen: (v: boolean) => void,
  openModule: (key: ModuleKey) => void,
) {
  const current = steps[step]
  return (
    <div className="modal-scrim" style={{ zIndex: 100 }}>
      <div className="tour-card" style={{ position: 'relative', left: 'auto', top: 'auto' }}>
        <div className="tc-head">
          <span className="tc-kicker">交互导览</span>
          <span className="tc-count">{step + 1} / {steps.length}</span>
        </div>
        <div className="tc-title">{current.title}</div>
        <div className="tc-body">{current.text}</div>
        <div className="tc-foot">
          <div className="stepdots">{steps.map((_, i) => <i key={i} className={i === step ? 'on' : ''} />)}</div>
          <button className="btn" onClick={() => { setOpen(false); setStep(0) }}>退出</button>
          {step > 0 && <button className="btn" onClick={() => setStep(step - 1)}>上一步</button>}
          <button
            className="btn primary"
            onClick={() => {
              if (step === steps.length - 1) {
                setOpen(false)
                setStep(0)
                return
              }
              openModule(steps[step + 1].view)
              setStep(step + 1)
            }}
          >
            {step === steps.length - 1 ? '完成' : '下一步'}
          </button>
        </div>
      </div>
    </div>
  )
}


export default function App() {
  const [authed, setAuthed] = useState(auth.isAuthenticated())

  useEffect(() => {
    const onExpired = () => setAuthed(false)
    window.addEventListener('auth-expired', onExpired)
    return () => window.removeEventListener('auth-expired', onExpired)
  }, [])

  if (!authed) return <LoginPage onLogin={() => setAuthed(true)} />
  return <Shell onLogout={() => { api.post('/auth/logout').catch(() => undefined).finally(() => { auth.clear(); setAuthed(false) }) }} />
}
