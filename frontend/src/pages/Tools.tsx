import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Tool } from '../api'
import { Icon } from '../components/icons'
import {
  Badge,
  Button,
  Drawer,
  Modal,
  PageHead,
  Panel,
  StatusBadge,
  Tabs,
  toast,
} from '../components/ui'

export function ToolsPage() {
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState('all')
  const [selected, setSelected] = useState<Tool | null>(null)
  const [drawerTab, setDrawerTab] = useState('overview')
  const [addOpen, setAddOpen] = useState(false)
  const [mcpOpen, setMcpOpen] = useState(false)
  const [toolForm, setToolForm] = useState({ name: '', type: 'HTTP', owner: '客服平台', description: '', endpoint: '' })
  const [mcpForm, setMcpForm] = useState({ name: '', mode: 'stdio', endpoint: '' })

  const tools = useQuery({ queryKey: ['tools'], queryFn: () => api.get<Tool[]>('/tools') })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['tools'] })

  const toggle = useMutation({
    mutationFn: (name: string) => api.post<Tool>(`/tools/${encodeURIComponent(name)}/toggle`),
    onSuccess: (t) => { toast(`${t.name} 已${t.enabled ? '启用' : '停用'}`); refresh(); if (selected?.name === t.name) setSelected(t) },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const check = useMutation({
    mutationFn: () => api.post<Tool[]>('/tools/check'),
    onSuccess: () => { toast('工具健康检查完成'); refresh() },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const create = useMutation({
    mutationFn: (body: Record<string, unknown>) => api.post<Tool>('/tools', body),
    onSuccess: () => { toast('工具已注册'); setAddOpen(false); setToolForm({ name: '', type: 'HTTP', owner: '客服平台', description: '', endpoint: '' }); refresh() },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const addMcp = useMutation({
    mutationFn: (body: Record<string, unknown>) => api.post<Tool>('/tools/mcp', body),
    onSuccess: () => { toast('MCP 服务已接入'); setMcpOpen(false); setMcpForm({ name: '', mode: 'stdio', endpoint: '' }); refresh() },
    onError: (e: Error) => toast(e.message, 'err'),
  })

  const list = tools.data ?? []
  const filtered = list.filter((t) => filter === 'all' || t.type === filter)
  const healthy = list.filter((t) => t.health === 'healthy').length
  const degraded = list.filter((t) => t.health === 'degraded').length
  const totalCalls = list.reduce((s, t) => s + t.calls, 0)
  const types = useMemo(() => Array.from(new Set(list.map((t) => t.type))), [list])

  return (
    <>
      <PageHead title="工具与 MCP" sub="管理智能体可调用的工具与 MCP 服务，查看健康度、错误率并设置调用权限。">
        <Button onClick={() => check.mutate()}><Icon name="refresh" />全部检查</Button>
        <Button onClick={() => setMcpOpen(true)}><Icon name="tools" />接入 MCP</Button>
        <Button kind="primary" onClick={() => setAddOpen(true)}><Icon name="plus" />添加工具</Button>
      </PageHead>

      <div className="grid-4">
        <div className="panel stat"><div className="lb">工具总数</div><div className="vl">{list.length}</div><div className="delta"><span>按智能体显式启用</span></div></div>
        <div className="panel stat"><div className="lb">健康</div><div className="vl">{healthy}</div><div className="delta"><Badge tone="good">正常</Badge><Badge tone="warn">{degraded} 降级</Badge></div></div>
        <div className="panel stat"><div className="lb">今日调用</div><div className="vl">{totalCalls.toLocaleString()}</div><div className="delta"><span className="up">↑ 8.4%</span><span>较昨日</span></div></div>
        <div className="panel stat"><div className="lb">平均错误率</div><div className="vl">{list.length ? (list.reduce((s, t) => s + t.errorRate, 0) / list.length).toFixed(1) : 0}<small>%</small></div><div className="delta"><span className="down">↓ 0.2pt</span></div></div>
      </div>

      <div className="filterbar mt-3">
        <div className="row wrap" style={{ gap: 6 }}>
          {['all', ...types].map((t) => <button key={t} className={`chip ${filter === t ? 'active' : ''}`} onClick={() => setFilter(t)}>{t === 'all' ? '全部' : t}<span className="count">{t === 'all' ? list.length : list.filter((x) => x.type === t).length}</span></button>)}
        </div>
        <span className="spacer" />
        <Button size="sm" onClick={() => setAddOpen(true)}><Icon name="plus" />添加工具</Button>
      </div>

      <Panel title="工具列表" sub="错误率为近 24 小时统计" tools={<><Badge tone="good">{healthy} 正常</Badge>{degraded > 0 && <Badge tone="warn">{degraded} 降级</Badge>}</>} noBody>
        <div className="table-wrap">
          <table className="data">
            <thead><tr><th>工具</th><th>类型</th><th>归属</th><th className="num">调用次数</th><th className="num">错误率</th><th>健康</th><th>最近检查</th><th style={{ textAlign: 'right' }}>操作</th></tr></thead>
            <tbody>
              {filtered.map((t) => (
                <tr key={t.name} className="clickable" onClick={() => { setSelected(t); setDrawerTab('overview') }}>
                  <td><b className="mono">{t.name}</b><div className="sub">{t.description}</div></td>
                  <td><span className="wf-tag">{t.type}</span></td>
                  <td>{t.owner}</td>
                  <td className="num">{t.calls.toLocaleString()}</td>
                  <td className="num" style={{ color: t.errorRate > 2 ? 'var(--danger-text)' : undefined }}>{t.errorRate}%</td>
                  <td><StatusBadge status={t.health} /></td>
                  <td className="muted">{t.checked}</td>
                  <td style={{ textAlign: 'right' }} onClick={(e) => e.stopPropagation()}><Button size="sm" onClick={() => toggle.mutate(t.name)}>{t.enabled ? '停用' : '启用'}</Button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Panel>

      <Drawer open={!!selected} onClose={() => setSelected(null)} width={680}>
        {selected && (
          <>
            <div className="drawer-head"><div className="avatar" style={{ width: 36, height: 36, borderRadius: 10, background: '#F0F2E9', color: 'var(--ink-2)' }}><Icon name="tools" /></div><div className="dh-main"><div className="drawer-title mono" style={{ fontSize: 15 }}>{selected.name} {selected.enabled ? <Badge tone="good">已启用</Badge> : <Badge>已停用</Badge>}</div><div className="drawer-sub"><span>{selected.type}</span><span>·</span><span>{selected.owner}</span><span>·</span><StatusBadge status={selected.health} /></div></div><button className="icon-btn" onClick={() => setSelected(null)}><Icon name="x" /></button></div>
            <div className="drawer-body">
              <Tabs active={drawerTab} onChange={setDrawerTab} items={[{ key: 'overview', label: '概览' }, { key: 'permissions', label: '权限矩阵' }]} />
              {drawerTab === 'overview' && (
                <>
                  <div className="grid-3"><div className="panel stat" style={{ padding: 12 }}><div className="lb">调用次数</div><div className="vl" style={{ fontSize: 24 }}>{selected.calls.toLocaleString()}</div></div><div className="panel stat" style={{ padding: 12 }}><div className="lb">错误率</div><div className="vl" style={{ fontSize: 24 }}>{selected.errorRate}%</div></div><div className="panel stat" style={{ padding: 12 }}><div className="lb">最近检查</div><div className="vl" style={{ fontSize: 18 }}>{selected.checked}</div></div></div>
                  <div className="section-head"><h2>参数 Schema</h2></div>
                  <pre className="codebox">{JSON.stringify({ orderId: 'string · required', fields: 'array<string>', dryRun: 'boolean · default true' }, null, 2)}</pre>
                  <div className="callout info mt-3"><Icon name="shield" /><div>工具调用需要智能体显式授权；生产环境写操作默认要求人工确认。</div></div>
                </>
              )}
              {drawerTab === 'permissions' && (
                <div className="table-wrap"><table className="data"><thead><tr><th>智能体</th><th>读取</th><th>写入</th><th>审批</th></tr></thead><tbody>{['售后工单助手', '退款审核 Copilot', '发票助手'].map((a, i) => <tr key={a}><td>{a}</td><td><Badge tone="good">允许</Badge></td><td>{i === 0 ? <Badge tone="warn">需审批</Badge> : <Badge>禁止</Badge>}</td><td>{i === 0 ? '退款审核组' : '—'}</td></tr>)}</tbody></table></div>
              )}
            </div>
            <div className="drawer-foot"><span className="spacer" /><Button onClick={() => toast('连接测试通过')}><Icon name="zap" />测试连接</Button><Button kind="primary" onClick={() => toggle.mutate(selected.name)}>{selected.enabled ? '停用工具' : '启用工具'}</Button></div>
          </>
        )}
      </Drawer>

      <Modal open={addOpen} onClose={() => setAddOpen(false)} title="添加工具" sub="工具注册后需由智能体显式启用" footer={<><span className="spacer" /><Button onClick={() => setAddOpen(false)}>取消</Button><Button kind="primary" disabled={!toolForm.name.trim()} onClick={() => create.mutate(toolForm)}>添加工具</Button></>}>
        <div className="field"><label>工具名称</label><input className="input" value={toolForm.name} onChange={(e) => setToolForm({ ...toolForm, name: e.target.value })} placeholder="order.lookup" /></div>
        <div className="field"><label>类型</label><select className="select" value={toolForm.type} onChange={(e) => setToolForm({ ...toolForm, type: e.target.value })}><option>HTTP</option><option>MCP</option><option>内置函数</option></select></div>
        <div className="field"><label>Endpoint</label><input className="input" value={toolForm.endpoint} onChange={(e) => setToolForm({ ...toolForm, endpoint: e.target.value })} placeholder="https://api.example.com/orders" /></div>
        <div className="field"><label>描述</label><textarea className="input" rows={3} value={toolForm.description} onChange={(e) => setToolForm({ ...toolForm, description: e.target.value })} placeholder="这个工具做什么，入参和出参是什么" /></div>
      </Modal>

      <Modal open={mcpOpen} onClose={() => setMcpOpen(false)} title="接入 MCP 服务" sub="通过 Model Context Protocol 连接外部能力" footer={<><span className="spacer" /><Button onClick={() => setMcpOpen(false)}>取消</Button><Button kind="primary" disabled={!mcpForm.name.trim()} onClick={() => addMcp.mutate({ ...mcpForm, type: 'MCP', owner: '供应链', description: 'MCP 服务' })}>连接并验证</Button></>}>
        <div className="field"><label>服务名称</label><input className="input" value={mcpForm.name} onChange={(e) => setMcpForm({ ...mcpForm, name: e.target.value })} placeholder="inventory-mcp" /></div>
        <div className="field"><label>连接方式</label><select className="select" value={mcpForm.mode} onChange={(e) => setMcpForm({ ...mcpForm, mode: e.target.value })}><option>stdio</option><option>SSE</option><option>Streamable HTTP</option></select></div>
        <div className="field"><label>启动命令 / URL</label><input className="input" value={mcpForm.endpoint} onChange={(e) => setMcpForm({ ...mcpForm, endpoint: e.target.value })} placeholder="npx @example/inventory-mcp" /></div>
        <div className="callout info"><Icon name="shield" /><div>接入前会执行能力探测，只注册用户批准的工具。</div></div>
      </Modal>
    </>
  )
}
