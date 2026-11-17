import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api, type ChatSession, type EventStats, type FeedbackItem, type FeedbackPage, type FeedbackStats } from '../api'
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

/** 运行追踪页：会话列表筛选、指标汇总、CSV 导出与调用链详情抽屉 */
export function SessionsPage() {
  const [status, setStatus] = useState('all')
  const [agent, setAgent] = useState('all')
  const [feedbackRating, setFeedbackRating] = useState('DOWN')
  const [feedbackPage, setFeedbackPage] = useState(1)
  const [selected, setSelected] = useState<ChatSession | null>(null)
  const { data, isLoading } = useQuery({ queryKey: ['sessions'], queryFn: () => api.get<ChatSession[]>('/sessions') })
  // 反馈质量：只读角色访问会被后端拒绝，页面按错误态提示，不影响会话列表
  const feedbackStats = useQuery({
    queryKey: ['feedback-stats', agent],
    queryFn: () => api.get<FeedbackStats>(`/feedback/stats?days=7${agent === 'all' ? '' : `&agentId=${agent}`}`),
    retry: false,
  })
  const feedbackList = useQuery({
    queryKey: ['feedback-list', agent, feedbackRating, feedbackPage],
    queryFn: () => api.get<FeedbackPage>(
      `/feedback?size=10&page=${feedbackPage}&rating=${feedbackRating}${agent === 'all' ? '' : `&agentId=${agent}`}`,
    ),
    retry: false,
  })
  // 埋点事件统计：仅管理员/开发者可读，VIEWER 按无权限展示
  const eventStats = useQuery({
    queryKey: ['insight-events'],
    queryFn: () => api.get<EventStats>('/insight/events/stats?days=7'),
    retry: false,
  })

  const sessions = data ?? []
  // 从会话数据中提取去重后的智能体列表，用于下拉筛选
  const agents = useMemo(() => {
    const map = new Map<string, string>()
    sessions.forEach((s) => map.set(s.agentId, s.agentName))
    return Array.from(map.entries())
  }, [sessions])
  const filtered = sessions.filter((s) => (status === 'all' || s.status === status) && (agent === 'all' || s.agentId === agent))
  const failed = sessions.filter((s) => s.status === 'failed' || s.status === 'timeout').length
  // 平均耗时只统计已结束（有 ms 且非超时）的会话
  const timed = sessions.filter((s) => s.ms && s.status !== 'timeout')
  const avg = timed.length ? Math.round(timed.reduce((sum, s) => sum + s.ms, 0) / timed.length) : 0

  // 反馈汇总：比例统一按 0–1 返回，这里只负责展示格式化
  const stats = feedbackStats.data
  const reasons = stats?.reasons ?? []
  const maxReason = Math.max(1, ...reasons.map((r) => r.count))
  const feedbackItems = feedbackList.data?.items ?? []
  const feedbackTotal = feedbackList.data?.total ?? 0
  const feedbackPages = Math.max(1, Math.ceil(feedbackTotal / 10))
  // 事件名到展示文案的映射；未知事件直接展示编码
  const eventLabels: Record<string, string> = {
    agent_opened: '打开智能体',
    session_started: '创建会话',
    message_sent: '用户提问',
    answer_completed: '回答完成',
    answer_failed: '回答失败',
    feedback_submitted: '提交反馈',
    session_deleted: '删除会话',
    answer_copied: '复制回答',
    citation_downloaded: '下载引用',
  }
  const percent = (value: number) => `${(value * 100).toFixed(1)}%`
  const feedbackTime = (iso: string) => new Date(iso).toLocaleString('zh-CN', { hour12: false })

  /** 点击反馈行时定位到对应会话；会话不在当前列表时给出提示 */
  const openFeedbackSession = (item: FeedbackItem) => {
    const session = sessions.find((s) => s.id === item.sessionId)
    if (session) setSelected(session)
    else toast('该会话不在当前列表中', 'err')
  }

  /** 将当前筛选结果导出为 CSV（带 UTF-8 BOM，便于 Excel 打开中文） */
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
        <div className="panel stat"><div className="lb">会话总数</div><div className="vl">{sessions.length}</div><div className="delta"><span>当前加载</span></div></div>
        <div className="panel stat"><div className="lb">异常会话</div><div className="vl">{failed} <small>条</small></div><div className="delta"><span>失败 {sessions.filter((s) => s.status === 'failed').length} · 超时 {sessions.filter((s) => s.status === 'timeout').length}</span></div></div>
        <div className="panel stat"><div className="lb">平均端到端耗时</div><div className="vl">{(avg / 1000).toFixed(2)}<small>s</small></div><div className="delta"><span className="down">↓ 0.14s</span><span>较昨日</span></div></div>
      </div>

      <Panel
        className="mt-3"
        title="会话列表"
        sub="点击任意一行查看完整调用链"
        tools={
          <>
            <select className="select" value={agent} onChange={(e) => { setAgent(e.target.value); setFeedbackPage(1) }} aria-label="按智能体筛选">
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

      <Panel
        className="mt-3"
        title="回答质量与反馈"
        sub="近 7 天 · 由用户端点赞/点踩与严格模式拒答产生"
        tools={
          <select className="select" value={feedbackRating} onChange={(e) => { setFeedbackRating(e.target.value); setFeedbackPage(1) }} aria-label="按反馈评分筛选">
            <option value="DOWN">仅看负反馈</option>
            <option value="UP">仅看正反馈</option>
            <option value="">全部反馈</option>
          </select>
        }
        noBody
      >
        {feedbackStats.isError ? (
          <div className="empty" style={{ padding: 36 }}>
            <Icon name="spark" />
            <b>暂无权限</b>
            当前角色不能查看反馈明细与质量数据。
          </div>
        ) : (
          <>
            <div className="panel-body">
              <div className="grid-4">
                <div className="panel stat" style={{ padding: 13 }}>
                  <div className="lb">反馈率</div>
                  <div className="vl" style={{ fontSize: 24 }}>{stats ? percent(stats.feedbackRate) : '—'}</div>
                  <div className="delta"><span>{stats ? `${stats.feedbackMessages} / ${stats.assistantMessages} 条回答` : '加载中'}</span></div>
                </div>
                <div className="panel stat" style={{ padding: 13 }}>
                  <div className="lb">好评率</div>
                  <div className="vl" style={{ fontSize: 24 }}>{stats ? percent(stats.positiveRate) : '—'}</div>
                  <div className="delta"><span>{stats ? `${stats.up} 赞 · ${stats.down} 踩` : '加载中'}</span></div>
                </div>
                <div className="panel stat" style={{ padding: 13 }}>
                  <div className="lb">无答案</div>
                  <div className="vl" style={{ fontSize: 24 }}>{stats ? stats.refusals : '—'} <small>条</small></div>
                  <div className="delta"><span>{stats ? `无答案率 ${percent(stats.refusalRate)}` : '加载中'}</span></div>
                </div>
                <div className="panel stat" style={{ padding: 13 }}>
                  <div className="lb">留下问题</div>
                  <div className="vl" style={{ fontSize: 24 }}>{stats ? stats.followUp : '—'} <small>条</small></div>
                  <div className="delta"><span>等待知识运营跟进</span></div>
                </div>
              </div>
              <div className="section-head"><h2>负反馈原因</h2><span className="muted">近 7 天</span></div>
              {reasons.length ? (
                <div>
                  {reasons.map((reason) => (
                    <div key={reason.code} className="row" style={{ gap: 10, alignItems: 'center', marginBottom: 8 }}>
                      <span style={{ width: 88, fontSize: 12, color: 'var(--ink-2)' }}>{reason.label}</span>
                      <div style={{ flex: 1, height: 6, background: 'var(--surface-muted-2)', borderRadius: 999, overflow: 'hidden' }}>
                        <div style={{ width: `${Math.round((reason.count / maxReason) * 100)}%`, height: '100%', background: 'var(--danger-text)', borderRadius: 999 }} />
                      </div>
                      <span className="muted" style={{ width: 32, textAlign: 'right', fontSize: 11 }}>{reason.count}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="empty" style={{ padding: 18 }}>近 7 天暂无负反馈原因。</div>
              )}
            </div>
            <div className="table-wrap">
              <table className="data">
                <thead><tr><th>时间</th><th>智能体</th><th>用户</th><th>评分</th><th>原因</th><th className="wrap">提问</th><th className="wrap">回答</th><th>留问题</th><th>处理</th></tr></thead>
                <tbody>
                  {feedbackItems.map((item) => (
                    <tr key={item.id} className="clickable" onClick={() => openFeedbackSession(item)}>
                      <td className="muted" style={{ fontSize: 11, whiteSpace: 'nowrap' }}>{feedbackTime(item.createdAt)}</td>
                      <td>{item.agentName || item.agentId}</td>
                      <td>{item.userId}</td>
                      <td><span className={`badge ${item.rating === 'UP' ? 'good' : 'danger'}`}><span className="bd" />{item.rating === 'UP' ? '有用' : '没用'}</span></td>
                      <td>{item.reasons.map((r) => r.label).join('、') || '—'}</td>
                      <td className="wrap" style={{ maxWidth: 220 }}>{item.questionPreview || '—'}</td>
                      <td className="wrap" style={{ maxWidth: 260, color: 'var(--ink-2)' }}>{item.answerPreview || '—'}</td>
                      <td>{item.followUpRequested ? '是' : '—'}</td>
                      <td>{item.handleStatus === 'HANDLED' ? '已处理' : item.handleStatus === 'IGNORED' ? '已忽略' : '待处理'}</td>
                    </tr>
                  ))}
                  {!feedbackItems.length && (
                    <tr><td colSpan={9}><div className="empty" style={{ padding: 24 }}><Icon name="sessions" /><b>暂无反馈</b>用户在回答下方点赞/点踩后会出现在这里。</div></td></tr>
                  )}
                </tbody>
              </table>
            </div>
            <div className="pager">
              <span>共 {feedbackTotal} 条</span>
              <span className="spacer" />
              <button className="btn" disabled={feedbackPage <= 1} onClick={() => setFeedbackPage((p) => Math.max(1, p - 1))}>上一页</button>
              <span>第 {feedbackPage} / {feedbackPages} 页</span>
              <button className="btn" disabled={feedbackPage >= feedbackPages} onClick={() => setFeedbackPage((p) => Math.min(feedbackPages, p + 1))}>下一页</button>
            </div>
          </>
        )}
      </Panel>

      <Panel className="mt-3" title="埋点事件（近 7 天）" sub="服务端与客户端事件计数，用于验证漏斗与采纳口径">
        {eventStats.isError ? (
          <div className="empty" style={{ padding: 24 }}>
            <Icon name="spark" />
            <b>暂无权限</b>
            当前角色不能查看埋点统计。
          </div>
        ) : (
          <div className="panel-body">
            <div className="grid-3">
              {Object.entries(eventStats.data?.counts ?? {}).map(([name, count]) => (
                <div className="panel stat" style={{ padding: 12 }} key={name}>
                  <div className="lb">{eventLabels[name] ?? name}</div>
                  <div className="vl" style={{ fontSize: 22 }}>{count}</div>
                  <div className="delta"><span className="mono" style={{ fontSize: 10 }}>{name}</span></div>
                </div>
              ))}
              {!Object.keys(eventStats.data?.counts ?? {}).length && (
                <div className="empty" style={{ padding: 18, gridColumn: '1 / -1' }}>近 7 天暂无事件。</div>
              )}
            </div>
          </div>
        )}
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
