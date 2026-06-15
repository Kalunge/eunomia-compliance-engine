import { useEffect, useMemo, useRef, useState } from 'react'
import {
  AlertCircle,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  Cpu,
  Database,
  FileText,
  Info,
  PenLine,
  Radio,
  Search,
  Sparkles,
  Tag,
  Upload,
  type LucideIcon,
} from 'lucide-react'
import { clsx } from 'clsx'
import type { WorkflowEvent } from '@/lib/events'

interface Props {
  events: WorkflowEvent[]
  isLive: boolean
  connectionState: 'idle' | 'connecting' | 'open' | 'closed' | 'error'
}

type Tone = 'slate' | 'brand' | 'violet' | 'amber' | 'emerald' | 'rose'

interface EventMeta {
  label: string
  icon: LucideIcon
  tone: Tone
}

const EVENT_META: Record<string, EventMeta> = {
  'regulation.uploaded': { label: 'Document uploaded', icon: Upload, tone: 'slate' },
  'extractor.start': { label: 'Extractor agent starting', icon: Cpu, tone: 'brand' },
  'extractor.parsed': { label: 'Document parsed', icon: FileText, tone: 'brand' },
  'extractor.identified': { label: 'Obligations identified', icon: CheckCircle2, tone: 'brand' },
  'mapper.start': { label: 'Mapper agent starting', icon: Cpu, tone: 'violet' },
  'mapper.obligation.start': { label: 'Mapping obligation', icon: Search, tone: 'violet' },
  'mapper.obligation.candidates': { label: 'Candidates retrieved', icon: Database, tone: 'violet' },
  'mapper.obligation.matched': { label: 'Match found', icon: Sparkles, tone: 'emerald' },
  'mapper.obligation.gap': { label: 'Gap detected', icon: AlertCircle, tone: 'amber' },
  'mapper.complete': { label: 'Mapping complete', icon: CheckCircle2, tone: 'violet' },
  'impact.start': { label: 'ImpactAnalyzer starting', icon: Tag, tone: 'amber' },
  'impact.tagged': { label: 'Products tagged', icon: Tag, tone: 'amber' },
  'impact.complete': { label: 'Impact tagging complete', icon: CheckCircle2, tone: 'amber' },
  'impact.skipped': { label: 'Impact tagging skipped', icon: Info, tone: 'slate' },
  'summarizer.start': { label: 'Composing impact brief', icon: PenLine, tone: 'emerald' },
  'summarizer.complete': { label: 'Brief ready', icon: CheckCircle2, tone: 'emerald' },
  'workflow.failed': { label: 'Workflow failed', icon: AlertCircle, tone: 'rose' },
}

const TONE_STYLES: Record<Tone, { icon: string; bar: string }> = {
  slate: { icon: 'bg-slate-100 text-slate-600', bar: 'bg-slate-300' },
  brand: { icon: 'bg-brand-50 text-brand-600', bar: 'bg-brand-300' },
  violet: { icon: 'bg-violet-50 text-violet-600', bar: 'bg-violet-300' },
  amber: { icon: 'bg-amber-50 text-amber-600', bar: 'bg-amber-300' },
  emerald: { icon: 'bg-emerald-50 text-emerald-600', bar: 'bg-emerald-300' },
  rose: { icon: 'bg-rose-50 text-rose-600', bar: 'bg-rose-300' },
}

export default function LiveActivityFeed({ events, isLive, connectionState }: Props) {
  // Default expanded while the saga is in flight; collapse once it
  // settles so the brief is the visible focus.
  const [expanded, setExpanded] = useState(isLive)
  useEffect(() => {
    setExpanded(isLive)
  }, [isLive])

  const listRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!expanded || !listRef.current) return
    listRef.current.scrollTop = listRef.current.scrollHeight
  }, [events.length, expanded])

  const groupedCount = useMemo(() => {
    const counts: Record<string, number> = {}
    events.forEach((e) => {
      counts[e.type] = (counts[e.type] ?? 0) + 1
    })
    return counts
  }, [events])

  if (events.length === 0 && !isLive) return null

  return (
    <section className="mt-6 card overflow-hidden">
      <header className="flex items-center justify-between border-b border-slate-100 px-5 py-3">
        <div className="flex items-center gap-2.5">
          <ConnectionIndicator state={connectionState} isLive={isLive} />
          <h2 className="text-xs font-semibold uppercase tracking-wider text-slate-700">
            Live agent activity
          </h2>
          <span className="text-xs text-slate-400">
            {events.length} {events.length === 1 ? 'event' : 'events'}
          </span>
        </div>
        <button
          type="button"
          onClick={() => setExpanded((e) => !e)}
          className="inline-flex items-center gap-1 text-xs text-slate-500 hover:text-slate-700"
        >
          {expanded ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
          {expanded ? 'Collapse' : 'Show'}
        </button>
      </header>

      {expanded && (
        <div
          ref={listRef}
          className="max-h-72 overflow-y-auto px-5 py-3 space-y-1.5 scroll-smooth"
        >
          {events.length === 0 ? (
            <p className="text-xs italic text-slate-400 py-4 text-center">
              Waiting for the first event…
            </p>
          ) : (
            events.map((e) => <EventRow key={e.id} event={e} />)
          )}
        </div>
      )}

      {!expanded && (
        <div className="px-5 py-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">
          {Object.entries(groupedCount).map(([type, count]) => {
            const meta = EVENT_META[type]
            if (!meta) return null
            return (
              <span key={type} className="inline-flex items-center gap-1">
                <span className="font-mono">{count}</span>
                <span>{meta.label.toLowerCase()}</span>
              </span>
            )
          })}
        </div>
      )}
    </section>
  )
}

