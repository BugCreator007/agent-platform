import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Icon } from './icons'

/* ------------------------------------------------------------------ */
/* 基础展示组件：与交互图最终稿保持同一套设计语言                      */
/* ------------------------------------------------------------------ */

export function PageHead({
  title,
  sub,
  children,
}: {
  title: string
  sub?: React.ReactNode
  children?: React.ReactNode
}) {
  return (
    <div className="page-head">
      <div>
        <h1 className="page-title">{title}</h1>
        {sub && <p className="page-sub">{sub}</p>}
      </div>
      {children && <div className="page-actions">{children}</div>}
    </div>
  )
}

export function Panel({
  title,
  sub,
  tools,
  children,
  footer,
  className = '',
  bodyStyle,
  noBody,
}: {
  title?: React.ReactNode
  sub?: React.ReactNode
  tools?: React.ReactNode
  children?: React.ReactNode
  footer?: React.ReactNode
  className?: string
  bodyStyle?: React.CSSProperties
  noBody?: boolean
}) {
  return (
    <section className={`panel ${className}`}>
      {(title || tools) && (
        <div className="panel-head">
          <div>
            {title && <h2 className="panel-title">{title}</h2>}
            {sub && <p className="panel-sub">{sub}</p>}
          </div>
          {tools && <div className="panel-tools">{tools}</div>}
        </div>
      )}
      {children && (noBody ? children : <div className="panel-body" style={bodyStyle}>{children}</div>)}
      {footer && <div className="panel-foot">{footer}</div>}
    </section>
  )
}

export function Button({
  children,
  kind = 'default',
  size,
  className = '',
  ...rest
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  kind?: 'default' | 'primary' | 'ghost' | 'danger'
  size?: 'sm'
}) {
  return (
    <button
      type={rest.type ?? 'button'}
      className={`btn ${kind !== 'default' ? kind : ''} ${size === 'sm' ? 'sm' : ''} ${className}`.trim()}
      {...rest}
    >
      {children}
    </button>
  )
}

const STATUS_MAP: Record<string, [string, string]> = {
  running: ['good', '运行中'],
  grayscale: ['warn', '灰度中'],
  paused: ['neutral', '已暂停'],
  draft: ['outline', '草稿'],
  success: ['good', '成功'],
  failed: ['danger', '失败'],
  timeout: ['warn', '超时'],
  active: ['danger', '待处理'],
  ack: ['warn', '已认领'],
  resolved: ['good', '已恢复'],
  ready: ['good', '已索引'],
  indexing: ['warn', '索引中'],
  healthy: ['good', '正常'],
  degraded: ['warn', '降级'],
  down: ['danger', '不可用'],
  canary: ['warn', '灰度中'],
  approve: ['info', '待审批'],
  live: ['good', '全量'],
  rollback: ['danger', '已回滚'],
  published: ['good', '已发布'],
  suspended: ['warn', '已暂停'],
  pending: ['warn', '待处理'],
  empty: ['neutral', '空数据集'],
  ok: ['good', '成功'],
  skip: ['neutral', '跳过'],
  RUNNING: ['info', '运行中'],
  WAITING_APPROVAL: ['warn', '待审批'],
  SUCCEEDED: ['good', '成功'],
  FAILED: ['danger', '失败'],
  CANCELLED: ['neutral', '已取消'],
  SUCCESS: ['good', '成功'],
  FAIL: ['danger', '失败'],
}

export function statusLabel(status: string) {
  return STATUS_MAP[status]?.[1] ?? status
}

export function StatusBadge({ status }: { status: string }) {
  const [tone, text] = STATUS_MAP[status] ?? ['neutral', status]
  return (
    <span className={`badge ${tone}`}>
      <span className="bd" />
      {text}
    </span>
  )
}

export function Badge({
  tone = 'neutral',
  children,
  dot = true,
}: {
  tone?: 'good' | 'warn' | 'danger' | 'info' | 'neutral' | 'outline'
  children: React.ReactNode
  dot?: boolean
}) {
  return (
    <span className={`badge ${tone}`}>
      {dot && <span className="bd" />}
      {children}
    </span>
  )
}

export function Meter({ value, tone }: { value: number; tone?: string }) {
  return (
    <span className="meter">
      <i style={{ width: `${Math.max(0, Math.min(100, value))}%` }} className={tone} />
    </span>
  )
}

