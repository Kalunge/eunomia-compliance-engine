import { useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import {
  AlertCircle,
  ArrowRight,
  Equal,
  GitCompare,
  Loader2,
  Minus,
  Plus,
  RefreshCw,
} from 'lucide-react'
import { clsx } from 'clsx'
import { fetchRegulations } from '@/lib/regulations'
import { runDiff, type DiffPair, type DiffSide, type RegulationDiff } from '@/lib/diff'
import { apiErrorMessage } from '@/lib/api'
import type { RegulationSummary } from '@/lib/types'
import EmptyState from '@/components/EmptyState'

const DIFFABLE_STATUSES = new Set(['MAPPED', 'BRIEF_GENERATING', 'BRIEF_READY'])

export default function RegulationDiffPage() {
  const [baselineId, setBaselineId] = useState<string>('')
  const [candidateId, setCandidateId] = useState<string>('')

  const regsQuery = useQuery({
    queryKey: ['regulations', 0, 100],
    queryFn: () => fetchRegulations(0, 100),
  })

  const diffable: RegulationSummary[] = useMemo(() => {
    return regsQuery.data?.items.filter((r) => DIFFABLE_STATUSES.has(r.status)) ?? []
  }, [regsQuery.data])

  const diffMutation = useMutation({
    mutationFn: () => runDiff(baselineId, candidateId),
  })

  const sameSelection = baselineId !== '' && baselineId === candidateId
  const canRun =
    baselineId !== '' && candidateId !== '' && !sameSelection && !diffMutation.isPending

  const handleRun = () => {
    if (canRun) diffMutation.mutate()
  }

  return (
    <div className="mx-auto max-w-6xl p-6 md:p-10 space-y-6">
      <PageHeader />

      {regsQuery.isLoading && <SkeletonCard />}

      {regsQuery.isError && (
        <ErrorBanner title="Could not load regulations" message={apiErrorMessage(regsQuery.error)} />
      )}

      {regsQuery.data && diffable.length < 2 && (
        <EmptyState
          icon={GitCompare}
          title="Need at least two mapped regulations to diff"
          description="Upload (or wait for processing on) at least two regulations before running a comparison. Only regulations that have reached the MAPPED state or later can be diffed."
        />
      )}

      {diffable.length >= 2 && (
        <SelectorCard
          diffable={diffable}
          baselineId={baselineId}
          candidateId={candidateId}
          onBaseline={setBaselineId}
          onCandidate={setCandidateId}
          onRun={handleRun}
          canRun={canRun}
          sameSelection={sameSelection}
          isRunning={diffMutation.isPending}
        />
      )}

      {diffMutation.isPending && <RunningState />}

      {diffMutation.isError && (
        <ErrorBanner
          title="Diff failed"
          message={apiErrorMessage(diffMutation.error, 'The comparison could not be completed.')}
        />
      )}

      {diffMutation.data && <ResultView diff={diffMutation.data} />}
    </div>
  )
}

function PageHeader() {
  return (
    <header>
      <h1 className="text-2xl font-bold text-slate-900">Compare regulations</h1>
      <p className="mt-1 text-sm text-slate-500">
        Pick a baseline (older) and a candidate (newer) regulation. Eunomia compares them at the
        obligation level — paraphrased restatements show as unchanged, threshold changes show as
        modified, and entirely new rules surface as added. Typical run time is 40-60 seconds.
      </p>
    </header>
  )
}

interface SelectorCardProps {
  diffable: RegulationSummary[]
  baselineId: string
  candidateId: string
  onBaseline: (id: string) => void
  onCandidate: (id: string) => void
  onRun: () => void
  canRun: boolean
  sameSelection: boolean
  isRunning: boolean
}

function SelectorCard({
  diffable,
  baselineId,
  candidateId,
  onBaseline,
  onCandidate,
  onRun,
  canRun,
  sameSelection,
  isRunning,
}: SelectorCardProps) {
  return (
    <section className="card p-6 space-y-4">
      <div className="grid gap-4 md:grid-cols-[1fr_auto_1fr] md:items-end">
        <RegulationSelect
          label="Baseline (older)"
          value={baselineId}
          onChange={onBaseline}
          options={diffable}
          disabledId={candidateId}
        />
        <div className="hidden md:flex md:items-center md:justify-center md:pb-2">
          <ArrowRight className="h-5 w-5 text-slate-400" />
        </div>
        <RegulationSelect
          label="Candidate (newer)"
          value={candidateId}
          onChange={onCandidate}
          options={diffable}
          disabledId={baselineId}
        />
      </div>

      {sameSelection && (
        <p className="text-xs text-amber-700">
          Baseline and candidate must be different regulations.
        </p>
      )}

      <div className="flex items-center justify-end gap-3 pt-2 border-t border-slate-100">
        <button
          type="button"
          onClick={onRun}
          disabled={!canRun}
          className="btn-primary disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isRunning ? (
            <>
              <Loader2 className="h-4 w-4 animate-spin" />
              Comparing…
            </>
          ) : (
            <>
              <GitCompare className="h-4 w-4" />
              Run comparison
            </>
          )}
        </button>
      </div>
    </section>
  )
}

