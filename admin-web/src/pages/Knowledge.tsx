import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Agent, type Dataset, type Document, type RetrievalResult } from '../api'
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

const formatBytes = (bytes: number) => bytes >= 1024 * 1024
  ? `${(bytes / 1024 / 1024).toFixed(1)} MB`
  : `${Math.max(1, Math.round(bytes / 1024))} KB`

const EMBEDDING = ['bge-m3', 'text-embedding-3-large', 'qwen3-embedding', 'bge-large-zh']
const PARSERS = ['默认解析', '自动识别', 'PDF 结构化解析', 'Word 解析', 'Markdown 解析']
const CHUNKERS = ['按标题层级', '固定长度', '语义分块', '按段落']
const RETRIEVAL = [
  { value: 'hybrid', label: '混合检索' },
  { value: 'vector', label: '向量检索' },
  { value: 'keyword', label: '关键词检索' },
]

export function KnowledgePage() {
  const queryClient = useQueryClient()
  const [datasetId, setDatasetId] = useState<string>()
  const [tab, setTab] = useState('basic')
  const [query, setQuery] = useState('')
  const [result, setResult] = useState<RetrievalResult | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [configDrawerOpen, setConfigDrawerOpen] = useState(false)
  const [webOpen, setWebOpen] = useState(false)
  const [webForm, setWebForm] = useState({ url: '', name: '', cookie: '' })
  const [uploadFile, setUploadFile] = useState<File | null>(null)
  const [selectedDoc, setSelectedDoc] = useState<Document | null>(null)
  const [versionsDoc, setVersionsDoc] = useState<Document | null>(null)
  const [replaceDoc, setReplaceDoc] = useState<Document | null>(null)
  const [draft, setDraft] = useState<Partial<Dataset>>({})
  const [createForm, setCreateForm] = useState({ name: '', owner: '顾先路', embeddingModel: 'bge-m3', indexType: 'HNSW', retrievalMode: 'hybrid' })
  const fileRef = useRef<HTMLInputElement>(null)
  const replaceRef = useRef<HTMLInputElement>(null)

  const datasets = useQuery({ queryKey: ['datasets'], queryFn: () => api.get<Dataset[]>('/datasets') })
  const agents = useQuery({ queryKey: ['agents'], queryFn: () => api.get<Agent[]>('/agents') })
  const selected = datasetId ?? datasets.data?.find((d) => d.id === 'kb-refund')?.id ?? datasets.data?.[0]?.id
  const ds = datasets.data?.find((d) => d.id === selected)
  const docs = useQuery({
    queryKey: ['documents', selected],
    queryFn: () => api.get<Document[]>(`/datasets/${selected}/documents`),
    enabled: !!selected,
    refetchInterval: 3000,
  })

  useEffect(() => {
    if (ds) setDraft({
      parser: ds.parser, chunkStrategy: ds.chunkStrategy, chunkSize: ds.chunkSize, overlap: ds.overlap,
      embeddingModel: ds.embeddingModel, indexType: ds.indexType, metric: ds.metric,
      retrievalMode: ds.retrievalMode, topK: ds.topK, threshold: ds.threshold, rerank: ds.rerank, authAgents: ds.authAgents,
    })
  }, [ds?.id])

  const refreshAll = () => {
    queryClient.invalidateQueries({ queryKey: ['datasets'] })
    queryClient.invalidateQueries({ queryKey: ['documents', selected] })
  }

  const saveConfig = useMutation({
    mutationFn: (patch: Partial<Dataset>) => api.put<Dataset>(`/datasets/${selected}`, { ...draft, ...patch }),
    onSuccess: () => { toast('配置已保存，如需生效请重建索引'); refreshAll() },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const saveChunkConfig = useMutation({
    mutationFn: () => api.put<Dataset>(`/datasets/${selected}`, {
      parser: draft.parser,
      chunkStrategy: draft.chunkStrategy,
      chunkSize: draft.chunkSize,
      overlap: draft.overlap,
    }),
    onSuccess: () => { toast('解析与切分配置已保存，上传时将使用新配置'); setConfigDrawerOpen(false); refreshAll() },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const reindex = useMutation({
    mutationFn: () => api.post<Dataset>(`/datasets/${selected}/reindex`),
    onSuccess: () => { toast('索引已重建'); refreshAll() },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const retrieval = useMutation({
    mutationFn: () => api.post<RetrievalResult>(`/datasets/${selected}/retrieval-test`, { query }),
    onSuccess: (r) => { setResult(r); toast(`命中 ${r.hits.length} 条`) },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const createDataset = useMutation({
    mutationFn: () => api.post<Dataset>('/datasets', createForm),
    onSuccess: (d) => { toast('数据集已创建'); setCreateOpen(false); setDatasetId(d.id); refreshAll() },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const upload = useMutation({
    mutationFn: () => {
      if (!uploadFile) throw new Error('请先选择文件')
      const form = new FormData()
      form.append('file', uploadFile)
      return api.postForm<Document>(`/datasets/${selected}/documents:upload`, form)
    },
    onSuccess: () => { toast('文档已上传'); setUploadOpen(false); setUploadFile(null); refreshAll() },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const removeDoc = useMutation({
    mutationFn: (id: string) => api.del<void>(`/documents/${id}`),
    onSuccess: () => { toast('文档已删除'); refreshAll() },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const addWeb = useMutation({
    mutationFn: () => api.post<Document>(`/datasets/${selected}/documents:web`, webForm),
    onSuccess: () => { toast('网页已加入知识库，正在解析索引'); setWebOpen(false); setWebForm({ url: '', name: '', cookie: '' }); refreshAll() },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const replace = useMutation({
    mutationFn: ({ doc, file }: { doc: Document; file: File }) => {
      const form = new FormData()
      form.append('file', file)
      return api.putForm<Document>(`/documents/${doc.id}:upload`, form)
    },
    onSuccess: () => { toast('文档已替换，新版本正在索引'); setReplaceDoc(null); refreshAll() },
    onError: (e: Error) => toast(e.message, 'err'),
  })
  const rollback = useMutation({
    mutationFn: ({ doc, version }: { doc: Document; version: number }) => api.post<Document>(`/documents/${doc.id}/rollback`, { version }),
    onSuccess: () => { toast('已回滚到指定版本'); setVersionsDoc(null); refreshAll() },
    onError: (e: Error) => toast(e.message, 'err'),
  })

  const docList = docs.data ?? []
  const totalDocs = datasets.data?.reduce((s, d) => s + d.docs, 0) ?? 0
  const totalChunks = datasets.data?.reduce((s, d) => s + d.chunks, 0) ?? 0
  const authAgents = draft.authAgents ?? ds?.authAgents ?? []

  const chooseFile = (file: File | null) => {
    if (!file) return
    if (file.size > 50 * 1024 * 1024) { toast('文件不能超过 50 MB', 'err'); return }
    setUploadFile(file)
  }

  return (
    <>
      <PageHead title="知识库" sub="管理数据集、文档与向量索引；解析、分块、检索参数和智能体授权都可以在页面配置。">
        <Button onClick={() => setCreateOpen(true)}><Icon name="plus" />新建数据集</Button>
        <Button onClick={() => setUploadOpen(true)} disabled={!selected}><Icon name="upload" />上传文档</Button>
        <Button onClick={() => setWebOpen(true)} disabled={!selected}><Icon name="knowledge" />添加网页</Button>
        <Button onClick={() => reindex.mutate()} disabled={!selected}><Icon name="refresh" />重建索引</Button>
      </PageHead>

      <div className="grid-4">
        <div className="panel stat"><div className="lb">数据集</div><div className="vl">{datasets.data?.length ?? 0}</div><div className="delta"><span>按业务域隔离</span></div></div>
        <div className="panel stat"><div className="lb">文档</div><div className="vl">{totalDocs}</div><div className="delta"><span>含历史版本</span></div></div>
        <div className="panel stat"><div className="lb">分块</div><div className="vl">{totalChunks.toLocaleString()}</div><div className="delta"><span>当前索引</span></div></div>
        <div className="panel stat"><div className="lb">待重建</div><div className="vl">{(datasets.data ?? []).filter((d) => d.needsReindex).length}</div><div className="delta"><Badge tone="warn">配置变更后需重建</Badge></div></div>
      </div>

      <div className="grid-2 mt-3">
        <Panel title="数据集" sub={`${datasets.data?.length ?? 0} 个数据集 · 共 ${totalDocs} 篇文档`} tools={<Button size="sm" kind="primary" onClick={() => setCreateOpen(true)}><Icon name="plus" />新建</Button>} bodyStyle={{ display: 'grid', gap: 8, paddingTop: 10 }}>
          {(datasets.data ?? []).map((d) => (
            <div key={d.id} className={`dataset-item ${d.id === selected ? 'active' : ''}`} onClick={() => { setDatasetId(d.id); setResult(null); setTab('basic') }}>
              <div className="ds-icon"><Icon name="database" /></div>
              <div style={{ minWidth: 0, flex: 1 }}>
                <b>{d.name}</b>
                <small>{d.docs} 篇文档 · {d.chunks.toLocaleString()} 个分块 · {d.owner}</small>
                <small style={{ marginTop: 3 }}>{d.embeddingModel} · {d.indexType} · 授权 {d.authAgents.length} 个智能体</small>
              </div>
              <div className="ds-stat"><StatusBadge status={d.needsReindex ? 'pending' : d.status} /><div style={{ marginTop: 3 }}>{d.updated}</div></div>
            </div>
          ))}
        </Panel>

        <Panel
          title={ds?.name ?? '数据集配置'}
          sub={ds ? `${ds.description || '解析、分块、索引与检索参数'} · ${ds.updated}` : '选择左侧数据集'}
          tools={<><Button size="sm" onClick={() => saveConfig.mutate({})} disabled={!ds}>保存配置</Button><Button size="sm" kind="ghost" onClick={() => reindex.mutate()} disabled={!ds}>重建索引</Button></>}
        >
          {ds ? (
            <>
              <Tabs active={tab} onChange={setTab} items={[{ key: 'basic', label: '基础' }, { key: 'parse', label: '解析与分块' }, { key: 'index', label: '向量索引' }, { key: 'retrieval', label: '检索参数' }, { key: 'auth', label: '授权' }]} />
              {tab === 'basic' && (
                <>
                  <div className="field-grid">
                    <div className="field"><label>数据集名称</label><input className="input" value={ds.name} readOnly /></div>
                    <div className="field"><label>负责人</label><input className="input" value={ds.owner} readOnly /></div>
                  </div>
                  <div className="field"><label>描述</label><textarea className="input" rows={2} value={ds.description} readOnly /></div>
                  <div className="kv"><dt>Embedding</dt><dd>{ds.embeddingModel}</dd><dt>索引类型</dt><dd>{ds.indexType}</dd><dt>Metric</dt><dd>{ds.metric}</dd><dt>检索模式</dt><dd>{RETRIEVAL.find((x) => x.value === ds.retrievalMode)?.label ?? ds.retrievalMode}</dd></div>
                </>
              )}
              {tab === 'parse' && (
                <>
                  <div className="field"><label>解析器</label><select className="select" value={draft.parser ?? ds.parser} onChange={(e) => setDraft({ ...draft, parser: e.target.value })}>{PARSERS.map((p) => <option key={p}>{p}</option>)}</select></div>
                  <div className="field"><label>分块策略</label><select className="select" value={draft.chunkStrategy ?? ds.chunkStrategy} onChange={(e) => setDraft({ ...draft, chunkStrategy: e.target.value })}>{CHUNKERS.map((p) => <option key={p}>{p}</option>)}</select></div>
                  <div className="field-grid">
                    <div className="field"><label>分块大小</label><input className="input" type="number" value={draft.chunkSize ?? ds.chunkSize} onChange={(e) => setDraft({ ...draft, chunkSize: Number(e.target.value) })} /><div className="hint">建议 256–1024 tokens</div></div>
                    <div className="field"><label>重叠长度</label><input className="input" type="number" value={draft.overlap ?? ds.overlap} onChange={(e) => setDraft({ ...draft, overlap: Number(e.target.value) })} /><div className="hint">避免跨块语义断裂</div></div>
                  </div>
                  <div className="callout info"><Icon name="help" /><div>修改解析或分块参数后，已有文档需要重建索引才会生效。</div></div>
                </>
              )}
              {tab === 'index' && (
                <>
                  <div className="field"><label>Embedding 模型</label><select className="select" value={draft.embeddingModel ?? ds.embeddingModel} onChange={(e) => setDraft({ ...draft, embeddingModel: e.target.value })}>{EMBEDDING.map((m) => <option key={m}>{m}</option>)}</select></div>
                  <div className="field-grid">
                    <div className="field"><label>索引类型</label><select className="select" value={draft.indexType ?? ds.indexType} onChange={(e) => setDraft({ ...draft, indexType: e.target.value })}>{['HNSW', 'IVF_FLAT', 'FLAT'].map((m) => <option key={m}>{m}</option>)}</select></div>
                    <div className="field"><label>距离度量</label><select className="select" value={draft.metric ?? ds.metric} onChange={(e) => setDraft({ ...draft, metric: e.target.value })}>{['COSINE', 'L2', 'IP'].map((m) => <option key={m}>{m}</option>)}</select></div>
                  </div>
                  <div className="kv"><dt>当前分块</dt><dd>{ds.chunks.toLocaleString()}</dd><dt>索引状态</dt><dd><StatusBadge status={ds.needsReindex ? 'pending' : ds.status} /></dd><dt>更新时间</dt><dd>{ds.updated}</dd></div>
                  <Button className="mt-3" onClick={() => reindex.mutate()}><Icon name="refresh" />重建索引</Button>
                </>
              )}
              {tab === 'retrieval' && (
                <>
                  <div className="field"><label>检索模式</label><select className="select" value={draft.retrievalMode ?? ds.retrievalMode} onChange={(e) => setDraft({ ...draft, retrievalMode: e.target.value })}>{RETRIEVAL.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}</select></div>
                  <div className="field-grid">
                    <div className="field"><label>Top K</label><input className="input" type="number" min={1} max={20} value={draft.topK ?? ds.topK} onChange={(e) => setDraft({ ...draft, topK: Number(e.target.value) })} /></div>
                    <div className="field"><label>相似度阈值</label><input className="input" type="number" min={0} max={1} step={0.01} value={draft.threshold ?? ds.threshold} onChange={(e) => setDraft({ ...draft, threshold: Number(e.target.value) })} /></div>
                  </div>
                  <div className="field"><label>Rerank</label><label className="checkline"><input type="checkbox" checked={draft.rerank ?? ds.rerank} onChange={(e) => setDraft({ ...draft, rerank: e.target.checked })} />启用 Rerank（bge-reranker-v2-m3）</label></div>
                  <div className="field"><label>检索测试</label><div className="row"><input className="input" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="例如：已签收的商品还能退款吗？" onKeyDown={(e) => { if (e.key === 'Enter') retrieval.mutate() }} /><Button kind="primary" onClick={() => retrieval.mutate()} disabled={!query.trim()}>运行检索</Button></div></div>
                  {result && (
                    <div className="mt-3">
                      <div className="muted" style={{ marginBottom: 7 }}>命中 {result.hits.length} 条 · {result.ms} ms · {result.mode} · 阈值 {result.threshold}</div>
                      {result.hits.map((h, i) => <div key={i} className="retrieval-hit"><div className="row"><span className="badge neutral">{h.source}</span><span className="muted">分块 #{h.chunk}</span><span className="spacer" /><b>{h.score.toFixed(2)}</b></div><p style={{ margin: '6px 0 0', fontSize: 12.5, lineHeight: 1.65 }}>{h.text}</p></div>)}
                    </div>
                  )}
                </>
              )}
              {tab === 'auth' && (
                <>
                  <div className="field"><label>允许访问的智能体</label><div className="node-list">{(agents.data ?? []).map((a) => <label className="node-card" key={a.id}><input type="checkbox" checked={authAgents.includes(a.id)} onChange={(e) => setDraft({ ...draft, authAgents: e.target.checked ? [...authAgents, a.id] : authAgents.filter((x) => x !== a.id) })} /><div><b>{a.name}</b><div className="muted">{a.code} · {a.team}</div></div></label>)}</div></div>
                  <Button kind="primary" onClick={() => saveConfig.mutate({ authAgents: authAgents })}>保存授权</Button>
                </>
              )}
            </>
          ) : <div className="empty"><Icon name="knowledge" /><b>请选择数据集</b>从左侧选择一个数据集查看配置。</div>}
        </Panel>
      </div>

      <Panel className="mt-3" title={`${ds?.name ?? '数据集'} · 文档`} sub="支持上传、替换、删除和版本回滚" tools={<><Button size="sm" onClick={() => setUploadOpen(true)} disabled={!selected}><Icon name="upload" />上传</Button></>} noBody>
        <div className="table-wrap">
          <table className="data">
            <thead><tr><th>文档</th><th>版本</th><th className="num">分块</th><th>大小</th><th>状态</th><th>更新时间</th><th style={{ textAlign: 'right' }}>操作</th></tr></thead>
            <tbody>
              {docList.map((d) => (
                <tr key={d.id} className="clickable" onClick={() => setSelectedDoc(d)}>
                  <td><b>{d.name}</b>{d.sourceType === 'WEB' && <div className="sub" style={{ maxWidth: 360, overflow: 'hidden', textOverflow: 'ellipsis' }}>网页 · {d.sourceUrl}</div>}</td><td>v{d.version}</td><td className="num">{d.chunks}</td><td>{d.size}</td><td><StatusBadge status={d.status} /></td><td className="muted">{d.updated}</td>
                  <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }} onClick={(e) => e.stopPropagation()}>
                    <Button size="sm" kind="ghost" onClick={() => setReplaceDoc(d)}>替换</Button>
                    <Button size="sm" kind="ghost" onClick={() => setVersionsDoc(d)}>版本</Button>
                    <Button size="sm" kind="danger" onClick={() => { if (window.confirm('确认删除该文档？')) removeDoc.mutate(d.id) }}>删除</Button>
                  </td>
                </tr>
              ))}
              {!docList.length && <tr><td colSpan={7}><div className="empty"><Icon name="knowledge" /><b>暂无文档</b>上传 PDF、Markdown 或 Word 文档开始构建知识库。</div></td></tr>}
            </tbody>
          </table>
        </div>
      </Panel>

      <Modal open={webOpen} onClose={() => setWebOpen(false)} title="添加网页知识" sub="抓取 Wiki、知识库或帮助中心页面正文，解析后写入当前数据集" footer={<><span className="spacer" /><Button onClick={() => setWebOpen(false)}>取消</Button><Button kind="primary" disabled={!webForm.url.trim() || addWeb.isPending} onClick={() => addWeb.mutate()}>抓取并索引</Button></>}>
        <div className="field"><label>网页地址</label><input className="input" value={webForm.url} onChange={(e) => setWebForm({ ...webForm, url: e.target.value })} placeholder="https://wiki.example.com/pages/refund-policy" /><div className="hint">支持 Wiki、Confluence、帮助中心等公开 HTTP/HTTPS 页面；默认禁止内网地址。</div></div>
        <div className="field"><label>文档名称（可选）</label><input className="input" value={webForm.name} onChange={(e) => setWebForm({ ...webForm, name: e.target.value })} placeholder="留空则使用网页标题" /></div>
        <div className="field"><label>访问 Cookie（可选）</label><input className="input" value={webForm.cookie} onChange={(e) => setWebForm({ ...webForm, cookie: e.target.value })} placeholder="仅用于本次抓取，不保存到文档" /><div className="hint">内部 Wiki / Confluence 可在浏览器复制 Cookie 后粘贴；生产建议使用服务账号。</div></div>
        <div className="callout info"><Icon name="knowledge" /><div>系统会提取页面正文、按数据集切分配置生成分块，并写入向量索引。</div></div>
      </Modal>

      <Modal open={createOpen} onClose={() => setCreateOpen(false)} title="新建数据集" sub="创建后可继续配置解析、索引和检索参数" footer={<><span className="spacer" /><Button onClick={() => setCreateOpen(false)}>取消</Button><Button kind="primary" onClick={() => createDataset.mutate()}>创建数据集</Button></>}>
        <div className="field-grid">
          <div className="field"><label>名称</label><input className="input" value={createForm.name} onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })} placeholder="售后政策库" /></div>
          <div className="field"><label>负责人</label><input className="input" value={createForm.owner} onChange={(e) => setCreateForm({ ...createForm, owner: e.target.value })} /></div>
          <div className="field"><label>Embedding 模型</label><select className="select" value={createForm.embeddingModel} onChange={(e) => setCreateForm({ ...createForm, embeddingModel: e.target.value })}>{EMBEDDING.map((m) => <option key={m}>{m}</option>)}</select></div>
          <div className="field"><label>索引类型</label><select className="select" value={createForm.indexType} onChange={(e) => setCreateForm({ ...createForm, indexType: e.target.value })}>{['HNSW', 'IVF_FLAT', 'FLAT'].map((m) => <option key={m}>{m}</option>)}</select></div>
        </div>
        <div className="field"><label>检索模式</label><select className="select" value={createForm.retrievalMode} onChange={(e) => setCreateForm({ ...createForm, retrievalMode: e.target.value })}>{RETRIEVAL.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}</select></div>
      </Modal>

      <Modal open={uploadOpen} onClose={() => { setUploadOpen(false); setUploadFile(null) }} title={`上传文档 · ${ds?.name ?? ''}`} sub="支持 PDF、Markdown、Word、TXT，单文件不超过 50 MB" footer={<><span className="spacer" /><Button onClick={() => { setUploadOpen(false); setUploadFile(null) }}>取消</Button><Button kind="primary" onClick={() => upload.mutate()} disabled={!uploadFile || upload.isPending}>开始上传</Button></>}>
        <input ref={fileRef} type="file" accept=".pdf,.md,.doc,.docx,.txt" hidden onChange={(e) => chooseFile(e.target.files?.[0] ?? null)} />
        <div className="upload-zone" style={{ cursor: 'pointer' }} onClick={() => fileRef.current?.click()} onDragOver={(e) => e.preventDefault()} onDrop={(e) => { e.preventDefault(); chooseFile(e.dataTransfer.files?.[0] ?? null) }}>
          <Icon name="upload" />
          <b>{uploadFile ? uploadFile.name : '点击选择文件，或拖拽到此处'}</b>
          <small>{uploadFile ? `${formatBytes(uploadFile.size)} · 已选择` : 'PDF / Markdown / Word / TXT'}</small>
        </div>
        <div className="panel" style={{ boxShadow: 'none', marginTop: 12 }}>
          <div className="panel-head" style={{ padding: '12px 14px 0' }}>
            <div>
              <h2 className="panel-title">继承数据集解析与切分配置</h2>
              <p className="panel-sub">上传后按以下配置解析、切分并写入索引</p>
            </div>
            <span className="badge info"><span className="bd" />{ds?.name}</span>
          </div>
          <div className="panel-body" style={{ padding: '10px 14px 14px' }}>
            <div className="kv">
              <dt>解析器</dt><dd>{ds?.parser ?? '—'}</dd>
              <dt>分块策略</dt><dd>{ds?.chunkStrategy ?? '—'}</dd>
              <dt>分块大小</dt><dd>{ds?.chunkSize ?? '—'} tokens</dd>
              <dt>重叠长度</dt><dd>{ds?.overlap ?? '—'} tokens</dd>
            </div>
            <div className="row" style={{ marginTop: 10 }}>
              <span className="muted">修改后仅影响本次及后续上传的文档</span>
              <span className="spacer" />
              <Button size="sm" kind="ghost" onClick={() => setConfigDrawerOpen(true)}><Icon name="settings" />调整</Button>
            </div>
          </div>
        </div>
        <div className="callout info mt-3"><Icon name="help" /><div>当前为演示环境，文件内容不会真正保存，仅记录文件名、大小并生成分块统计。</div></div>
      </Modal>

      <Drawer
        open={configDrawerOpen}
        onClose={() => setConfigDrawerOpen(false)}
        width={620}
        zIndex={80}
      >
        <div className="drawer-head">
          <div className="avatar" style={{ width: 36, height: 36, borderRadius: 10, background: 'var(--pine-wash)', color: 'var(--pine-deep)' }}><Icon name="settings" /></div>
          <div className="dh-main"><div className="drawer-title">解析与切分配置</div><div className="drawer-sub"><span>{ds?.name}</span><span>·</span><span>上传文档时继承</span></div></div>
          <button className="icon-btn" onClick={() => setConfigDrawerOpen(false)}><Icon name="x" /></button>
        </div>
        <div className="drawer-body">
          <div className="callout info"><Icon name="help" /><div>这里修改的是数据集级配置。保存后，本次上传会立即使用新配置；已上传文档需重建索引才会按新配置生效。</div></div>
          <div className="section-head"><h2>解析</h2></div>
          <div className="field"><label>解析器</label><select className="select" value={draft.parser ?? ds?.parser ?? ''} onChange={(e) => setDraft({ ...draft, parser: e.target.value })}>{PARSERS.map((p) => <option key={p}>{p}</option>)}</select></div>
          <div className="section-head"><h2>切分</h2></div>
          <div className="field"><label>分块策略</label><select className="select" value={draft.chunkStrategy ?? ds?.chunkStrategy ?? ''} onChange={(e) => setDraft({ ...draft, chunkStrategy: e.target.value })}>{CHUNKERS.map((p) => <option key={p}>{p}</option>)}</select></div>
          <div className="field-grid">
            <div className="field"><label>分块大小（tokens）</label><input className="input" type="number" min={128} max={4096} value={draft.chunkSize ?? ds?.chunkSize ?? 512} onChange={(e) => setDraft({ ...draft, chunkSize: Number(e.target.value) })} /><div className="hint">建议 256–1024 tokens</div></div>
            <div className="field"><label>重叠长度（tokens）</label><input className="input" type="number" min={0} max={1024} value={draft.overlap ?? ds?.overlap ?? 64} onChange={(e) => setDraft({ ...draft, overlap: Number(e.target.value) })} /><div className="hint">避免跨块语义断裂</div></div>
          </div>
          <div className="callout mt-3"><Icon name="alert-triangle" /><div>修改后该数据集会被标记为“待重建”，重建索引完成后线上检索才会切换。</div></div>
        </div>
        <div className="drawer-foot">
          <Button onClick={() => setConfigDrawerOpen(false)}>取消</Button>
          <span className="spacer" />
          <Button kind="primary" onClick={() => saveChunkConfig.mutate()} disabled={saveChunkConfig.isPending}>保存并用于本次上传</Button>
        </div>
      </Drawer>

      <Drawer open={!!selectedDoc} onClose={() => setSelectedDoc(null)} width={680}>
        {selectedDoc && (
          <>
            <div className="drawer-head"><div className="avatar" style={{ width: 36, height: 36, borderRadius: 10, background: 'var(--pine-wash)', color: 'var(--pine-deep)' }}><Icon name="knowledge" /></div><div className="dh-main"><div className="drawer-title">{selectedDoc.name}</div><div className="drawer-sub"><span>v{selectedDoc.version}</span><span>·</span><span>{selectedDoc.chunks} 个分块</span><span>·</span><span>{selectedDoc.size}</span></div></div><button className="icon-btn" onClick={() => setSelectedDoc(null)}><Icon name="x" /></button></div>
            <div className="drawer-body">
              <div className="kv"><dt>状态</dt><dd><StatusBadge status={selectedDoc.status} /></dd><dt>来源</dt><dd>{selectedDoc.sourceType === 'WEB' ? '网页' : '文件'}</dd>{selectedDoc.sourceType === 'WEB' && <><dt>网页地址</dt><dd className="mono" style={{ fontSize: 11, wordBreak: 'break-all' }}>{selectedDoc.sourceUrl}</dd></>}<dt>更新时间</dt><dd>{selectedDoc.updated}</dd><dt>所属数据集</dt><dd>{ds?.name}</dd><dt>解析器</dt><dd>{ds?.parser}</dd><dt>分块策略</dt><dd>{ds?.chunkStrategy} · {ds?.chunkSize} tokens</dd></div>
              <div className="section-head"><h2>分块预览</h2></div>
              <div className="panel" style={{ boxShadow: 'none' }}><div className="panel-body"><p style={{ fontSize: 12.5, lineHeight: 1.75, margin: 0 }}>#1 退款政策适用范围……<br />#2 已签收商品的退款条件……<br />#3 跨境订单与特殊品类……</p></div></div>
            </div>
            <div className="drawer-foot"><span className="spacer" /><Button onClick={() => setVersionsDoc(selectedDoc)}><Icon name="git" />版本记录</Button><Button kind="primary" onClick={() => setReplaceDoc(selectedDoc)}>替换文档</Button></div>
          </>
        )}
      </Drawer>

      <Modal open={!!versionsDoc} onClose={() => setVersionsDoc(null)} title={`版本记录 · ${versionsDoc?.name ?? ''}`} sub="替换文档会生成新版本，可回滚到历史版本">
        {versionsDoc && [0, 1, 2].map((i) => {
          const v = Math.max(1, versionsDoc.version - i)
          return <div key={v} className={`step-item ${i === 0 ? 'current' : 'done'}`}><div className="step-dot"><Icon name={i === 0 ? 'git' : 'check'} /></div><div className="step-title">v{v} {i === 0 && <Badge tone="good">当前</Badge>}</div><div className="step-meta">{i === 0 ? '刚刚' : `${i} 天前`} · {versionsDoc.chunks} 个分块 · 顾先路</div>{i > 0 && <Button size="sm" kind="ghost" className="mt-2" onClick={() => rollback.mutate({ doc: versionsDoc, version: v })}>回滚至此</Button>}</div>
        })}
      </Modal>

      <input ref={replaceRef} type="file" accept=".pdf,.md,.doc,.docx,.txt" hidden onChange={(e) => { const f = e.target.files?.[0]; if (f && replaceDoc) replace.mutate({ doc: replaceDoc, file: f }) }} />
      <Modal open={!!replaceDoc} onClose={() => setReplaceDoc(null)} title={`替换文档 · ${replaceDoc?.name ?? ''}`} sub="上传新文件后会生成新版本，旧版本保留" footer={<><span className="spacer" /><Button onClick={() => setReplaceDoc(null)}>取消</Button><Button kind="primary" onClick={() => replaceRef.current?.click()}>选择文件并替换</Button></>}>
        <div className="callout warn"><Icon name="alert-triangle" /><div>替换后会重新解析、分块并重建该文档索引；线上检索在索引完成前仍使用旧版本。</div></div>
      </Modal>
    </>
  )
}