export function Avatar({
  text,
  tone,
  size,
}: {
  text: string
  tone?: string
  size?: number
}) {
  return (
    <div
      className={`avatar tone-${tone ?? 'default'}`}
      style={{ width: size, height: size, fontSize: size ? size * 0.42 : undefined }}
    >
      {text}
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* 抽屉、弹窗、Toast                                                  */
/* ------------------------------------------------------------------ */

export function Drawer({
  open,
  onClose,
  children,
  width,
  zIndex,
}: {
  open: boolean
  onClose: () => void
  children: React.ReactNode
  width?: number
  zIndex?: number
}) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null
  return createPortal(
    <>
      <div className="overlay open" onClick={onClose} style={zIndex ? { zIndex } : undefined} />
      <aside className="drawer open" style={{ width: width ? `min(${width}px, 94vw)` : undefined, zIndex: zIndex ? zIndex + 1 : undefined }} role="dialog" aria-modal="true">
        {children}
      </aside>
    </>,
    document.body,
  )
}

export function Modal({
  open,
  onClose,
  title,
  sub,
  children,
  footer,
  wide,
}: {
  open: boolean
  onClose: () => void
  title: React.ReactNode
  sub?: React.ReactNode
  children: React.ReactNode
  footer?: React.ReactNode
  wide?: boolean
}) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !document.querySelector('.drawer.open')) onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null
  return createPortal(
    <div className="modal-scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div className={`modal ${wide ? 'wide' : ''}`} role="dialog" aria-modal="true">
        <div className="modal-head">
          <div>
            <div className="modal-title">{title}</div>
            {sub && <div className="modal-sub">{sub}</div>}
          </div>
          <button className="icon-btn" onClick={onClose} aria-label="关闭">
            <Icon name="x" />
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>,
    document.body,
  )
}

type ToastItem = { id: number; msg: string; type: 'ok' | 'err' }

let toastId = 0

export function toast(msg: string, type: 'ok' | 'err' = 'ok') {
  window.dispatchEvent(new CustomEvent('app-toast', { detail: { msg, type, id: ++toastId } }))
}

export function ToastHost() {
  const [items, setItems] = useState<ToastItem[]>([])
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent).detail as ToastItem
      setItems((prev) => [...prev, detail])
      window.setTimeout(() => {
        setItems((prev) => prev.filter((x) => x.id !== detail.id))
      }, 3200)
    }
    window.addEventListener('app-toast', handler)
    return () => window.removeEventListener('app-toast', handler)
  }, [])
  return (
    <div className="toasts" aria-live="polite">
      {items.map((t) => (
        <div key={t.id} className={`toast ${t.type === 'err' ? 'err' : ''}`}>
          <Icon name={t.type === 'err' ? 'x-circle' : 'check-circle'} />
          <span>{t.msg}</span>
        </div>
      ))}
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* 轻量图表                                                           */
/* ------------------------------------------------------------------ */

export function Sparkline({
  values,
  color = '#0E4F3C',
  width = 120,
  height = 34,
}: {
  values: number[]
  color?: string
  width?: number
  height?: number
}) {
  const points = useMemo(() => {
    const min = Math.min(...values)
    const max = Math.max(...values)
    const span = max - min || 1
    return values.map((v, i) => [
      (i / Math.max(1, values.length - 1)) * width,
      height - 4 - ((v - min) / span) * (height - 9),
    ])
  }, [values, width, height])
  const d = points.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join(' ')
  const last = points[points.length - 1]
  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} aria-hidden="true">
      <path d={d} fill="none" stroke={color} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" opacity=".65" />
      {last && <circle className="spark-cap" cx={last[0]} cy={last[1]} r="2.6" fill={color} strokeWidth="1.5" />}
    </svg>
  )
}