interface RegulationSelectProps {
  label: string
  value: string
  onChange: (id: string) => void
  options: RegulationSummary[]
  disabledId: string
}

function RegulationSelect({ label, value, onChange, options, disabledId }: RegulationSelectProps) {
  return (
    <label className="block">
      <span className="text-xs font-semibold uppercase tracking-wider text-slate-600">{label}</span>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="mt-1.5 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
      >
        <option value="">Select a regulation…</option>
        {options.map((o) => (
          <option key={o.id} value={o.id} disabled={o.id === disabledId}>
            {o.title}
          </option>
        ))}
      </select>
    </label>
  )
}

function RunningState() {
  return (
    <section className="card flex items-start gap-4 p-6 border-brand-100 bg-brand-50/50">
      <Loader2 className="h-5 w-5 text-brand-600 animate-spin flex-shrink-0 mt-0.5" />
      <div>
        <h3 className="font-semibold text-brand-900">Comparing regulations…</h3>
        <p className="mt-1 text-sm text-brand-800">
          The Diff agent is embedding each candidate obligation and asking the LLM to classify the
          relationship to its nearest baseline. With ~10-15 obligations and the rate-limit
          throttle, this typically takes 40-60 seconds.
        </p>
      </div>
    </section>
  )
}

function ErrorBanner({ title, message }: { title: string; message: string }) {
  return (
    <div className="card flex items-start gap-4 p-6 border-rose-200 bg-rose-50">
      <AlertCircle className="h-5 w-5 text-rose-600 flex-shrink-0 mt-0.5" />
      <div>
        <h3 className="font-semibold text-rose-900">{title}</h3>
        <p className="mt-1 text-sm text-rose-700">{message}</p>
      </div>
    </div>
  )
}

function SkeletonCard() {
  return (
    <div className="card p-6 animate-pulse">
      <div className="h-3 w-1/3 rounded bg-slate-200 mb-4" />
      <div className="h-10 w-full rounded bg-slate-100 mb-3" />
      <div className="h-10 w-full rounded bg-slate-100" />
    </div>
  )
}

function ResultView({ diff }: { diff: RegulationDiff }) {
  return (
    <section className="space-y-6">
      <DiffBanner diff={diff} />
      <CountChips diff={diff} />

      {diff.modified.length > 0 && (
        <PairSection title="Modified" icon={RefreshCw} tone="amber" pairs={diff.modified} />
      )}

      {diff.added.length > 0 && (
        <SideSection title="Added (in candidate only)" icon={Plus} tone="emerald" sides={diff.added} />
      )}

      {diff.removed.length > 0 && (
        <SideSection title="Removed (in baseline only)" icon={Minus} tone="rose" sides={diff.removed} />
      )}

      {diff.unchanged.length > 0 && (
        <PairSection
          title="Unchanged"
          icon={Equal}
          tone="slate"
          pairs={diff.unchanged}
          defaultCollapsed
        />
      )}

      {isNoOpDiff(diff) && (
        <EmptyState
          icon={Equal}
          title="Identical scope — no material change"
          description="Every candidate obligation maps cleanly to a baseline with no substantive shift. The amendment likely reorders text or rewords obligations without changing controls."
        />
      )}

      {isEntirelyNew(diff) && (
        <div className="card p-4 border-amber-200 bg-amber-50">
          <p className="text-sm text-amber-900">
            <strong>This appears to be an entirely new regulation.</strong> None of the baseline
            obligations were claimed by any candidate. Double-check that you compared the right pair.
          </p>
        </div>
      )}
    </section>
  )
}

function DiffBanner({ diff }: { diff: RegulationDiff }) {
  const pct = Math.round(diff.summary.materialChangeScore * 100)
  const tone = pct === 0 ? 'slate' : pct < 20 ? 'emerald' : pct < 60 ? 'amber' : 'rose'
  const toneClass = {
    slate: 'border-slate-200 bg-slate-50 text-slate-900',
    emerald: 'border-emerald-200 bg-emerald-50 text-emerald-900',
    amber: 'border-amber-200 bg-amber-50 text-amber-900',
    rose: 'border-rose-200 bg-rose-50 text-rose-900',
  }[tone]

  return (
    <div className={clsx('card p-6 border', toneClass)}>
      <div className="flex items-baseline justify-between gap-4 flex-wrap">
        <h2 className="text-lg font-semibold">
          {pct}% material change
        </h2>
        <div className="text-xs opacity-80">
          {diff.baselineTitle} <ArrowRight className="inline h-3 w-3 mx-1" /> {diff.candidateTitle}
        </div>
      </div>
      <p className="mt-2 text-sm opacity-90">
        {summarySentence(diff.summary)}
      </p>
    </div>
  )
}