function EventRow({ event }: { event: WorkflowEvent }) {
  const meta = EVENT_META[event.type] ?? {
    label: event.type,
    icon: Info,
    tone: 'slate' as Tone,
  }
  const tone = TONE_STYLES[meta.tone]
  const Icon = meta.icon
  return (
    <div className="flex items-start gap-3 py-1.5 group">
      <div className="flex flex-col items-center pt-0.5">
        <div className={clsx('flex h-6 w-6 items-center justify-center rounded-md', tone.icon)}>
          <Icon className="h-3.5 w-3.5" />
        </div>
        <div className={clsx('w-px flex-1 mt-1', tone.bar)} />
      </div>
      <div className="min-w-0 flex-1 pb-1">
        <div className="flex items-baseline justify-between gap-3">
          <p className="text-xs font-medium text-slate-800">{meta.label}</p>
          <span className="text-[10px] font-mono text-slate-400 tabular-nums">
            {formatTime(event.receivedAt)}
          </span>
        </div>
        <EventSubtitle event={event} />
      </div>
    </div>
  )
}

function EventSubtitle({ event }: { event: WorkflowEvent }) {
  const p = event.payload as Record<string, unknown>
  const text = (() => {
    switch (event.type) {
      case 'regulation.uploaded':
        return `${p.title ?? ''} · ${formatBytes(p.sizeBytes as number)}`
      case 'extractor.parsed':
        return `${formatNumber(p.characters as number)} characters extracted`
      case 'extractor.identified':
        return `${p.obligationCount} obligation${p.obligationCount === 1 ? '' : 's'} found`
      case 'mapper.start':
        return `${p.obligationCount} obligations to map, top-k = ${p.topK}, threshold ${p.threshold}`
      case 'mapper.obligation.start':
        return `${p.index}/${p.total} — "${truncate(p.summary as string, 70)}"`
      case 'mapper.obligation.candidates':
        return `obligation ${p.index} — ${p.candidateCount} candidate chunk${p.candidateCount === 1 ? '' : 's'}`
      case 'mapper.obligation.matched': {
        const conf = Math.round((p.topConfidence as number) * 100)
        return `obligation ${p.index} — ${p.matchCount} match${p.matchCount === 1 ? '' : 'es'} above threshold (top ${conf}%)`
      }
      case 'mapper.obligation.gap':
        return `obligation ${p.index} — no policy covers this rule`
      case 'mapper.complete':
        return `${p.matched} matched · ${p.gaps} gap${p.gaps === 1 ? '' : 's'}`
      case 'impact.start':
        return `${p.obligationCount} obligations × ${p.catalogueSize} catalogue entries`
      case 'impact.tagged':
        return `obligation ${p.index} — ${p.productCount} product${p.productCount === 1 ? '' : 's'} affected`
      case 'impact.complete':
        return `${p.tagged} tagged · ${p.untagged} untagged`
      case 'summarizer.start':
        return `composing from ${p.obligationCount} obligations`
      case 'summarizer.complete':
        return `${formatNumber(p.characters as number)} chars in ${formatNumber(p.elapsedMs as number)} ms`
      case 'workflow.failed':
        return `${p.stage} stage: ${p.message}`
      default:
        return null
    }
  })()
  if (!text) return null
  return <p className="mt-0.5 text-[11px] text-slate-500 leading-snug">{text}</p>
}

function ConnectionIndicator({ state, isLive }: { state: Props['connectionState']; isLive: boolean }) {
  if (!isLive) {
    return (
      <span className="inline-flex h-2 w-2 rounded-full bg-slate-300" title="Saga complete" />
    )
  }
  const colour =
    state === 'open' ? 'bg-emerald-500' :
    state === 'connecting' ? 'bg-amber-400' :
    state === 'error' ? 'bg-rose-500' :
    'bg-slate-300'
  return (
    <span className="relative inline-flex" title={`Connection: ${state}`}>
      <span className={clsx('inline-flex h-2 w-2 rounded-full', colour)} />
      {state === 'open' && (
        <span className="absolute inset-0 inline-flex h-2 w-2 animate-ping rounded-full bg-emerald-400 opacity-75" />
      )}
    </span>
  )
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function pad(n: number): string {
  return n.toString().padStart(2, '0')
}

function formatNumber(n: number | undefined): string {
  if (n == null) return '—'
  return n.toLocaleString()
}

function formatBytes(n: number | undefined): string {
  if (n == null) return '—'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

function truncate(s: string, max: number): string {
  if (!s) return ''
  return s.length > max ? `${s.slice(0, max)}…` : s
}

// Re-export the connection icon for AppLayout if we ever want it elsewhere.
export { Radio as LiveActivityIcon }
