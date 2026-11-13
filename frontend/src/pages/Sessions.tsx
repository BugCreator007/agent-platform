import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api, type ChatSession } from '../api'
import { Icon } from '../components/icons'
import {
  Avatar,
  Button,
  Drawer,
  PageHead,
  Panel,
  StatusBadge,
  toast,
} from '../components/ui'

export function SessionsPage() {
  const [status, setStatus] = useState('all')
  const [agent, setAgent] = useState('all')
  const [selected, setSelected] = useState<ChatSession | null>(null)
  const { data, isLoading } = useQuery({ queryKey: ['sessions'], queryFn: () => api.get<ChatSession[]>('/sessions') })

  const sessions = data ?? []
  const agents = useMemo(() => {
    const map = new Map<string, string>()
    sessions.forEach((s) => map.set(s.agentId, s.agentName))
    return Array.from(map.entries())
  }, [sessions])
  const filtered = sessions.filter((s) => (status === 'all' || s.status === status) && (agent === 'all' || s.agentId === agent))
  const failed = sessions.filter((s) => s.status === 'failed' || s.status === 'timeout').length
  const timed = sessions.filter((s) => s.ms && s.status !== 'timeout')
  const avg = timed.length ? Math.round(timed.reduce((sum, s) => sum + s.ms, 0) / timed.length) : 0

  const exportCsv = () => {
    const rows = [['会话ID', '智能体', '状态', '问题', '轮次', '工具', 'Tokens', '耗时', '时间']]
    filtered.forEach((s) => rows.push([s.id, s.agentName, s.status, s.topic, String(s.turns), String(s.tools), String(s.tokens), String(s.ms), s.time]))
    const url = URL.createObjectURL(new Blob([`﻿${rows.map((r) => r.join(',')).join('\n')}`], { type: 'text/csv;charset=utf-8' }))
    const a = document.createElement('a')
    a.href = url
    a.download = 'sessions.csv'
    a.click()
    URL.revokeObjectURL(url)
    toast('会话已导出')
  }

  return (
    <>
      <PageHead title="运行追踪" sub="逐条查看会话的模型决策、工具调用与耗时，定位失败原因并重放问题会话。">
        <Button onClick={exportCsv}><Icon name="download" />导出</Button>
        <Button onClick={() => toast('会话列表已刷新')}><Icon name="refresh" />刷新</Button>
      </PageHead>

      <div className="grid-3">
        <div className="panel stat"><div className="lb">近 24 小时会话</div><div className="vl">6,508</div><div className="delta"><span className="up">↑ 2.8%</span><span>较昨日</span></div></div>
        <div className="panel stat"><div className="lb">异常会话</div><div className="vl">{failed} <small>条</small></div><div className="delta"><span>失败 {sessions.filter((s) => s.status === 'failed').length} · 超时 {sessions.filter((s) => s.status === 'timeout').length}</span></div></div>
        <div className="panel stat"><div className="lb">平均端到端耗时</div><div className="vl">{(avg / 1000).toFixed(2)}<small>s</small></div><div className="delta"><span className="down">↓ 0.14s</span><span>较昨日</span></div></div>
      </div>

      <Panel
        className="mt-3"
        title="会话列表"
        sub="点击任意一行查看完整调用链"
        tools={
          <>
            <select className="select" value={agent} onChange={(e) => setAgent(e.target.value)} aria-label="按智能体筛选">
              <option value="all">全部智能体</option>
              {agents.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
            </select>
            <select className="select" value={status} onChange={(e) => setStatus(e.target.value)} aria-label="按状态筛选">
              <option value="all">全部状态</option>
              <option value="success">成功</option>
              <option value="failed">失败</option>
              <option value="timeout">超时</option>
              <option value="running">运行中</option>
            </select>
          </>
        }
        noBody
      >
        <div className="table-wrap">
          <table className="data">
            <thead><tr><th>会话 ID</th><th>智能体</th><th>状态</th><th className="wrap">用户问题</th><th className="num">轮次</th><th className="num">工具</th><th className="num">Tokens</th><th className="num">耗时</th><th className="num">时间</th></tr></thead>
            <tbody>
              {filtered.map((s, i) => (
                <tr key={s.id} className={`clickable ${s.status === 'failed' || s.status === 'timeout' ? 'row-danger' : ''}`} onClick={() => setSelected(s)}>
                  <td className="mono" style={{ color: 'var(--ink-2)' }}>{s.id}</td>
                  <td>
                    <div className="entity">
                      <Avatar text={s.agentName.slice(0, 1)} tone={['blue', 'amber', 'aqua', 'violet', 'orange'][i % 5]} size={24} />
                      <span style={{ fontSize: 12 }}>{s.agentName}</span>
                    </div>
                  </td>
                  <td><StatusBadge status={s.status} /></td>
                  <td className="wrap" style={{ maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis' }}>{s.topic}</td>
                  <td className="num">{s.turns}</td>
                  <td className="num">{s.tools}</td>
                  <td className="num">{s.tokens.toLocaleString()}</td>
                  <td className="num">{s.ms ? `${(s.ms / 1000).toFixed(2)}s` : <span className="badge info"><span className="bd" />进行中</span>}</td>
                  <td className="num muted">{s.time}</td>
                </tr>
              ))}
              {!filtered.length && <tr><td colSpan={9}><div className="empty"><Icon name="sessions" /><b>没有匹配的会话</b>调整筛选条件后重试。</div></td></tr>}
            </tbody>
          </table>
        </div>
        <div className="pager"><span>显示 {filtered.length} / {sessions.length} 条</span><span className="spacer" /><button className="btn" disabled><Icon name="chevron-left" /></button><span>第 1 页</span><button className="btn" disabled><Icon name="chevron-right" /></button></div>
      </Panel>

      <Drawer open={!!selected} onClose={() => setSelected(null)} width={760}>
        {selected && (
          <>
            <div className="drawer-head">
              <Avatar text={selected.agentName.slice(0, 1)} tone="blue" size={38} />
              <div className="dh-main">
                <div className="drawer-title"><span className="mono" style={{ fontSize: 15 }}>{selected.id}</span> <StatusBadge status={selected.status} /></div>
                <div className="drawer-sub"><span>{selected.agentName}</span><span>·</span><span>{selected.time}</span><span>·</span><span>{selected.ms ? `${(selected.ms / 1000).toFixed(2)}s` : '进行中'}</span></div>
              </div>
              <button className="icon-btn" onClick={() => setSelected(null)}><Icon name="x" /></button>
            </div>
            <div className="drawer-body">
              <div className="panel" style={{ boxShadow: 'none' }}>
                <div className="panel-head"><div><h2 className="panel-title">用户问题</h2></div></div>
                <div className="panel-body"><p style={{ margin: 0, fontSize: 13.5, lineHeight: 1.75 }}>{selected.topic}</p></div>
              </div>
              <div className="grid-4" style={{ margin: '14px 0' }}>
                <div className="panel stat" style={{ padding: 12 }}><div className="lb">轮次</div><div className="vl" style={{ fontSize: 22 }}>{selected.turns}</div></div>
                <div className="panel stat" style={{ padding: 12 }}><div className="lb">工具调用</div><div className="vl" style={{ fontSize: 22 }}>{selected.tools}</div></div>
                <div className="panel stat" style={{ padding: 12 }}><div className="lb">Tokens</div><div className="vl" style={{ fontSize: 22 }}>{selected.tokens.toLocaleString()}</div></div>
                <div className="panel stat" style={{ padding: 12 }}><div className="lb">耗时</div><div className="vl" style={{ fontSize: 22 }}>{selected.ms ? `${(selected.ms / 1000).toFixed(2)}s` : '—'}</div></div>
              </div>
              <div className="section-head"><h2>调用链</h2></div>
              <div className="panel" style={{ boxShadow: 'none' }}>
                <div className="panel-body" style={{ paddingTop: 6 }}>
                  {(selected.trace ?? []).map((step, i) => (
                    <div className="trace-row" key={i}>
                      <span className={`step-dot ${step.status === 'fail' ? 'fail' : step.status === 'skip' ? '' : 'ok'}`} style={{ background: step.status === 'fail' ? 'var(--danger-wash)' : 'var(--pine-wash)', color: step.status === 'fail' ? 'var(--danger-text)' : 'var(--pine-deep)' }}>
                        {step.status === 'fail' ? <Icon name="x" /> : <Icon name="check" />}
                      </span>
                      <div style={{ flex: 1 }}>
                        <div className="row"><b style={{ fontSize: 12.5 }}>{step.name}</b><span className="badge neutral" style={{ height: 18 }}>{step.kind}</span></div>
                        <div className="muted" style={{ fontSize: 11.5, marginTop: 2 }}>{step.detail}</div>
                      </div>
                      <span className="mono muted">{step.ms} ms</span>
                    </div>
                  ))}
                </div>
              </div>
              <div className="callout mt-3"><Icon name="spark" /><div>重放会用相同输入重新执行会话，并生成一条新的追踪记录，不会影响原始数据。</div></div>
            </div>
            <div className="drawer-foot">
              <Button onClick={() => toast('已复制会话 ID')}><Icon name="copy" />复制 ID</Button>
              <span className="spacer" />
              <Button onClick={() => toast('已加入问题样本库')}>加入样本库</Button>
              <Button kind="primary" onClick={() => toast('已开始重放，可在运行追踪中查看新会话')}><Icon name="play" />重放会话</Button>
            </div>
          </>
        )}
      </Drawer>
    </>
  )
}
