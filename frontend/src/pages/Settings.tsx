import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type ApiKey, type AuditLog, type Member } from '../api'
import { Icon } from '../components/icons'
import {
  Badge,
  Button,
  Modal,
  PageHead,
  Panel,
  Tabs,
  toast,
} from '../components/ui'

export function SettingsPage() {
  const queryClient = useQueryClient()
  const [tab, setTab] = useState('members')
  const [inviteOpen, setInviteOpen] = useState(false)
  const [keyOpen, setKeyOpen] = useState(false)
  const [createdKey, setCreatedKey] = useState<string | null>(null)
  const [inviteForm, setInviteForm] = useState({ name: '', email: '', role: '开发者' })
  const [keyForm, setKeyForm] = useState({ name: '', scope: 'runtime:read' })
  const [envPolicy, setEnvPolicy] = useState(true)

  const members = useQuery({ queryKey: ['members'], queryFn: () => api.get<Member[]>('/members') })
  const keys = useQuery({ queryKey: ['api-keys'], queryFn: () => api.get<ApiKey[]>('/api-keys') })
  const audit = useQuery({ queryKey: ['audit'], queryFn: () => api.get<AuditLog[]>('/audit-logs') })

  const changeRole = useMutation({
    mutationFn: ({ name, role }: { name: string; role: string }) => api.put<Member>(`/members/${encodeURIComponent(name)}/role`, { role }),
    onSuccess: () => { toast('角色已更新'); queryClient.invalidateQueries({ queryKey: ['members'] }) },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const invite = useMutation({
    mutationFn: (body: Record<string, unknown>) => api.post<Member>('/members', body),
    onSuccess: () => { toast('邀请已发送'); setInviteOpen(false); setInviteForm({ name: '', email: '', role: '开发者' }); queryClient.invalidateQueries({ queryKey: ['members'] }) },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const createKey = useMutation({
    mutationFn: (body: Record<string, unknown>) => api.post<ApiKey>('/api-keys', body),
    onSuccess: (k) => { setKeyOpen(false); setCreatedKey(k.key); setKeyForm({ name: '', scope: 'runtime:read' }); queryClient.invalidateQueries({ queryKey: ['api-keys'] }) },
    onError: (e: Error) => toast(e.message, 'err'),
  })

  const memberList = members.data ?? []
  const keyList = keys.data ?? []
  const auditList = audit.data ?? []

  return (
    <>
      <PageHead title="平台设置" sub="管理成员权限、API 密钥、安全护栏与审计记录。所有高风险操作都会写入审计日志。" />

      <div className="grid-4">
        <div className="panel stat"><div className="lb">成员</div><div className="vl">{memberList.length}</div><div className="delta"><span>平台管理员 1 名</span></div></div>
        <div className="panel stat"><div className="lb">API 密钥</div><div className="vl">{keyList.length}</div><div className="delta"><span>最近 7 天使用 3 个</span></div></div>
        <div className="panel stat"><div className="lb">审计记录</div><div className="vl">{auditList.length}</div><div className="delta"><span>留存 180 天</span></div></div>
        <div className="panel stat"><div className="lb">安全护栏</div><div className="vl" style={{ fontSize: 20 }}>已开启</div><div className="delta"><Badge tone="good">生产保护</Badge></div></div>
      </div>

      <Panel className="mt-3" noBody>
        <div style={{ padding: '8px 18px 0' }}>
          <Tabs active={tab} onChange={setTab} items={[{ key: 'members', label: '成员与角色' }, { key: 'keys', label: 'API 密钥' }, { key: 'audit', label: '审计日志' }, { key: 'security', label: '安全护栏' }]} />
        </div>
        {tab === 'members' && (
          <>
            <div className="panel-head" style={{ paddingTop: 0 }}><div><h2 className="panel-title">成员与角色</h2><p className="panel-sub">角色决定可访问的模块与操作</p></div><div className="panel-tools"><Button size="sm" kind="primary" onClick={() => setInviteOpen(true)}><Icon name="users" />邀请成员</Button></div></div>
            <div className="table-wrap">
              <table className="data">
                <thead><tr><th>成员</th><th>角色</th><th className="num">负责智能体</th><th>最近活跃</th><th>状态</th></tr></thead>
                <tbody>{memberList.map((m) => <tr key={m.name}><td><div className="member-cell"><div className="avatar" style={{ background: 'var(--pine-wash)', color: 'var(--pine-deep)' }}>{m.name.slice(0, 1)}</div><div><div className="nm">{m.name}</div><div className="em">{m.email}</div></div></div></td><td><select className="select" style={{ height: 30 }} value={m.role} onChange={(e) => changeRole.mutate({ name: m.name, role: e.target.value })}>{['平台管理员', '开发者', '评测员', '只读成员'].map((r) => <option key={r}>{r}</option>)}</select></td><td className="num">{m.agents}</td><td className="muted">{m.last}</td><td><Badge tone="good">活跃</Badge></td></tr>)}</tbody>
              </table>
            </div>
          </>
        )}
        {tab === 'keys' && (
          <>
            <div className="panel-head" style={{ paddingTop: 0 }}><div><h2 className="panel-title">API 密钥</h2><p className="panel-sub">密钥只会在创建时完整展示一次</p></div><div className="panel-tools"><Button size="sm" kind="primary" onClick={() => setKeyOpen(true)}><Icon name="key" />创建密钥</Button></div></div>
            <div className="table-wrap">
              <table className="data">
                <thead><tr><th>名称</th><th>密钥</th><th>范围</th><th>创建时间</th><th>最近使用</th><th style={{ textAlign: 'right' }}>操作</th></tr></thead>
                <tbody>{keyList.map((k) => <tr key={k.name}><td>{k.name}</td><td><span className="key-mask">{k.key}<button className="btn sm ghost" onClick={() => { navigator.clipboard?.writeText(k.key); toast('密钥已复制') }}><Icon name="copy" /></button></span></td><td><Badge tone="info">{k.scope}</Badge></td><td className="muted">{k.created}</td><td className="muted">{k.last}</td><td style={{ textAlign: 'right' }}><Button size="sm" kind="danger" onClick={() => toast('已撤销（演示）')}>撤销</Button></td></tr>)}</tbody>
              </table>
            </div>
          </>
        )}
        {tab === 'audit' && (
          <>
            <div className="panel-head" style={{ paddingTop: 0 }}><div><h2 className="panel-title">审计日志</h2><p className="panel-sub">高风险操作全部记录，不可修改</p></div><div className="panel-tools"><Button size="sm" onClick={() => toast('审计日志已导出')}><Icon name="download" />导出</Button></div></div>
            <div className="table-wrap">
              <table className="data">
                <thead><tr><th>操作人</th><th>操作</th><th>对象</th><th>时间</th><th>来源 IP</th></tr></thead>
                <tbody>{auditList.map((a, i) => <tr key={`${a.who}-${i}`}><td>{a.who}</td><td><span className="audit-action">{a.action}</span></td><td className="mono" style={{ fontSize: 11.5 }}>{a.object}</td><td className="muted">{a.time}</td><td className="mono" style={{ fontSize: 11.5 }}>{a.ip}</td></tr>)}</tbody>
              </table>
            </div>
          </>
        )}
        {tab === 'security' && (
          <div className="panel-body">
            <div className="grid-2">
              <div>
                <div className="section-head"><h2>生产环境保护</h2></div>
                <div className="checklist">
                  <div className="ci"><Icon name="shield" />生产环境写操作需要二次确认</div>
                  <div className="ci"><Icon name="shield" />删除智能体、知识库、密钥需要管理员角色</div>
                  <div className="ci"><Icon name="shield" />发布与回滚全部写入审计日志</div>
                  <div className="ci"><Icon name="shield" />敏感配置使用 KMS 加密</div>
                </div>
                <div className="field mt-3"><label>环境写权限</label><label className="checkline"><input type="checkbox" checked={envPolicy} onChange={(e) => setEnvPolicy(e.target.checked)} />仅平台管理员可修改生产环境配置</label></div>
                <Button kind="primary" onClick={() => toast('安全护栏设置已保存')}>保存设置</Button>
              </div>
              <div>
                <div className="section-head"><h2>近期风险事件</h2></div>
                <div className="alert-item"><div className="alert-icon warning"><Icon name="key" /></div><div><div className="al-title">生产环境密钥即将到期</div><div className="al-meta">3 个密钥将在 14 天内到期</div></div></div>
                <div className="alert-item"><div className="alert-icon info"><Icon name="users" /></div><div><div className="al-title">新成员加入</div><div className="al-meta">李婧 · 开发者 · 2 小时前</div></div></div>
                <div className="callout info mt-3"><Icon name="shield" /><div>安全设置变更会在 5 分钟内同步到运行时网关。</div></div>
              </div>
            </div>
          </div>
        )}
      </Panel>

      <Modal open={inviteOpen} onClose={() => setInviteOpen(false)} title="邀请成员" sub="邀请后将发送企微通知" footer={<><span className="spacer" /><Button onClick={() => setInviteOpen(false)}>取消</Button><Button kind="primary" disabled={!inviteForm.name.trim() || !inviteForm.email.trim()} onClick={() => invite.mutate(inviteForm)}>发送邀请</Button></>}>
        <div className="field"><label>姓名</label><input className="input" value={inviteForm.name} onChange={(e) => setInviteForm({ ...inviteForm, name: e.target.value })} placeholder="成员姓名" /></div>
        <div className="field"><label>邮箱</label><input className="input" value={inviteForm.email} onChange={(e) => setInviteForm({ ...inviteForm, email: e.target.value })} placeholder="name@company.com" /></div>
        <div className="field"><label>角色</label><select className="select" value={inviteForm.role} onChange={(e) => setInviteForm({ ...inviteForm, role: e.target.value })}><option>开发者</option><option>评测员</option><option>只读成员</option><option>平台管理员</option></select></div>
        <div className="callout info"><Icon name="users" /><div>邀请链接 7 天内有效。成员加入后会收到权限说明。</div></div>
      </Modal>

      <Modal open={keyOpen} onClose={() => setKeyOpen(false)} title="创建 API 密钥" sub="密钥只会完整展示一次" footer={<><span className="spacer" /><Button onClick={() => setKeyOpen(false)}>取消</Button><Button kind="primary" disabled={!keyForm.name.trim()} onClick={() => createKey.mutate(keyForm)}>创建密钥</Button></>}>
        <div className="field"><label>名称</label><input className="input" value={keyForm.name} onChange={(e) => setKeyForm({ ...keyForm, name: e.target.value })} placeholder="例如：生产运行时" /></div>
        <div className="field"><label>权限范围</label><select className="select" value={keyForm.scope} onChange={(e) => setKeyForm({ ...keyForm, scope: e.target.value })}><option>runtime:read</option><option>runtime:read,write</option><option>admin</option></select></div>
      </Modal>

      <Modal open={!!createdKey} onClose={() => setCreatedKey(null)} title="密钥已创建" sub="请立即复制并妥善保存，关闭后不再完整展示" footer={<><span className="spacer" /><Button kind="primary" onClick={() => setCreatedKey(null)}>我已保存</Button></>}>
        <div className="key-reveal"><span>{createdKey}</span><Button size="sm" onClick={() => { navigator.clipboard?.writeText(createdKey ?? ''); toast('密钥已复制') }}><Icon name="copy" />复制</Button></div>
      </Modal>
    </>
  )
}