function CountChips({ diff }: { diff: RegulationDiff }) {
  const items: Array<{ label: string; count: number; tone: string; icon: typeof Equal }> = [
    { label: 'Unchanged', count: diff.summary.unchanged, tone: 'bg-slate-100 text-slate-700', icon: Equal },
    { label: 'Modified', count: diff.summary.modified, tone: 'bg-amber-100 text-amber-800', icon: RefreshCw },
    { label: 'Added', count: diff.summary.added, tone: 'bg-emerald-100 text-emerald-800', icon: Plus },
    { label: 'Removed', count: diff.summary.removed, tone: 'bg-rose-100 text-rose-800', icon: Minus },
  ]
  return (
    <div className="flex flex-wrap gap-2">
      {items.map((it) => (
        <span
          key={it.label}
          className={clsx('inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium', it.tone)}
        >
          <it.icon className="h-3 w-3" />
          <span className="font-mono tabular-nums">{it.count}</span>
          <span>{it.label.toLowerCase()}</span>
        </span>
      ))}
    </div>
  )
}

interface PairSectionProps {
  title: string
  icon: typeof Equal
  tone: 'amber' | 'slate'
  pairs: DiffPair[]
  defaultCollapsed?: boolean
}

function PairSection({ title, icon: Icon, tone, pairs, defaultCollapsed }: PairSectionProps) {
  const [collapsed, setCollapsed] = useState(!!defaultCollapsed)
  const headerTone = tone === 'amber' ? 'text-amber-800' : 'text-slate-700'
  return (
    <section className="card overflow-hidden">
      <button
        type="button"
        onClick={() => setCollapsed((c) => !c)}
        className="flex w-full items-center gap-2 border-b border-slate-100 px-5 py-3 text-left hover:bg-slate-50"
      >
        <Icon className={clsx('h-4 w-4', headerTone)} />
        <h3 className="text-sm font-semibold text-slate-800">{title}</h3>
        <span className="text-xs text-slate-400">({pairs.length})</span>
        <span className="ml-auto text-xs text-slate-500">{collapsed ? 'Show' : 'Hide'}</span>
      </button>
      {!collapsed && (
        <ul className="divide-y divide-slate-100">
          {pairs.map((p, i) => (
            <li key={`${p.baseline.obligationId}-${p.candidate.obligationId}-${i}`} className="p-5">
              <div className="grid gap-4 md:grid-cols-2">
                <ObligationCell label="Baseline" side={p.baseline} />
                <ObligationCell label="Candidate" side={p.candidate} />
              </div>
              {p.rationale && (
                <p className="mt-3 rounded-md bg-slate-50 px-3 py-2 text-xs text-slate-700">
                  <span className="font-semibold text-slate-800">Rationale: </span>
                  {p.rationale}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

interface SideSectionProps {
  title: string
  icon: typeof Equal
  tone: 'emerald' | 'rose'
  sides: DiffSide[]
}

function SideSection({ title, icon: Icon, tone, sides }: SideSectionProps) {
  const headerTone = tone === 'emerald' ? 'text-emerald-700' : 'text-rose-700'
  return (
    <section className="card overflow-hidden">
      <header className="flex items-center gap-2 border-b border-slate-100 px-5 py-3">
        <Icon className={clsx('h-4 w-4', headerTone)} />
        <h3 className="text-sm font-semibold text-slate-800">{title}</h3>
        <span className="text-xs text-slate-400">({sides.length})</span>
      </header>
      <ul className="divide-y divide-slate-100">
        {sides.map((s) => (
          <li key={s.obligationId} className="p-5">
            <ObligationCell label={null} side={s} />
          </li>
        ))}
      </ul>
    </section>
  )
}

function ObligationCell({ label, side }: { label: string | null; side: DiffSide }) {
  return (
    <div className="min-w-0">
      {label && (
        <p className="text-[11px] font-semibold uppercase tracking-wider text-slate-400 mb-1">{label}</p>
      )}
      <p className="text-sm font-medium text-slate-900">{side.summary}</p>
      <p className="mt-1 text-xs text-slate-500 italic">"{side.sourceExcerpt}"</p>
    </div>
  )
}

function summarySentence(s: RegulationDiff['summary']): string {
  const bits: string[] = []
  if (s.unchanged > 0) bits.push(`${s.unchanged} unchanged`)
  if (s.modified > 0) bits.push(`${s.modified} modified`)
  if (s.added > 0) bits.push(`${s.added} added`)
  if (s.removed > 0) bits.push(`${s.removed} removed`)
  if (bits.length === 0) return 'No obligations were compared.'
  return `${bits.join(' · ')} (${s.totalBaseline} baseline, ${s.totalCandidate} candidate).`
}

function isNoOpDiff(diff: RegulationDiff): boolean {
  return (
    diff.summary.modified === 0 &&
    diff.summary.added === 0 &&
    diff.summary.removed === 0 &&
    diff.summary.unchanged > 0
  )
}

function isEntirelyNew(diff: RegulationDiff): boolean {
  return (
    diff.summary.unchanged === 0 &&
    diff.summary.modified === 0 &&
    diff.summary.totalBaseline > 0 &&
    diff.summary.removed === diff.summary.totalBaseline
  )
}