export function LineChart({
  labels,
  series,
  height = 250,
  format = (v: number) => v.toLocaleString('zh-CN'),
}: {
  labels: string[]
  series: Array<{ name: string; color: string; values: number[] }>
  height?: number
  format?: (v: number) => string
}) {
  const width = 760
  const pad = { l: 42, r: 14, t: 12, b: 28 }
  const n = labels.length
  const max = Math.max(...series.flatMap((s) => s.values), 1)
  const nice = niceMax(max)
  const plotW = width - pad.l - pad.r
  const plotH = height - pad.t - pad.b
  const step = plotW / Math.max(1, n - 1)
  const y = (v: number) => pad.t + plotH - (v / nice) * plotH
  const ticks = 4
  const colorMap: Record<string, string> = {
    'var(--s1)': '#2a78d6',
    'var(--s2)': '#eb6834',
    'var(--s3)': '#1baf7a',
  }
  const [hover, setHover] = useState<number | null>(null)
  const hostRef = useRef<HTMLDivElement>(null)
  return (
    <div className="chart" ref={hostRef} style={{ position: 'relative' }}>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        style={{ width: '100%', height: 'auto', display: 'block' }}
        onPointerMove={(e) => {
          const rect = hostRef.current?.getBoundingClientRect()
          if (!rect) return
          const px = ((e.clientX - rect.left) / rect.width) * width
          const idx = Math.max(0, Math.min(n - 1, Math.round((px - pad.l) / step)))
          setHover(idx)
        }}
        onPointerLeave={() => setHover(null)}
      >
        {Array.from({ length: ticks + 1 }).map((_, i) => {
          const yy = pad.t + (plotH / ticks) * i
          return <line key={i} className="gridline" x1={pad.l} x2={width - pad.r} y1={yy} y2={yy} />
        })}
        {Array.from({ length: ticks + 1 }).map((_, i) => {
          const val = nice - (nice / ticks) * i
          const yy = pad.t + (plotH / ticks) * i
          return <text key={i} x={pad.l - 8} y={yy + 3.5} textAnchor="end">{fmtAxis(val)}</text>
        })}
        {labels.map((lb, i) => {
          if (n > 9 && i % 2 !== 0 && i !== n - 1) return null
          return (
            <text key={i} x={pad.l + i * step} y={height - 8} textAnchor={i === 0 ? 'start' : i === n - 1 ? 'end' : 'middle'}>
              {lb}
            </text>
          )
        })}
        {hover !== null && <line className="cross" x1={pad.l + hover * step} x2={pad.l + hover * step} y1={pad.t} y2={pad.t + plotH} />}
        {series.map((s) => {
          const color = colorMap[s.color] ?? s.color
          const pts = s.values.map((v, i) => [pad.l + i * step, y(v)])
          const d = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join(' ')
          return <path key={s.name} d={d} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        })}
      </svg>
      <div style={{ display: 'flex', gap: 18, alignItems: 'center', fontSize: 11.5, color: 'var(--muted)', paddingTop: 2 }}>
        {series.map((s) => (
          <span key={s.name} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 8, height: 8, borderRadius: '50%', background: colorMap[s.color] ?? s.color, display: 'inline-block' }} />
            {s.name}
          </span>
        ))}
      </div>
      {hover !== null && (
        <div
          className="chart-tip"
          style={{
            opacity: 1,
            left: `${Math.min(80, ((pad.l + hover * step) / width) * 100)}%`,
            top: 18,
          }}
        >
          <b>{labels[hover]}</b>
          {series.map((s) => (
            <div className="t-row" key={s.name}>
              <span className="t-dot" style={{ background: colorMap[s.color] ?? s.color }} />
              {s.name}
              <b>{format(s.values[hover])}</b>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function niceMax(max: number) {
  const pow = Math.pow(10, Math.floor(Math.log10(Math.max(1, max))))
  const base = max / pow
  const nice = base <= 1 ? 1 : base <= 2 ? 2 : base <= 5 ? 5 : 10
  return nice * pow
}

function fmtAxis(v: number) {
  if (v >= 10000) return `${(v / 10000).toFixed(v % 10000 === 0 ? 0 : 1)}万`
  if (v >= 1000) return `${(v / 1000).toFixed(v % 1000 === 0 ? 0 : 1)}k`
  return String(Math.round(v))
}

export function Tabs({
  items,
  active,
  onChange,
}: {
  items: Array<{ key: string; label: React.ReactNode }>
  active: string
  onChange: (key: string) => void
}) {
  return (
    <div className="tabs">
      {items.map((it) => (
        <button key={it.key} className={active === it.key ? 'active' : ''} onClick={() => onChange(it.key)}>
          {it.label}
        </button>
      ))}
    </div>
  )
}

export function Empty({ icon = 'list', title, children }: { icon?: string; title: string; children?: React.ReactNode }) {
  return (
    <div className="empty">
      <Icon name={icon} />
      <b>{title}</b>
      {children}
    </div>
  )
}
