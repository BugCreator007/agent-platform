import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type ModelInfo, type Provider, type ProviderTestResult } from '../api'
import { Icon } from '../components/icons'
import {
  Avatar,
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

interface RoutingPolicy { strategy: string; primary: string; fallback: string[]; region: string }

export function ModelsPage() {
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState('all')
  const [selected, setSelected] = useState<Provider | null>(null)
  const [drawerTab, setDrawerTab] = useState('overview')
  const [addOpen, setAddOpen] = useState(false)
  const [newProvider, setNewProvider] = useState({ name: '', endpoint: 'https://api.example.com/v1', region: '华东 1', key: '', quota: 10000, protocol: 'openai' })
  const [routingOpen, setRoutingOpen] = useState(false)
  const [quotaOpen, setQuotaOpen] = useState(false)
  const [quota, setQuota] = useState(0)
  const [routing, setRouting] = useState<RoutingPolicy>({ strategy: 'failover', primary: '', fallback: [], region: '华东 1' })
  const [routingAdd, setRoutingAdd] = useState('')
  const [keyOpen, setKeyOpen] = useState(false)
  const [keyValue, setKeyValue] = useState('')
  const [modelOpen, setModelOpen] = useState(false)
  const [modelForm, setModelForm] = useState({ id: '', name: '', upstreamModel: '', context: '128K', inPrice: 0, outPrice: 0, capabilities: '文本', routeWeight: 10 })

  const providers = useQuery({ queryKey: ['providers'], queryFn: () => api.get<Provider[]>('/providers') })
  const models = useQuery({ queryKey: ['models'], queryFn: () => api.get<ModelInfo[]>('/models') })
  const routingQuery = useQuery({ queryKey: ['model-routing'], queryFn: () => api.get<RoutingPolicy>('/models/routing') })

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['providers'] })
    queryClient.invalidateQueries({ queryKey: ['models'] })
    queryClient.invalidateQueries({ queryKey: ['model-routing'] })
  }

  const act = useMutation({
    mutationFn: ({ id, act }: { id: string; act: string }) => api.post<Provider>(`/providers/${id}/${act}`),
    onSuccess: (p) => {
      toast('厂商状态已更新')
      refresh()
      if (selected?.id === p.id) setSelected(p)
    },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const testProvider = useMutation({
    mutationFn: (id: string) => api.post<ProviderTestResult>(`/providers/${id}/test`),
    onSuccess: (result, id) => {
      toast(result.ok ? `连接成功 · ${result.latency}` : `连接失败：${result.message}`, result.ok ? undefined : 'err')
      refresh()
      setSelected((current) => current && current.id === id
        ? { ...current, status: result.status, latency: result.latency } : current)
    },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const updateKey = useMutation({
    mutationFn: ({ id, key }: { id: string; key: string }) => api.put<Provider>(`/providers/${id}/key`, { key }),
    onSuccess: (provider) => {
      toast('API Key 已保存')
      setKeyOpen(false)
      setKeyValue('')
      refresh()
      if (selected?.id === provider.id) setSelected(provider)
    },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const createModel = useMutation({
    mutationFn: () => api.post<ModelInfo>('/models', {
      ...modelForm,
      providerId: selected?.id,
      capabilities: modelForm.capabilities.split(/[,，]/).map((value) => value.trim()).filter(Boolean),
    }),
    onSuccess: () => {
      toast('模型已添加')
      setModelOpen(false)
      refresh()
    },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const setDefault = useMutation({
    mutationFn: (id: string) => api.post<ModelInfo>(`/models/${id}/default`),
    onSuccess: () => { toast('默认模型已更新'); refresh() },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const saveQuota = useMutation({
    mutationFn: () => api.put<Provider>(`/providers/${selected!.id}/quota`, { quota }),
    onSuccess: () => { toast('额度已更新'); setQuotaOpen(false); refresh() },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const saveRouting = useMutation({
    mutationFn: () => api.put<RoutingPolicy>('/models/routing', routing),
    onSuccess: () => { toast('路由策略已保存'); setRoutingOpen(false); refresh() },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const createProvider = useMutation({
    mutationFn: (values: Record<string, unknown>) => api.post<Provider>('/providers', values),
    onSuccess: () => { toast('厂商已接入'); setAddOpen(false); setNewProvider({ name: '', endpoint: 'https://api.example.com/v1', region: '华东 1', key: '', quota: 10000, protocol: 'openai' }); refresh() },
    onError: (e: Error) => toast(e.message, 'err'),
  })

  const list = providers.data ?? []
  const allModels = models.data ?? []
  const filteredModels = allModels.filter((m) => filter === 'all' || m.providerId === filter)
  const healthy = list.filter((p) => p.status === 'healthy').length
  const degraded = list.filter((p) => p.status === 'degraded').length
  const paused = list.filter((p) => p.status === 'paused').length
  const primary = allModels.find((m) => m.id === routingQuery.data?.primary) ?? allModels.find((m) => m.isDefault) ?? allModels[0]
  const primaryProvider = list.find((p) => p.id === primary?.providerId)

  const openProvider = (p: Provider) => {
    setSelected(p)
    setDrawerTab('overview')
  }
  const openQuota = (p: Provider) => {
    setSelected(p)
    setQuota(p.quota)
    setQuotaOpen(true)
  }
  const openRouting = () => {
    const r = routingQuery.data
    setRouting(r ?? { strategy: 'failover', primary: primary?.id ?? '', fallback: allModels.slice(1, 4).map((m) => m.id), region: '华东 1' })
    setRoutingAdd('')
    setRoutingOpen(true)
  }
  const openKey = (provider: Provider) => {
    setSelected(provider)
    setKeyValue('')
    setKeyOpen(true)
  }
  const openModel = () => {
    setModelForm({ id: '', name: '', upstreamModel: '', context: '128K', inPrice: 0, outPrice: 0, capabilities: '文本', routeWeight: 10 })
    setModelOpen(true)
  }

  const providerModels = useMemo(() => selected ? allModels.filter((m) => m.providerId === selected.id) : [], [selected, allModels])

  return (
    <>
      <PageHead title="模型厂商" sub="统一管理 DeepSeek、Kimi、千问、Anthropic 等模型，按智能体绑定真实路由并在失败时自动切换。">
        <Button onClick={() => { refresh(); toast('模型目录已刷新') }}><Icon name="refresh" />刷新目录</Button>
        <Button kind="primary" onClick={() => setAddOpen(true)}><Icon name="plus" />接入厂商</Button>
      </PageHead>

      <div className="grid-4">
        <div className="panel stat"><div className="lb">已接入厂商</div><div className="vl">{list.length} <small>家</small></div><div className="delta"><Badge tone="good">{healthy} 正常</Badge><Badge tone="warn">{degraded} 降级</Badge><Badge>{paused} 暂停</Badge></div></div>
        <div className="panel stat"><div className="lb">可路由模型</div><div className="vl">{allModels.length} <small>个</small></div><div className="delta"><span>覆盖文本、多模态、Embedding</span></div></div>
        <div className="panel stat"><div className="lb">今日调用</div><div className="vl">{list.reduce((s, p) => s + p.calls, 0).toLocaleString()}</div><div className="delta"><span className="up">↑ 4.6%</span><span>较昨日</span></div></div>
        <div className="panel stat"><div className="lb">默认模型</div><div className="vl" style={{ fontSize: 19 }}>{primary?.name ?? '未设置'}</div><div className="delta"><span>{primaryProvider?.name ?? '—'}</span><span>·</span><span>{primary?.context ?? ''}</span></div></div>
      </div>

      <div className="section-head"><h2>厂商概览</h2><span className="section-note">点击卡片查看密钥、额度与模型清单</span></div>
      <div className="provider-grid">
        {list.map((p) => {
          const pct = Math.round((p.used / Math.max(1, p.quota)) * 100)
          return (
            <article key={p.id} className="panel provider-card" onClick={() => openProvider(p)}>
              <div className="pv-head">
                <div className="pv-logo" style={{ background: 'var(--pine-wash)', color: 'var(--pine-deep)' }}>{p.name.slice(0, 1)}</div>
                <div><div className="pv-name">{p.name}</div><div className="pv-sub">{p.endpoint}</div></div>
                <StatusBadge status={p.status} />
              </div>
              <div className="pv-stats">
                <div className="pv-stat"><div className="lb">今日调用</div><div className="vl">{p.calls.toLocaleString()}</div></div>
                <div className="pv-stat"><div className="lb">成功率</div><div className="vl">{p.success}%</div></div>
                <div className="pv-stat"><div className="lb">P95</div><div className="vl">{p.latency}</div></div>
              </div>
              <div className="pv-quota">
                <div className="row"><span>月度额度</span><span className="spacer" /><b className={pct > 90 ? 'bad' : pct > 75 ? 'warn' : ''}>¥{p.used.toLocaleString()} / ¥{p.quota.toLocaleString()}</b></div>
                <div className="budget-track"><i style={{ width: `${Math.min(100, pct)}%` }} className={pct > 90 ? 'danger' : pct > 75 ? 'warn' : ''} /></div>
              </div>
              <div className="pv-foot" onClick={(e) => e.stopPropagation()}>
                <Button size="sm" onClick={() => testProvider.mutate(p.id)}><Icon name="zap" />测试连接</Button>
                <Button size="sm" kind="ghost" onClick={() => openProvider(p)}>配置</Button>
                <span className="spacer" />
                <button className={`toggle ${p.status !== 'paused' ? 'on' : ''}`} onClick={() => act.mutate({ id: p.id, act: 'toggle' })} aria-label={`启用 ${p.name}`} />
              </div>
            </article>
          )
        })}
      </div>

      <div className="grid-2 mt-3">
        <Panel
          title="路由与故障转移"
          sub="默认模型、降级顺序与区域"
          tools={<><Badge tone="good">故障转移已开启</Badge><Button size="sm" onClick={openRouting}><Icon name="settings" />编辑策略</Button></>}
        >
          <div className="route-section">
            <div className="route-primary">
              <span className="badge good"><span className="bd" />主模型</span>
              <div className="route-node"><b>{primary?.name ?? '未配置'}</b><small>{primaryProvider?.name} · 权重 100%</small></div>
            </div>
            <div className="route-chain">
              {(routingQuery.data?.fallback ?? allModels.slice(1, 4).map((m) => m.id)).map((id) => {
                const m = allModels.find((x) => x.id === id)
                return (
                  <div key={id} className="route-node">
                    <b>{m?.name ?? id}</b><small>{list.find((p) => p.id === m?.providerId)?.name ?? '备用'} · 故障时切换</small>
                  </div>
                )
              })}
            </div>
          </div>
          <div className="callout info mt-3"><Icon name="shield" /><div>主模型连续 3 次超时或错误率超过 5% 时自动切换，恢复后 10 分钟回切。</div></div>
        </Panel>

        <Panel title="配额与密钥" sub="额度使用与密钥轮换状态" noBody>
          <div className="panel-body" style={{ paddingTop: 8 }}>
            {list.map((p) => {
              const pct = Math.round((p.used / Math.max(1, p.quota)) * 100)
              return (
                <div key={p.id} className="key-row" style={{ cursor: 'pointer' }} onClick={() => openProvider(p)}>
                  <div style={{ width: 30, height: 30, borderRadius: 8, background: 'var(--pine-wash)', color: 'var(--pine-deep)', display: 'grid', placeItems: 'center' }}>{p.name.slice(0, 1)}</div>
                  <div><div className="key-name">{p.name}</div><div className="key-meta">{p.key} · {p.region}</div></div>
                  <span className="spacer" />
                  <Badge tone={pct > 90 ? 'danger' : pct > 75 ? 'warn' : 'neutral'}>{pct}% 已用</Badge>
                </div>
              )
            })}
            <div className="panel-foot" style={{ paddingLeft: 0, paddingRight: 0 }}><span>建议每 90 天轮换一次密钥</span><span className="spacer" /><Button size="sm" onClick={() => toast('已生成轮换提醒')}>轮换提醒</Button></div>
          </div>
        </Panel>
      </div>

      <Panel
        className="mt-3"
        title="模型目录"
        sub="按厂商筛选模型，设置默认路由"
        tools={
          <div className="row wrap" style={{ gap: 6 }}>
            {[{ id: 'all', name: '全部' }, ...list.map((p) => ({ id: p.id, name: p.name }))].map((f) => (
              <button key={f.id} className={`chip ${filter === f.id ? 'active' : ''}`} onClick={() => setFilter(f.id)}>{f.name}</button>
            ))}
          </div>
        }
        noBody
      >
        <div className="table-wrap">
          <table className="data">
            <thead><tr><th>模型</th><th>厂商</th><th>能力</th><th className="num">上下文</th><th className="num">价格 / 百万 tokens</th><th>状态</th><th className="num">路由权重</th><th style={{ textAlign: 'right' }}>操作</th></tr></thead>
            <tbody>
              {filteredModels.map((m) => (
                <tr key={m.id} className="clickable" onClick={() => openProvider(list.find((p) => p.id === m.providerId) ?? list[0])}>
                  <td><b>{m.name}</b>{m.isDefault && <span className="badge info" style={{ marginLeft: 6 }}><span className="bd" />默认</span>}<div className="sub mono" style={{ fontSize: 10 }}>{m.upstreamModel}</div></td>
                  <td>{list.find((p) => p.id === m.providerId)?.name ?? m.providerId}</td>
                  <td>{m.capabilities.map((c) => <span key={c} className="wf-tag" style={{ marginRight: 4 }}>{c}</span>)}</td>
                  <td className="num">{m.context}</td>
                  <td className="num">¥{m.inPrice} / ¥{m.outPrice}</td>
                  <td><StatusBadge status={m.status} /></td>
                  <td className="num">{m.routeWeight}%</td>
                  <td style={{ textAlign: 'right' }} onClick={(e) => e.stopPropagation()}>
                    {m.isDefault ? <span className="muted" style={{ fontSize: 10.5 }}>当前默认</span> : <Button size="sm" onClick={() => setDefault.mutate(m.id)}>设为默认</Button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="panel-foot"><span>模型清单来自平台目录，可手动刷新</span><span className="spacer" /><Button kind="ghost" size="sm" onClick={() => toast('模型目录已刷新')}><Icon name="refresh" />刷新目录</Button></div>
      </Panel>

      <Drawer open={!!selected} onClose={() => setSelected(null)} width={720}>
        {selected && (
          <>
            <div className="drawer-head">
              <Avatar text={selected.name.slice(0, 1)} tone="blue" size={38} />
              <div className="dh-main"><div className="drawer-title">{selected.name} <StatusBadge status={selected.status} /></div><div className="drawer-sub"><span>{selected.endpoint}</span><span>·</span><span>{selected.region}</span></div></div>
              <button className="icon-btn" onClick={() => setSelected(null)}><Icon name="x" /></button>
            </div>
            <div className="drawer-body">
              <Tabs active={drawerTab} onChange={setDrawerTab} items={[{ key: 'overview', label: '概览' }, { key: 'models', label: '模型' }, { key: 'quota', label: '额度与密钥' }]} />
              {drawerTab === 'overview' && (
                <>
                  <div className="grid-3">
                    <div className="panel stat" style={{ padding: 13 }}><div className="lb">今日调用</div><div className="vl" style={{ fontSize: 24 }}>{selected.calls.toLocaleString()}</div></div>
                    <div className="panel stat" style={{ padding: 13 }}><div className="lb">成功率</div><div className="vl" style={{ fontSize: 24 }}>{selected.success}%</div></div>
                    <div className="panel stat" style={{ padding: 13 }}><div className="lb">P95</div><div className="vl" style={{ fontSize: 24 }}>{selected.latency}</div></div>
                  </div>
                  <div className="section-head"><h2>连接信息</h2></div>
                  <div className="kv">
                    <dt>Endpoint</dt><dd className="mono" style={{ fontSize: 11.5 }}>{selected.endpoint}</dd>
                    <dt>协议</dt><dd>{selected.protocol === 'anthropic' ? 'Anthropic Messages' : 'OpenAI Compatible'}</dd>
                    <dt>区域</dt><dd>{selected.region}</dd>
                    <dt>路由角色</dt><dd>{selected.route}</dd>
                    <dt>API Key</dt><dd>{selected.keyConfigured ? <><Badge tone="good">已配置</Badge> <span className="muted mono" style={{ fontSize: 10.5 }}>{selected.key}</span></> : <Badge tone="warn">未配置</Badge>}</dd>
                    <dt>模型数量</dt><dd>{providerModels.length} 个</dd>
                  </div>
                  <div className="callout info mt-3"><Icon name="shield" /><div>密钥使用 AES-GCM 加密保存，接口只返回掩码。更新密钥后可立即执行真实连通性测试。</div></div>
                </>
              )}
              {drawerTab === 'models' && (
                <>
                  <div className="row" style={{ marginBottom: 10 }}>
                    <span className="section-note">上游模型 ID 用于真实 API 请求</span>
                    <span className="spacer" />
                    <Button size="sm" kind="primary" onClick={openModel}><Icon name="plus" />添加模型</Button>
                  </div>
                  <div className="table-wrap">
                    <table className="data">
                      <thead><tr><th>模型</th><th>能力</th><th className="num">上下文</th><th>上游模型</th><th className="num">权重</th><th style={{ textAlign: 'right' }}>默认</th></tr></thead>
                      <tbody>
                        {providerModels.map((m) => (
                          <tr key={m.id}><td><b>{m.name}</b><div className="sub mono">{m.id}</div></td><td>{m.capabilities.join(' · ')}</td><td className="num">{m.context}</td><td className="mono" style={{ fontSize: 11 }}>{m.upstreamModel}</td><td className="num">{m.routeWeight}%</td><td style={{ textAlign: 'right' }}>{m.isDefault ? <Badge tone="info">默认</Badge> : <Button size="sm" onClick={() => setDefault.mutate(m.id)}>设为默认</Button>}</td></tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
              {drawerTab === 'quota' && (
                <>
                  <div className="field"><label>API Key</label><div className="row"><div className="key-mask">{selected.key}</div>{selected.keyConfigured ? <Badge tone="good">已加密保存</Badge> : <Badge tone="warn">未配置</Badge>}</div><div className="hint">更新后立即生效；DeepSeek 未在页面配置时会回退到环境变量 DEEPSEEK_API_KEY。</div></div>
                  <div className="field"><label>月度额度</label><div className="hint">已使用 ¥{selected.used.toLocaleString()} / ¥{selected.quota.toLocaleString()}</div><div className="budget-track"><i style={{ width: `${Math.min(100, Math.round(selected.used / Math.max(1, selected.quota) * 100))}%` }} /></div></div>
                  <div className="field"><label>额度调整</label><Button onClick={() => openQuota(selected)}><Icon name="settings" />调整额度</Button></div>
                  <div className="panel-foot" style={{ paddingLeft: 0, paddingRight: 0 }}><span>建议每 90 天轮换一次密钥</span><span className="spacer" /><Button size="sm" onClick={() => openKey(selected)}><Icon name="key" />更新密钥</Button></div>
                </>
              )}
            </div>
            <div className="drawer-foot">
              <Button kind="danger" onClick={() => act.mutate({ id: selected.id, act: 'toggle' })}>{selected.status === 'paused' ? '启用厂商' : '停用厂商'}</Button>
              <span className="spacer" />
              <Button onClick={() => testProvider.mutate(selected.id)}><Icon name="zap" />测试连接</Button>
              <Button kind="primary" onClick={openRouting}><Icon name="settings" />路由策略</Button>
            </div>
          </>
        )}
      </Drawer>

      <Modal
        open={addOpen}
        onClose={() => setAddOpen(false)}
        title="接入模型厂商"
        sub="填写厂商信息与 API Key，接入后先执行连通性测试"
        footer={<><span className="spacer" /><Button onClick={() => setAddOpen(false)}>取消</Button><Button kind="primary" disabled={!newProvider.name.trim()} onClick={() => createProvider.mutate(newProvider)}>接入厂商</Button></>}
      >
        <div className="field-grid">
          <div className="field"><label>厂商名称</label><input className="input" value={newProvider.name} onChange={(e) => setNewProvider({ ...newProvider, name: e.target.value })} placeholder="例如：DeepSeek" /></div>
          <div className="field"><label>区域</label><select className="select" value={newProvider.region} onChange={(e) => setNewProvider({ ...newProvider, region: e.target.value })}><option>华东 1</option><option>华北 3</option><option>新加坡</option></select></div>
        </div>
        <div className="field"><label>协议</label><select className="select" value={newProvider.protocol} onChange={(e) => setNewProvider({ ...newProvider, protocol: e.target.value })}><option value="openai">OpenAI Compatible（DeepSeek / Kimi / 千问 / OpenAI / 智谱）</option><option value="anthropic">Anthropic Messages</option></select></div>
        <div className="field"><label>Endpoint</label><input className="input" value={newProvider.endpoint} onChange={(e) => setNewProvider({ ...newProvider, endpoint: e.target.value })} placeholder="https://api.moonshot.cn/v1" /><div className="hint">填写 API Base URL，例如 Kimi 为 https://api.moonshot.cn/v1，千问为 https://dashscope.aliyuncs.com/compatible-mode/v1。</div></div>
        <div className="field"><label>API Key</label><input className="input" type="password" value={newProvider.key} onChange={(e) => setNewProvider({ ...newProvider, key: e.target.value })} placeholder="sk-…" /><div className="hint">密钥会加密保存，仅用于服务端调用。</div></div>
        <div className="field"><label>月度额度</label><input className="input" type="number" value={newProvider.quota} onChange={(e) => setNewProvider({ ...newProvider, quota: Number(e.target.value) })} /></div>
      </Modal>

      <Modal
        open={routingOpen}
        onClose={() => setRoutingOpen(false)}
        title="编辑路由策略"
        sub="默认模型、故障转移顺序与降级规则"
        footer={<><span className="spacer" /><Button onClick={() => setRoutingOpen(false)}>取消</Button><Button kind="primary" onClick={() => saveRouting.mutate()}>保存策略</Button></>}
      >
        <div className="field"><label>默认模型</label><select className="select" value={routing.primary} onChange={(e) => setRouting({ ...routing, primary: e.target.value })}>{allModels.map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}</select></div>
        <div className="field">
          <label>故障转移顺序</label>
          <div className="node-list">{routing.fallback.map((id) => <div key={id} className="node-card"><span className="node-index"><Icon name="git" /></span><span>{allModels.find((m) => m.id === id)?.name ?? id}</span><span className="spacer" /><button className="btn sm ghost" onClick={() => setRouting({ ...routing, fallback: routing.fallback.filter((x) => x !== id) })}>移除</button></div>)}</div>
          <div className="row" style={{ marginTop: 8 }}>
            <select className="select" value={routingAdd} onChange={(e) => setRoutingAdd(e.target.value)}>
              <option value="">选择备用模型</option>
              {allModels.filter((m) => m.id !== routing.primary && !routing.fallback.includes(m.id)).map((m) => (
                <option key={m.id} value={m.id}>{m.name} · {list.find((p) => p.id === m.providerId)?.name ?? m.providerId}</option>
              ))}
            </select>
            <Button disabled={!routingAdd} onClick={() => { setRouting({ ...routing, fallback: [...routing.fallback, routingAdd] }); setRoutingAdd('') }}>添加</Button>
          </div>
          <div className="hint">主模型失败且尚未输出内容时，按顺序切换；已输出内容后不会重试，避免重复回答。</div>
        </div>
        <div className="field"><label>策略</label><select className="select" value={routing.strategy} onChange={(e) => setRouting({ ...routing, strategy: e.target.value })}><option value="failover">故障转移</option><option value="cost">成本优先</option><option value="latency">延迟优先</option><option value="quality">质量优先</option></select></div>
      </Modal>

      <Modal
        open={quotaOpen}
        onClose={() => setQuotaOpen(false)}
        title={`调整月度额度 · ${selected?.name ?? ''}`}
        footer={<><span className="spacer" /><Button onClick={() => setQuotaOpen(false)}>取消</Button><Button kind="primary" onClick={() => saveQuota.mutate()}>保存额度</Button></>}
      >
        <div className="field"><label>月度额度（元）</label><input className="input" type="number" value={quota} onChange={(e) => setQuota(Number(e.target.value))} /></div>
        <div className="field"><label>使用预警</label><select className="select"><option>75%</option><option>85%</option><option>90%</option></select><div className="hint">达到阈值后通过企微和应用内通知提醒。</div></div>
      </Modal>

      <Modal
        open={keyOpen}
        onClose={() => setKeyOpen(false)}
        title={`更新 API Key · ${selected?.name ?? ''}`}
        sub="密钥仅提交到服务端，使用 AES-GCM 加密保存"
        footer={<><span className="spacer" /><Button onClick={() => setKeyOpen(false)}>取消</Button><Button kind="primary" disabled={!keyValue.trim() || updateKey.isPending} onClick={() => selected && updateKey.mutate({ id: selected.id, key: keyValue })}>保存密钥</Button></>}
      >
        <div className="field"><label>新 API Key</label><input className="input" type="password" autoComplete="off" value={keyValue} onChange={(e) => setKeyValue(e.target.value)} placeholder="sk-…" /><div className="hint">保存后建议立即点击「测试连接」验证厂商、Endpoint、模型和密钥是否匹配。</div></div>
        <div className="callout info"><Icon name="shield" /><div>接口不会返回明文密钥；页面只显示掩码和是否已配置。</div></div>
      </Modal>

      <Modal
        open={modelOpen}
        onClose={() => setModelOpen(false)}
        title={`添加模型 · ${selected?.name ?? ''}`}
        sub="模型 ID 用于平台内部绑定，上游模型 ID 用于真实厂商请求"
        footer={<><span className="spacer" /><Button onClick={() => setModelOpen(false)}>取消</Button><Button kind="primary" disabled={!modelForm.id.trim() || createModel.isPending} onClick={() => createModel.mutate()}>添加模型</Button></>}
      >
        <div className="field-grid">
          <div className="field"><label>模型 ID</label><input className="input" value={modelForm.id} onChange={(e) => setModelForm({ ...modelForm, id: e.target.value })} placeholder="kimi-k2.5" /></div>
          <div className="field"><label>显示名称</label><input className="input" value={modelForm.name} onChange={(e) => setModelForm({ ...modelForm, name: e.target.value })} placeholder="Kimi K2.5" /></div>
          <div className="field"><label>上游模型 ID</label><input className="input" value={modelForm.upstreamModel} onChange={(e) => setModelForm({ ...modelForm, upstreamModel: e.target.value })} placeholder="留空则与模型 ID 一致" /></div>
          <div className="field"><label>上下文</label><input className="input" value={modelForm.context} onChange={(e) => setModelForm({ ...modelForm, context: e.target.value })} placeholder="128K" /></div>
          <div className="field"><label>输入价格 / 百万 tokens</label><input className="input" type="number" value={modelForm.inPrice} onChange={(e) => setModelForm({ ...modelForm, inPrice: Number(e.target.value) })} /></div>
          <div className="field"><label>输出价格 / 百万 tokens</label><input className="input" type="number" value={modelForm.outPrice} onChange={(e) => setModelForm({ ...modelForm, outPrice: Number(e.target.value) })} /></div>
          <div className="field"><label>能力标签</label><input className="input" value={modelForm.capabilities} onChange={(e) => setModelForm({ ...modelForm, capabilities: e.target.value })} placeholder="推理, 工具, 中文" /></div>
          <div className="field"><label>路由权重</label><input className="input" type="number" value={modelForm.routeWeight} onChange={(e) => setModelForm({ ...modelForm, routeWeight: Number(e.target.value) })} /></div>
        </div>
        <div className="callout info"><Icon name="help" /><div>厂商返回 404/model_not_found 时，优先核对上游模型 ID。Kimi 常用 kimi-k2.5，千问常用 qwen3-max。</div></div>
      </Modal>
    </>
  )
}
