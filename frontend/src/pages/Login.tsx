import { useState } from 'react'
import { api, auth, type LoginResponse } from '../api'
import { Icon } from '../components/icons'
import { Button, toast } from '../components/ui'

export function LoginPage({ onLogin }: { onLogin: () => void }) {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin123')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const result = await api.post<LoginResponse>('/auth/login', { username, password })
      auth.setToken(result.accessToken)
      onLogin()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setLoading(false)
    }
  }

  const useAccount = (u: string, p: string) => {
    setUsername(u)
    setPassword(p)
    setError('')
  }

  return (
    <div className="login-page">
      <div className="login-grid" aria-hidden="true" />
      <div className="login-shell">
        <div className="login-brand">
          <span className="brand-mark">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <circle cx="6" cy="6" r="2.2" /><circle cx="18" cy="6" r="2.2" /><circle cx="12" cy="18" r="2.2" />
              <path d="M7.8 7.8 10.5 16M16.2 7.8 13.5 16M8.2 6h7.6" />
            </svg>
          </span>
          <div><div className="brand-name">Agent 平台</div><div className="brand-sub">智核科技 · 管理后台</div></div>
        </div>
        <div className="login-card">
          <div className="login-kicker">平台控制台</div>
          <h1 className="login-title">登录管理后台</h1>
          <p className="login-sub">使用平台账号登录，或接入企业 OIDC 单点登录。</p>
          <form onSubmit={submit}>
            <div className="field">
              <label>用户名</label>
              <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" placeholder="admin" />
            </div>
            <div className="field">
              <label>密码</label>
              <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" placeholder="••••••••" />
            </div>
            {error && <div className="login-error"><Icon name="alert-triangle" />{error}</div>}
            <Button kind="primary" type="submit" className="login-submit" disabled={loading || !username || !password}>
              {loading ? '正在登录…' : '登录'} {!loading && <Icon name="arrow-right" />}
            </Button>
          </form>
          <div className="login-demo">
            <span>演示账号</span>
            <button type="button" onClick={() => useAccount('admin', 'admin123')}>管理员</button>
            <button type="button" onClick={() => useAccount('developer', 'developer123')}>开发者</button>
            <button type="button" onClick={() => useAccount('viewer', 'viewer123')}>只读</button>
          </div>
        </div>
        <div className="login-foot">
          <span><span className="pulse-dot" />API 网关正常</span>
          <span>生产环境 · 华东 1</span>
        </div>
      </div>
    </div>
  )
}
